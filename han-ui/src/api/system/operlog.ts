import { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface OperLog {
  id: number
  tenantId: number
  title: string
  businessType: number
  method: string
  requestMethod: string
  operatorType: number
  operName: string
  deptName: string
  operUrl: string
  operIp: string
  operLocation: string
  operParam: string
  jsonResult: string
  status: number
  errorMsg: string
  operTime: string
  costTime: number
}

// 查询操作日志列表
export function listOperLog(query: { pageNum: number; pageSize: number; title?: string; businessType?: number; status?: number; operName?: string }) {
  return get<PageResult<OperLog>>('/system/operlog/list', query)
}

// 查询操作日志详情
export function getOperLog(id: number) {
  return get<OperLog>(`/system/operlog/${id}`)
}

// 删除操作日志
export function deleteOperLog(ids: number[]) {
  return post<void>('/system/operlog/remove', ids)
}

// 清空操作日志
export function cleanOperLog() {
  return post<void>('/system/operlog/clean')
}
