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
      ElMessage.error(refreshResult.message)
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
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

/** 仅用于模板渲染的日期格式化工具，不修改原始值。 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').replace(/\.\d+$/, '')
}

service.interceptors.response.use(
  async (response: AxiosResponse) => {
    const res = response.data as R

    if (response.config.responseType === 'blob') {
      return response
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
        ElMessage.error(res.msg || '请求失败')
      }

      return Promise.reject(buildRequestRuntimeError(res.msg || '请求失败', {
        bizCode: res.code
      }))
    }

    return res as any
  },
  async (error) => {
    console.error('响应错误:', error)

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
      ElMessage.error(message)
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
