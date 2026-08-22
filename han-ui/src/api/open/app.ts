import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface OpenApp {
  appId: string | number
  appName: string
  appKey: string
  vendorId?: string | number
  vendorName?: string
  appIcon?: string
  appDesc?: string
  appType?: string
  redirectUris?: string[]
  scopes?: string[]
  schoolIds?: (string | number)[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  status: number
  lifecycleStatus?: number
  environmentPolicy?: string
  contactName?: string
  createTime?: string
}

export interface OpenAppQuery extends PageQuery {
  appName?: string
  appType?: string
  status?: number
  lifecycleStatus?: number
}

export interface OpenAppForm {
  appId?: string | number
  vendorId?: string | number
  appName: string
  appDesc?: string
  appType?: string
  redirectUris?: string[]
  scopes?: string[]
  schoolIds?: (string | number)[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  status?: number
  lifecycleStatus?: number
  environmentPolicy?: string
  contactName?: string
}

export interface OpenApiResource {
  id: string | number
  resourceCode: string
  resourceName: string
  category: string
  httpMethod: string
  path: string
  scopeCode: string
  description?: string
  sensitivity?: string
  status: number
  sort?: number
}

export function listOpenApp(query: OpenAppQuery) {
  return get<PageResult<OpenApp>>('/open/app/list', query)
}

export function getOpenApp(id: string | number) {
  return get<OpenApp>(`/open/app/${id}`)
}

export interface OpenAppCredential {
  appId: string | number
  appKey: string
  appSecret: string
}

export function addOpenApp(data: OpenAppForm) {
  return post<OpenAppCredential>('/open/app', data)
}

export function updateOpenApp(data: OpenAppForm) {
  return post<void>('/open/app/edit', {
    ...data,
    id: data.appId ?? undefined
  })
}

export function deleteOpenApp(id: string | number) {
  return post<void>(`/open/app/remove/${id}`)
}

export function resetAppSecret(appId: string | number) {
  return post<string>(`/open/app/resetSecret/${appId}`)
}

export function changeAppStatus(appId: string | number, status: number) {
  return post<void>('/open/app/changeStatus', { appId, base: { status } })
}

export function changeAppLifecycleStatus(appId: string | number, lifecycleStatus: number) {
  return post<void>('/open/app/changeLifecycleStatus', undefined, { params: { appId, lifecycleStatus } })
}

export function listOpenApiResources() {
  return get<OpenApiResource[]>('/open/app/api-resources')
}
