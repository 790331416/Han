import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export interface OpenVendorUser {
  userId: string | number
  userName?: string
  username?: string
  role?: string
  status?: number
}

export interface OpenVendorApp {
  appId: string | number
  appName?: string
  appType?: string
  lifecycleStatus?: number
}

export interface OpenVendor {
  id: string | number
  name: string
  qualificationNo?: string
  industry?: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  website?: string
  status: number
  reviewInfo?: string
  applyTime?: string
  reviewTime?: string
  users?: OpenVendorUser[]
  apps?: OpenVendorApp[]
}

export interface OpenVendorQuery extends PageQuery {
  name?: string
  status?: number
}

export interface OpenVendorApplication {
  id?: string | number
  applicationId: string | number
  vendorId: string | number
  applicantUserId?: string | number
  applicationNo?: string
  status: number
  applyData?: string
  reason?: string
  reviewerId?: string | number
  reviewTime?: string
  createTime?: string
}

export interface OpenVendorApplicationQuery extends PageQuery {
  vendorId?: string | number
  status?: number
}

export interface OpenVendorApplicationForm {
  name: string
  qualificationNo: string
  industry?: string
  contactName: string
  contactPhone: string
  contactEmail?: string
  website?: string
  applyReason?: string
}

export function listOpenVendor(query: OpenVendorQuery) {
  return get<PageResult<OpenVendor>>('/open/vendor/list', query)
}

export function getOpenVendor(id: string | number) {
  return get<OpenVendor>(`/open/vendor/${id}`)
}

export function listOpenVendorApplications(query: OpenVendorApplicationQuery) {
  return get<PageResult<OpenVendorApplication>>('/open/vendor/applications', query)
}

export function submitOpenVendorApplication(data: OpenVendorApplicationForm) {
  return post<string | number>('/open/vendor/application', data)
}

export function reviewOpenVendorApplication(id: string | number, status: number, reason?: string) {
  return post<void>(`/open/vendor/application/review/${id}`, undefined, { params: { status, reason } })
}

export function updateOpenVendorStatus(id: string | number, status: number, reason?: string) {
  return post<void>(`/open/vendor/${id}/status`, undefined, { params: { status, reason } })
}

export function bindOpenVendorUser(vendorId: string | number, userId: string | number, role: string) {
  return post<void>(`/open/vendor/${vendorId}/bind-user`, undefined, { params: { userId, role } })
}
