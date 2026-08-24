import axios from 'axios'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import {
  getRefreshToken,
  removeRefreshToken,
  removeToken,
  setRefreshToken,
  setToken
} from '@/utils/auth'
import type { LoginVO, R } from '@/types'

/**
 * 需要跳过自动续期的认证类接口。
 *
 * 说明：
 * 1. 登录、刷新、登出本身不能再触发刷新，否则会形成递归续期。
 * 2. `/tenant/` 相关切换流程属于建立新会话的入口，也不应该沿用旧刷新令牌兜底。
 */
const REFRESH_EXEMPT_PATH_PREFIXES = [
  '/auth/login',
  '/auth/app/login',
  '/auth/wechat/mp/login',
  '/auth/wechat/oa/login',
  '/auth/refresh',
  '/auth/logout',
  '/tenant/'
]

/** Pinia 持久化用户会话在 localStorage 中的固定键名。 */
const PERSISTED_USER_STORE_KEY = 'HAN-user'

/** 并发 401 只允许发起一次刷新请求，其他请求复用同一个 Promise。 */
let sessionRefreshPromise: Promise<SessionRefreshResult> | null = null

/** 防止刷新彻底失效时重复跳转登录页。 */
let sessionRedirecting = false

const browserBasePath = import.meta.env.BASE_URL.replace(/\/$/, '')
const browserLoginPath = `${browserBasePath}/login`

export interface SessionRefreshResult {
  status: 'success' | 'expired' | 'failed'
  accessToken?: string
  message: string
  httpStatus?: number
  bizCode?: number
}

/**
 * 判断指定请求是否允许尝试自动续期。
 */
export function canAttemptSessionRefresh(url?: string | null): boolean {
  if (!url) {
    return false
  }

  const normalizedPath = normalizeRequestPath(url)
  return !REFRESH_EXEMPT_PATH_PREFIXES.some((prefix) => normalizedPath.startsWith(prefix))
}

/**
 * 统一尝试刷新访问令牌。
 *
 * 返回值说明：
 * - `success`：成功拿到新的 access token，可以重放原请求。
 * - `expired`：刷新凭证真实失效，应该清会话并跳登录。
 * - `failed`：认证服务临时异常、网络抖动等，不应立刻清会话。
 */
export async function tryRefreshSession(baseURL = ''): Promise<SessionRefreshResult> {
  if (sessionRefreshPromise) {
    return sessionRefreshPromise
  }

  sessionRefreshPromise = refreshSessionInternal(baseURL).finally(() => {
    sessionRefreshPromise = null
  })

  return sessionRefreshPromise
}

/**
 * 统一清理本地登录态，并携带当前页面地址回到登录页。
 */
export function clearSessionAndRedirectToLogin(redirectPath?: string): void {
  clearPersistedSession()

  if (typeof window === 'undefined') {
    return
  }

  if (window.location.pathname === browserLoginPath || sessionRedirecting) {
    return
  }

  sessionRedirecting = true
  const rawRedirect = redirectPath ?? router.currentRoute.value.fullPath
  const redirect = browserBasePath && rawRedirect.startsWith(browserBasePath)
    ? rawRedirect.slice(browserBasePath.length) || '/'
    : rawRedirect
  const loginQuery = `?redirect=${encodeURIComponent(redirect)}`

  router.push(`/login${loginQuery}`).catch(() => {
    window.location.assign(`${browserLoginPath}${loginQuery}`)
  }).finally(() => {
    window.setTimeout(() => {
      sessionRedirecting = false
    }, 800)
  })
}

/**
 * 真正执行刷新令牌请求，并在成功后同步更新 Pinia 与 localStorage。
 */
async function refreshSessionInternal(baseURL: string): Promise<SessionRefreshResult> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return {
      status: 'expired',
      message: '登录状态已过期，请重新登录'
    }
  }

  const refreshUrl = buildRefreshUrl(baseURL)

  try {
    const response = await axios.post<R<LoginVO>>(refreshUrl, null, {
      headers: {
        'X-Refresh-Token': refreshToken
      }
    })

    if (response.data?.code !== 200 || !response.data?.data?.accessToken) {
      if (response.data?.code === 401) {
        return {
          status: 'expired',
          message: response.data?.msg || '登录状态已过期，请重新登录',
          httpStatus: response.status,
          bizCode: response.data?.code
        }
      }

      return {
        status: 'failed',
        message: response.data?.msg || '会话续期失败，请稍后重试',
        httpStatus: response.status,
        bizCode: response.data?.code
      }
    }

    const session = response.data.data
    applyPersistedSession(
      session.accessToken,
      session.refreshToken,
      session.userInfo?.userId ?? getPersistedRuntimeUserId()
    )
    return {
      status: 'success',
      accessToken: session.accessToken,
      message: '会话续期成功'
    }
  } catch (error: any) {
    const httpStatus = error?.response?.status as number | undefined
    const bizCode = error?.response?.data?.code as number | undefined
    const message =
      error?.response?.data?.msg ||
      error?.message ||
      '会话续期失败，请稍后重试'

    if (httpStatus === 401 || httpStatus === 403 || bizCode === 401 || bizCode === 403) {
      return {
        status: 'expired',
        message,
        httpStatus,
        bizCode
      }
    }

    return {
      status: 'failed',
      message,
      httpStatus,
      bizCode
    }
  }
}

/**
 * 兼容绝对 URL、相对路径和带查询串的接口地址，统一抽取 pathname。
 */
function normalizeRequestPath(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    try {
      return new URL(url).pathname
    } catch {
      return url
    }
  }

  if (!url.startsWith('/')) {
    return `/${url}`
  }

  return url
}

/**
 * 刷新接口永远走 `/auth/refresh`，并复用当前请求层 baseURL。
 */
function buildRefreshUrl(baseURL: string): string {
  if (!baseURL) {
    return '/auth/refresh'
  }

  return baseURL.endsWith('/') ? `${baseURL.slice(0, -1)}/auth/refresh` : `${baseURL}/auth/refresh`
}

/**
 * 保底清空本地会话。
 *
 * 优先通过 Pinia Store 统一清理；如果当前运行时还没完成 Store 初始化，
 * 至少要把 localStorage 中的 token、refresh token 和持久化用户信息一起删掉。
 */
function clearPersistedSession(): void {
  const userStore = resolveUserStore()
  if (userStore) {
    userStore.resetToken()
    return
  }

  removeToken()
  removeRefreshToken()
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem(PERSISTED_USER_STORE_KEY)
  }
}

/**
 * 优先通过 Pinia Store 同步会话；如果当前运行时没有激活 Pinia，则直接回写 localStorage，
 * 保证流式请求、测试场景和应用初始化早期也能安全刷新会话。
 */
function applyPersistedSession(
  accessToken: string,
  refreshToken?: string | null,
  runtimeUserId?: string | number | null
): void {
  const userStore = resolveUserStore()
  if (userStore) {
    userStore.applySession(accessToken, refreshToken, runtimeUserId)
    return
  }

  setToken(accessToken)
  if (refreshToken) {
    setRefreshToken(refreshToken)
  }

  if (typeof window === 'undefined') {
    return
  }

  const persistedState = readPersistedUserStore()
  window.localStorage.setItem(
    PERSISTED_USER_STORE_KEY,
    JSON.stringify({
      ...persistedState,
      token: accessToken,
      _userId: runtimeUserId ?? null
    })
  )
}

/** 在 Store 不可用时，从持久化用户状态中补拿上一次记录的调试用户锚点。 */
function getPersistedRuntimeUserId(): string | number | null {
  const userStore = resolveUserStore()
  if (userStore) {
    return userStore._userId ?? null
  }

  return readPersistedUserStore()._userId ?? null
}

/** 读取 localStorage 中持久化的用户状态，解析失败时按空对象处理。 */
function readPersistedUserStore(): Record<string, unknown> & { _userId?: string | number | null } {
  if (typeof window === 'undefined') {
    return {}
  }

  try {
    const raw = window.localStorage.getItem(PERSISTED_USER_STORE_KEY)
    if (!raw) {
      return {}
    }
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

/** 安全获取用户 Store，避免在 Pinia 尚未激活时直接抛错。 */
function resolveUserStore() {
  try {
    return useUserStore()
  } catch {
    return null
  }
}
