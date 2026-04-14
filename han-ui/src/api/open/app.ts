import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface OpenApp {
  appId: string | number
  appName: string
  appKey: string
  appIcon?: string
  appDesc?: string
  appType?: string
  redirectUris?: string[]
  scopes?: string[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  status: number
  contactName?: string
  createTime?: string
}

export interface OpenAppQuery extends PageQuery {
  appName?: string
  appType?: string
  status?: number
}

export interface OpenAppForm {
  appId?: string | number
  appName: string
  appDesc?: string
  appType?: string
  redirectUris?: string[]
  scopes?: string[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  status?: number
  contactName?: string
}

export function listOpenApp(query: OpenAppQuery) {
  return get<PageResult<OpenApp>>('/open/app/list', query)
}

export function getOpenApp(id: string | number) {
  return get<OpenApp>(`/open/app/${id}`)
}

export function addOpenApp(data: OpenAppForm) {
  return post<void>('/open/app', data)
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
