import { get, post } from '@/utils/request'
import type { OpenVendor } from '@/api/open/vendor'

/** 登录前厂商入驻申请的完整表单。密码只在当前请求内使用，不写入本地存储。 */
export interface PublicVendorApplicationForm {
  username: string
  nickname: string
  password: string
  phone: string
  email?: string
  name?: string
  qualificationNo?: string
  industry?: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  website?: string
  applyReason?: string
  code?: string
  uuid?: string
}

export interface PublicVendorApplicationRequest extends Omit<PublicVendorApplicationForm, 'password' | 'code' | 'uuid'> {
  encryptedPassword?: string
  /** 仅在系统设置明确开启 HTTP 测试兼容时发送，正式 HTTPS 环境不会使用。 */
  plainPassword?: string
  captchaCode?: string
  captchaUuid?: string
}

export interface PublicVendorApplicationStatus {
  applicationNo?: string
  status?: number
  statusName?: string
  reason?: string
  createTime?: string
  reviewTime?: string
}

export function getVendorPublicKey() {
  return get<{ enabled: boolean; publicKey?: string; allowInsecureHttp?: boolean }>('/auth/vendor/publicKey')
}

export function submitPublicVendorApplication(data: PublicVendorApplicationRequest) {
  return post<{ applicationNo?: string } | string>('/auth/vendor/register', data)
}

export function getPublicVendorApplication(contactPhone: string) {
  return get<PublicVendorApplicationStatus>(
    '/auth/vendor/application/status',
    { contactPhone }
  )
}

export interface GrantApplyResource {
  resourceId: string | number
  scopes: string
  quota?: number
  expireDays?: number
}

export interface GrantApplyForm {
  appId: string | number
  environment: 'SANDBOX' | 'PROD'
  resources: GrantApplyResource[]
  applyReason: string
}

export function submitGrantApply(data: GrantApplyForm) {
  return post<string | number>('/open/authorization/apply', data)
}

/** 厂商门户只读取当前登录用户关联的厂商，后端负责最终归属校验。 */
export function listMyOpenVendors() {
  return get<OpenVendor[]>('/open/vendor/my')
}

export interface OpenApiTestRun {
  id?: string | number
  appId: string | number
  resourceId: string | number
  environment: 'SANDBOX' | 'PROD'
  requestMethod: string
  requestPath: string
  statusCode: number
  result: 'SUCCESS' | 'FAIL'
  traceId?: string
  durationMs: number
  responseSize?: number
  createTime?: string
}

export interface OpenApiTestRunForm {
  appId: string | number
  resourceId: string | number
  environment: 'SANDBOX' | 'PROD'
  statusCode: number
  businessSuccess: boolean
  durationMs: number
  responseSize: number
  traceId?: string
}

/** 只提交脱敏后的状态摘要；响应正文、headers、token 和 secret 不进入该请求。 */
export function addOpenApiTestRun(data: OpenApiTestRunForm) {
  return post<OpenApiTestRun>('/open/debug/run/add', data)
}

export function listOpenApiTestRuns(appId?: string | number) {
  return get<OpenApiTestRun[]>('/open/debug/run/list', appId === undefined ? undefined : { appId })
}
