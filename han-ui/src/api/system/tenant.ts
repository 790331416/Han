import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Tenant {
  tenantId: number
  tenantName: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  packageId?: number
  packageName?: string
  userLimit?: number
  userCount?: number
  expireTime?: string
  isolationType?: string
  domain?: string
  status: number
  remark?: string
  createTime?: string
}

export interface TenantQuery extends PageQuery {
  tenantName?: string
  contactName?: string
  status?: number
}

export interface TenantForm {
  tenantId?: number
  tenantName: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  packageId?: number
  userLimit?: number
  expireTime?: string
  isolationType?: string
  domain?: string
  status?: number
  remark?: string
}

export function listTenant(query: TenantQuery) {
  return get<PageResult<Tenant>>('/tenant/list', query)
}

export function getTenant(id: number) {
  return get<Tenant>(`/tenant/${id}`)
}

export function addTenant(data: TenantForm) {
  return post<void>('/tenant', data)
}

export function updateTenant(data: TenantForm) {
  return post<void>('/tenant/edit', data)
}

export function deleteTenant(id: number) {
  return post<void>(`/tenant/remove/${id}`)
}

export function changeTenantStatus(tenantId: number, status: number) {
  return post<void>('/tenant/changeStatus', { tenantId, base: { status } })
}
