import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface OpenApp {
  appId: string | number
  appName: string
  appKey: string
  appIcon?: string
  appDesc?: string
  appType?: string
  logoutUri?: string
  redirectUris?: string[]
  scopes?: string[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  requirePkce?: number
  autoApprove?: number
  status: number
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  remark?: string
  createTime?: string
}

// 授权类型选项。取值与后端 OAuth2ServiceImpl.token 的分支保持一致，
// 后端会按应用登记的列表校验 grant_type，前端必须提供录入入口。
export const grantTypeOptions = [
  { label: '授权码模式 authorization_code', value: 'authorization_code' },
  { label: '刷新令牌 refresh_token', value: 'refresh_token' },
  { label: '客户端凭证 client_credentials', value: 'client_credentials' }
]

// 常用授权范围建议值，允许自定义输入
export const scopeSuggestions = ['openid', 'profile', 'email', 'phone']

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
  logoutUri?: string
  redirectUris?: string[]
  scopes?: string[]
  grantTypes?: string[]
  accessTokenTtl?: number
  refreshTokenTtl?: number
  requirePkce?: number
  autoApprove?: number
  status?: number
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  remark?: string
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
