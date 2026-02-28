import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import type { R } from '@/types'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 防止401重复弹窗
let isReloginShowing = false

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    // 租户ID
    if (userStore.tenantId) {
      config.headers['X-Tenant-Id'] = userStore.tenantId
    }
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

/**
 * 日期显示格式化工具（仅用于模板展示，不修改原始数据）
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').replace(/\.\d+$/, '')
}

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data as R
    
    // 文件下载
    if (response.config.responseType === 'blob') {
      return response
    }
    
    // 业务错误
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      
      // 401: Token过期
      if (res.code === 401 && !isReloginShowing) {
        isReloginShowing = true
        ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          const userStore = useUserStore()
          userStore.resetToken()
          location.href = '/login'
        }).finally(() => {
          isReloginShowing = false
        })
      }
      
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    
    return res as any
  },
  (error) => {
    console.error('响应错误:', error)
    let message = error.message || '请求失败'
    
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请登录'
          if (!isReloginShowing) {
            const userStore = useUserStore()
            userStore.resetToken()
            router.push('/login')
          }
          break
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
    } else if (error.message.includes('timeout')) {
      message = '请求超时'
    } else if (error.message.includes('Network')) {
      message = '网络错误'
    }
    
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// 封装请求方法
export function request<T = any>(config: AxiosRequestConfig): Promise<R<T>> {
  return service(config) as Promise<R<T>>
}

export function get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'GET', params, ...config })
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'POST', data, ...config })
}

export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'PUT', data, ...config })
}

export function del<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<R<T>> {
  return request<T>({ url, method: 'DELETE', params, ...config })
}

export default service
