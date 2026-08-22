import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export interface OpenAuthorizationRequest {
  id: string | number
  appId: string | number
  appName?: string
  grantId?: string | number
  environment: 'SANDBOX' | 'PROD' | string
  requestType?: number
  status: number
  requestData?: string
  reason?: string
  applyReason?: string
  reviewReason?: string
  reviewTime?: string
  createTime?: string
}

export interface OpenAuthorizationRequestQuery extends PageQuery {
  appId?: string | number
  environment?: string
  status?: number
}

export interface OpenGrant {
  id: string | number
  appId: string | number
  resourceId: string | number
  resourceCode?: string
  resourceName?: string
  environment?: 'SANDBOX' | 'PROD' | string
  versionId?: string | number
  version?: string
  scopes?: string
  dataScope?: string
  quota?: number
  usedCount?: number
  expiresAt?: string
  status: number
  applyReason?: string
  reviewReason?: string
  reviewTime?: string
  createTime?: string
}

export interface OpenCredential {
  id: string | number
  appId: string | number
  appName?: string
  environment: 'SANDBOX' | 'PROD' | string
  clientId?: string
  status?: number
  rotatedAt?: string
  expireAt?: string
  createTime?: string
}

export interface OpenCredentialSecret extends OpenCredential {
  clientSecret?: string
}

export function listAuthorizationRequests(query: OpenAuthorizationRequestQuery) {
  return get<PageResult<OpenAuthorizationRequest>>('/open/authorization/request/list', query)
}

export function reviewAuthorizationRequest(id: string | number, status: number, reason?: string) {
  return post<void>(`/open/authorization/review/${id}`, undefined, { params: { status, reason } })
}

export function listAppGrants(appId: string | number) {
  return get<OpenGrant[]>(`/open/authorization/app/${appId}`)
}

export function revokeAppGrant(id: string | number, reason?: string) {
  return post<void>(`/open/authorization/revoke/${id}`, undefined, { params: { reason } })
}

export function listAppCredentials(appId?: string | number) {
  return get<OpenCredential[]>('/open/authorization/credential/list', appId === undefined ? undefined : { appId })
}

export function generateAppCredential(appId: string | number, environment: string) {
  return post<OpenCredentialSecret>('/open/authorization/credential/generate', undefined, { params: { appId, environment } })
}

export function rotateAppCredential(id: string | number) {
  return post<OpenCredentialSecret>(`/open/authorization/credential/rotate/${id}`)
}
