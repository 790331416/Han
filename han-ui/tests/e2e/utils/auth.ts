import type { APIRequestContext, Page } from '@playwright/test'

const TOKEN_KEY = 'Admin-Token'
const REFRESH_TOKEN_KEY = 'Admin-Refresh-Token'
const USER_STORE_KEY = 'HAN-user'

export interface AuthUserInfo {
  userId?: string | number
}

export interface AuthSession {
  accessToken: string
  refreshToken: string
  userInfo?: AuthUserInfo
}

export interface LoginByApiOptions {
  apiBaseUrl: string
  username: string
  password: string
  tenantId?: string | number
}

/**
 * 通过后端登录接口创建测试会话。
 *
 * 说明：
 * 当前 auth 服务仅在传入验证码时才校验验证码，因此 E2E 夹具直接走用户名密码登录，
 * 以保持自动化测试的稳定性。
 */
export async function loginByApi(request: APIRequestContext, options: LoginByApiOptions): Promise<AuthSession> {
  const payload: Record<string, string | number> = {
    username: options.username,
    password: options.password
  }

  if (options.tenantId !== undefined && options.tenantId !== '') {
    payload.tenantId = normalizeTenantId(options.tenantId)
  }

  let lastError: Error | undefined

  for (let attempt = 1; attempt <= 4; attempt += 1) {
    const response = await request.post(`${options.apiBaseUrl}/auth/login`, {
      data: payload,
      headers: {
        'Content-Type': 'application/json'
      }
    })

    const result = await response.json()
    if (response.ok() && result?.code === 200 && result?.data?.accessToken) {
      return result.data as AuthSession
    }

    lastError = new Error(`E2E 登录失败: ${JSON.stringify(result)}`)
    const rateLimited = typeof result?.msg === 'string' && result.msg.includes('请求过于频繁')
    if (!rateLimited || attempt === 4) {
      throw lastError
    }

    await sleep(attempt * 1500)
  }

  throw lastError ?? new Error('E2E 登录失败: 未知错误')
}

/**
 * 将登录态注入浏览器 localStorage，供前端路由守卫与 store 初始化使用。
 */
export async function applyAuthSession(page: Page, session: AuthSession, tenantId?: string | number): Promise<void> {
  const persistedTenantId = tenantId !== undefined && tenantId !== '' ? normalizeTenantId(tenantId) : null
  const scriptPayload = {
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    userId: session.userInfo?.userId ?? null,
    persistedTenantId,
    tokenKey: TOKEN_KEY,
    refreshTokenKey: REFRESH_TOKEN_KEY,
    userStoreKey: USER_STORE_KEY
  }

  await page.context().addInitScript(
    ({
      accessToken,
      refreshToken,
      userId,
      persistedTenantId: runtimeTenantId,
      tokenKey,
      refreshTokenKey,
      userStoreKey
    }) => {
      localStorage.setItem(tokenKey, accessToken)
      localStorage.setItem(refreshTokenKey, refreshToken)
      localStorage.setItem(
        userStoreKey,
        JSON.stringify({
          token: accessToken,
          tenantId: runtimeTenantId ?? null,
          _userId: userId ?? null
        })
      )
    },
    scriptPayload
  )
}

function normalizeTenantId(tenantId: string | number): string | number {
  if (typeof tenantId === 'number') {
    return tenantId
  }

  return /^\d+$/.test(tenantId) ? Number(tenantId) : tenantId
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
