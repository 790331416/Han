import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { R } from '@/types'
import {
  canAttemptSessionRefresh,
  clearSessionAndRedirectToLogin,
  tryRefreshSession,
  type SessionRefreshResult
} from '@/utils/session-refresh'

/**
 * 扩展 Axios 配置：
 * `silentError=true` 时由调用方自行处理错误提示，请求层不再弹全局消息。
 */
declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
    /** 已经尝试过自动续期的请求，禁止二次刷新形成死循环。 */
    _retryAfterRefresh?: boolean
  }
}

/**
 * 请求层统一透传的运行时错误。
 *
 * 说明：
 * 1. `unauthorized` 专门给路由守卫和上层流程判断“是否真的应该清会话并跳登录”。
 * 2. `httpStatus` / `bizCode` 保留失败来源，便于区分是 HTTP 401 还是业务 code=401。
 */
export interface RequestRuntimeError extends Error {
  unauthorized?: boolean
  httpStatus?: number
  bizCode?: number
  sessionRefreshFailed?: boolean
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

/**
 * `X-User-Id` 仅允许在本地显式打开调试开关时发送。
 * 线上默认绝不能携带这个头，否则旧的调试身份会污染后端鉴权。
 */
const shouldSendDebugIdentityHeader =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEBUG_IDENTITY_HEADER === 'true'

function buildRequestRuntimeError(
  message: string,
  extras: Partial<RequestRuntimeError> = {}
): RequestRuntimeError {
  return Object.assign(new Error(message), extras)
}

/**
 * 相同文案在这个时间窗内只弹一次。
 *
 * 刷新令牌本身已经用共享 Promise 去重了，但**提示没有跟着去重**：N 个并发 401
 * 各自 await 同一个 Promise，拿到失败结果后各弹一次红条。一个页面同时发
 * 列表 + 字典 + 统计五到八个接口很常见，认证服务一抖就叠一屏。
 * 这里统一收口，顺带覆盖业务错误与 HTTP 错误两处同样没有节流的提示。
 */
const TOAST_DEDUPE_WINDOW_MS = 3000
const recentToastAt = new Map<string, number>()

function notifyRequestError(message: string): void {
  const now = Date.now()

  for (const [text, at] of recentToastAt) {
    if (now - at >= TOAST_DEDUPE_WINDOW_MS) {
      recentToastAt.delete(text)
    }
  }

  if (recentToastAt.has(message)) {
    return
  }

  recentToastAt.set(message, now)
  ElMessage.error(message)
}

/**
 * 日志脱敏：`error.config.headers` 里带着 `Authorization: Bearer <token>`，
 * 整个 error 对象打进控制台等于把会话令牌暴露给任何能看到控制台的人
 * （远程协助、录屏、浏览器插件）。只保留定位问题真正需要的字段。
 */
function logRequestError(scope: string, error: any): void {
  console.error(scope, {
    message: error?.message,
    url: error?.config?.url,
    method: error?.config?.method,
    status: error?.response?.status
  })
}

/**
 * 统一处理 401：
 * 1. 优先尝试刷新会话并重放原请求；
 * 2. 只有刷新凭证真实失效时才清会话跳登录；
 * 3. 若只是认证服务临时失败，则保留当前会话并把错误抛给上层。
 */
async function resolveUnauthorizedRequest(
  originalConfig: AxiosRequestConfig,
  expiredMessage: string
) {
  const canRetryAfterRefresh =
    !originalConfig._retryAfterRefresh && canAttemptSessionRefresh(originalConfig.url)

  if (canRetryAfterRefresh) {
    const refreshResult = await tryRefreshSession(service.defaults.baseURL || '')
    const retriedResponse = await retryOriginalRequestAfterRefresh(originalConfig, refreshResult)
    if (retriedResponse) {
      return retriedResponse
    }
  }

  clearSessionAndRedirectToLogin()
  throw buildRequestRuntimeError(expiredMessage, {
    unauthorized: true,
    httpStatus: 401,
    bizCode: 401
  })
}

async function retryOriginalRequestAfterRefresh(
  originalConfig: AxiosRequestConfig,
  refreshResult: SessionRefreshResult
) {
  if (refreshResult.status === 'success' && refreshResult.accessToken) {
    originalConfig._retryAfterRefresh = true
    return service(originalConfig)
  }

  if (refreshResult.status === 'failed') {
    if (!originalConfig.silentError) {
      notifyRequestError(refreshResult.message)
    }
    throw buildRequestRuntimeError(refreshResult.message, {
      sessionRefreshFailed: true,
      httpStatus: refreshResult.httpStatus,
      bizCode: refreshResult.bizCode
    })
  }

  return null
}

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()

    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    if (shouldSendDebugIdentityHeader && userStore._userId) {
      config.headers['X-User-Id'] = String(userStore._userId)
    }

    // 登录、租户切换等认证接口不主动附带旧租户上下文，避免污染新会话。
    const isAuthRequest = config.url?.startsWith('/auth/') || config.url?.startsWith('/tenant/')
    if (userStore.tenantId && !isAuthRequest) {
      config.headers['X-Tenant-Id'] = userStore.tenantId
    }

    // 搜索表单中的“全部”通常用空串表示，这里统一裁掉无意义参数。
    if (config.params) {
      const cleanParams: Record<string, any> = {}
      for (const [key, value] of Object.entries(config.params)) {
        if (value !== '' && value !== null && value !== undefined) {
          cleanParams[key] = value
        }
      }
      config.params = cleanParams
    }

    return config
  },
  (error) => {
    logRequestError('请求错误:', error)
    return Promise.reject(error)
  }
)

/**
 * 日期格式化已迁到 `utils/date.ts`。
 * 这里保留再导出，避免改动散落在各视图里的 `import { formatDate } from '@/utils/request'`。
 */
export { formatDate } from '@/utils/date'

/**
 * blob 响应的错误体识别。
 *
 * 导出接口失败时后端仍会返回 HTTP 200 + `{"code":500,"msg":"导出失败"}`（R 包装的标准做法），
 * 而拦截器原来对 blob 一律原样返回，调用方会把这段 JSON 当成 xlsx 落盘，
 * 用户拿到一个打不开的损坏文件且没有任何提示。
 */
async function resolveBlobResponse(response: AxiosResponse) {
  const blob = response.data as Blob
  const contentType: string = blob?.type || ''
  if (!/^(application\/json|text\/)/i.test(contentType)) {
    return response
  }

  let payload: any
  try {
    payload = JSON.parse(await blob.text())
  } catch {
    // 不是 JSON（例如真的在下载 CSV / 纯文本），按正常响应处理。
    return response
  }

  if (!payload || typeof payload.code !== 'number' || payload.code === 200) {
    return response
  }

  if (payload.code === 401) {
    return resolveUnauthorizedRequest(response.config, payload.msg || '登录状态已过期，请重新登录')
  }

  const message = payload.msg || '请求失败'
  if (!response.config.silentError) {
    notifyRequestError(message)
  }
  return Promise.reject(buildRequestRuntimeError(message, { bizCode: payload.code }))
}

service.interceptors.response.use(
  async (response: AxiosResponse) => {
    const res = response.data as R

    if (response.config.responseType === 'blob') {
      return resolveBlobResponse(response)
    }

    // 非 R 包装的裸 JSON（如 /actuator/jobflow/* 监控端点）直接透传，避免被误判为业务失败
    if (res === null || typeof res !== 'object' || typeof (res as any).code !== 'number') {
      return response.data
    }

    // 非 R 包装的裸 JSON（如 /actuator/jobflow/* 监控端点）直接透传，避免被误判为业务失败
    if (res === null || typeof res !== 'object' || typeof (res as any).code !== 'number') {
      return response.data
    }

    if (res.code !== 200) {
      if (res.code === 401) {
        return resolveUnauthorizedRequest(response.config, res.msg || '登录状态已过期，请重新登录')
      }

      if (!response.config.silentError) {
        notifyRequestError(res.msg || '请求失败')
      }

      return Promise.reject(buildRequestRuntimeError(res.msg || '请求失败', {
        bizCode: res.code
      }))
    }

    return res as any
  },
  async (error) => {
    // 主动取消的请求不是故障：既不打日志也不弹提示，直接原样抛给调用方。
    if (axios.isCancel(error) || error?.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }

    logRequestError('响应错误:', error)

    let message = error.message || '请求失败'
    let unauthorized = false

    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          unauthorized = true
          message = '登录状态已过期，请重新登录'
          return resolveUnauthorizedRequest(
            (error.config || {}) as AxiosRequestConfig,
            message
          )
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = `请求失败: ${error.response.status}`
      }
    } else if (typeof error.message === 'string' && error.message.includes('timeout')) {
      message = '请求超时'
    } else if (typeof error.message === 'string' && error.message.includes('Network')) {
      message = '网络错误'
    }

    if (!unauthorized && !error.config?.silentError) {
      notifyRequestError(message)
    }

    return Promise.reject(Object.assign(error, {
      message,
      unauthorized,
      httpStatus: error.response?.status
    }))
  }
)

export function request<T = any>(config: AxiosRequestConfig): Promise<R<T>> {
  return service(config) as Promise<R<T>>
}

export function get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'GET', params, ...config })
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'POST', data, ...config })
}

export function postParams<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'POST', params, ...config })
}

export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'PUT', data, ...config })
}

export function del<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'DELETE', params, ...config })
}

export default service
