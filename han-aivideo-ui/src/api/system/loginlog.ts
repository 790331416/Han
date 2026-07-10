import { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface LoginLog {
  id: string | number
  tenantId: string | number
  username: string
  clientType: string
  ipAddr: string
  loginLocation: string
  browser: string
  os: string
  status: number
  message: string
  loginTime: string
}

// 查询登录日志列表
export function listLoginLog(query: { pageNum: number; pageSize: number; username?: string; status?: number; ipaddr?: string }) {
  return get<PageResult<LoginLog>>('/system/loginlog/list', query)
}

// 删除登录日志
export function deleteLoginLog(ids: (string | number)[]) {
  return post<void>('/system/loginlog/remove', ids)
}

// 清空登录日志
export function cleanLoginLog() {
  return post<void>('/system/loginlog/clean')
}
