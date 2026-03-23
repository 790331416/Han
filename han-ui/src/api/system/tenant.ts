import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Tenant {
  tenantId: string | number
  tenantName: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  packageId?: string | number
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
  tenantId?: string | number
  tenantName: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  packageId?: string | number
  userLimit?: number
  expireTime?: string
  isolationType?: string
  domain?: string
  status?: number
  remark?: string
  adminUsername?: string
  adminPassword?: string
}

export function listTenant(query: TenantQuery) {
  return get<PageResult<Tenant>>('/tenant/list', query)
}

export function getTenant(id: string | number) {
  return get<Tenant>(`/tenant/${id}`)
}

export function addTenant(data: TenantForm) {
  return post<void>('/tenant', data)
}

export function updateTenant(data: TenantForm) {
  return post<void>('/tenant/edit', data)
}

export function deleteTenant(id: string | number) {
  return post<void>(`/tenant/remove/${id}`)
}

export function changeTenantStatus(tenantId: string | number, status: number) {
  return post<void>(`/tenant/changeStatus?tenantId=${tenantId}&status=${status}`)
}

export function getTenantAdminUser(tenantId: string | number) {
  return get<string | number>(`/tenant/adminUser`, { tenantId })
}
