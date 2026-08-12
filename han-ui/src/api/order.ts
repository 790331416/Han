import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export type GrantScope = 'WHOLE_CLASS' | 'BY_SUBJECT'
export type OrderStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'FROZEN' | 'EXPIRED' | 'CANCELLED'
export type GrantStatus = 'PENDING' | 'MATERIALIZED' | 'REVOKED' | 'FAILED'

export interface CourseOrder {
  id?: string | number
  orderNo?: string
  listenSchoolId?: string | number
  listenClassId?: string | number
  listenRoomId?: string | number
  listenDeviceId?: string | number
  lectureSchoolId?: string | number
  lectureClassId?: string | number
  semesterId?: string | number
  grantScope?: GrantScope
  status?: OrderStatus
  effectiveTime?: string
  expireTime?: string
  freezeReason?: string
  cancelReason?: string
  remark?: string
}

export interface CourseOrderGrant {
  id?: string | number
  orderId?: string | number
  courseId?: string
  courseName?: string
  courseBeginTime?: string
  listenClassId?: string | number
  subjectId?: string | number
  attendId?: string
  grantStatus?: GrantStatus
  suspendedFlag?: number
  attemptCount?: number
  lastError?: string
  lastAttemptTime?: string
  materializedTime?: string
  revokedTime?: string
}

export interface SyncResult {
  materialized: number
  alreadyMaterialized: number
  failed: number
  revoked: number
}

export interface OrderQuery extends PageQuery {
  listenSchoolId?: string | number
  listenClassId?: string | number
  lectureClassId?: string | number
  semesterId?: string | number
  status?: OrderStatus | ''
}

export interface GrantQuery extends PageQuery {
  orderId?: string | number
  grantStatus?: GrantStatus | ''
}

export interface CreateOrderForm {
  orderNo?: string
  listenClassId?: string | number
  listenRoomId?: string | number
  listenDeviceId?: string | number
  lectureClassId?: string | number
  semesterId?: string | number
  grantScope: GrantScope
  subjectIds?: Array<string | number>
  draft?: boolean
  remark?: string
}

export function listOrders(query: OrderQuery) {
  return get<PageResult<CourseOrder>>('/system/order/courses/list', query)
}

export function getOrder(id: string | number) {
  return get<{ order: CourseOrder; subjectIds: Array<string | number> }>(`/system/order/courses/${id}`)
}

export function createOrder(data: CreateOrderForm) {
  return post<CourseOrder>('/system/order/courses', data)
}

export function updateOrderScope(data: { id: string | number; grantScope: GrantScope; subjectIds?: Array<string | number> }) {
  return post<CourseOrder>('/system/order/courses/scope', data)
}

export function submitOrder(id: string | number) {
  return post<CourseOrder>('/system/order/courses/submit', { id })
}

export function freezeOrder(id: string | number, reason?: string) {
  return post<CourseOrder>('/system/order/courses/freeze', { id, reason })
}

export function unfreezeOrder(id: string | number) {
  return post<SyncResult>('/system/order/courses/unfreeze', { id })
}

export function cancelOrder(id: string | number, reason?: string) {
  return post<CourseOrder>('/system/order/courses/cancel', { id, reason })
}

export function syncOrder(id: string | number) {
  return post<SyncResult>('/system/order/courses/sync', { id })
}

export function listGrants(query: GrantQuery) {
  return get<PageResult<CourseOrderGrant>>('/system/order/grants/list', query)
}

export function retryGrant(id: string | number) {
  return post<SyncResult>('/system/order/grants/retry', { id })
}
