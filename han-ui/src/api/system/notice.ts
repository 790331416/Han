import { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface Notice {
  id: string | number
  tenantId: string | number
  noticeTitle: string
  noticeType: string
  noticeContent: string
  status: number
  read?: boolean
  readTime?: string
  createName: string
  createTime: string
  remark: string
}

export interface NoticeForm {
  id?: string | number
  noticeTitle: string
  noticeType: string
  noticeContent: string
  status: number
  remark?: string
}

// 查询通知公告列表
export function listNotice(query: { pageNum: number; pageSize: number; noticeTitle?: string; noticeType?: string; status?: number }) {
  return get<PageResult<Notice>>('/system/notice/list', query)
}

// 查询通知公告详情
export function getNotice(id: string | number) {
  return get<Notice>(`/system/notice/${id}`)
}

// 新增通知公告
export function addNotice(data: NoticeForm) {
  return post<void>('/system/notice/add', data)
}

// 修改通知公告
export function updateNotice(data: NoticeForm) {
  return post<void>('/system/notice/edit', data)
}

// 删除通知公告
export function deleteNotice(id: string | number) {
  return post<void>(`/system/notice/remove/${id}`)
}

// 批量删除通知公告
export function deleteNotices(ids: (string | number)[]) {
  return post<void>('/system/notice/remove', ids)
}

// 获取最新通知（铃铛下拉）
export function getLatestNotices(limit = 5) {
  return get<Notice[]>('/system/notice/latest', { limit })
}

// 获取未读通知数量
export function getUnreadCount() {
  return get<number>('/system/notice/unreadCount')
}

// 标记单条通知已读
export function markNoticeRead(id: string | number) {
  return post<void>(`/system/notice/markRead/${id}`)
}

// 全部标记已读
export function markAllNoticeRead() {
  return post<void>('/system/notice/markAllRead')
}
