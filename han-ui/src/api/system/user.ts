import { get, post, postParams } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// 用户类型
export interface User {
  userId: string | number
  tenantId: string | number
  deptId: string | number
  deptName?: string
  username: string
  nickname: string
  phone: string
  email: string
  sex: number
  status: number
  avatar: string
  createTime: string
  roleIds?: (string | number)[]
  postIds?: (string | number)[]
}

export interface UserQuery extends PageQuery {
  username?: string
  nickname?: string
  phone?: string
  status?: number
  deptId?: string | number
  accountType?: 'SYSTEM' | 'CLIENT'
}

export interface UserForm {
  userId?: string | number
  deptId?: string | number
  username: string
  nickname: string
  password?: string
  phone?: string
  email?: string
  sex?: number
  status?: number
  roleIds?: (string | number)[]
  postIds?: (string | number)[]
  remark?: string
}

// 获取用户列表
export function listUser(query: UserQuery) {
  return get<PageResult<User>>('/system/user/list', query)
}

/** 仅返回关联有效教育人员的同表客户端用户，服务端强制 CLIENT 查询条件。 */
export function listClientUser(query: Omit<UserQuery, 'accountType'>) {
  return get<PageResult<User>>('/system/user/client/list', query)
}

// 获取用户详情
export function getUser(userId: string | number) {
  return get<User>(`/system/user/info/${userId}`)
}

// 新增用户
export function addUser(data: UserForm) {
  return post<void>('/system/user', data)
}

// 修改用户
export function updateUser(data: UserForm) {
  return post<void>('/system/user/edit', data)
}

// 删除用户
export function deleteUser(userId: string | number) {
  return post<void>(`/system/user/remove/${userId}`)
}

// 批量删除用户
export function deleteUsers(userIds: (string | number)[]) {
  return post<void>('/system/user/remove', userIds)
}

// 重置密码
export function resetUserPwd(userId: string | number, password: string) {
  return postParams<void>('/system/user/resetPwd', { userId, password })
}

// 修改用户状态
export function changeUserStatus(userId: string | number, status: number) {
  return postParams<void>('/system/user/changeStatus', { userId, status })
}

// ==================== 简单用户列表（下拉选择） ====================

export interface SimpleUser {
  userId: string | number
  nickname: string
  phone?: string
  email?: string
}

export function listSimpleUser() {
  return get<SimpleUser[]>('/system/user/simple-list')
}

// ==================== 个人中心 ====================

// 获取个人信息
export function getUserProfile() {
  return get<User>('/system/user/profile')
}

// 修改个人信息
export function updateUserProfile(data: { nickname: string; phone?: string; email?: string; sex?: number }) {
  return post<void>('/system/user/profile/edit', data)
}

// 修改个人密码
export function updateUserPassword(data: { oldPassword: string; newPassword: string }) {
  return post<void>('/system/user/profile/password', data)
}

// 更新头像
export function updateUserAvatar(data: { avatar: string }) {
  return post<void>('/system/user/profile/avatar', data)
}

/** 上传个人头像，保存时只使用文件服务返回的受控访问地址。 */
export function uploadUserAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', 'avatar')
  formData.append('visibility', 'PUBLIC')
  return post<{ id: string | number; name: string; url: string }>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 导入导出 ====================

// 导出用户
export function exportUser(query: UserQuery) {
  return get<Blob>('/system/user/export', query, { responseType: 'blob' })
}

// 下载导入模板
export function importTemplate() {
  return get<Blob>('/system/user/importTemplate', {}, { responseType: 'blob' })
}

// 导入用户
export function importUser(file: File, updateSupport: boolean = false) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('updateSupport', String(updateSupport))
  return post<string>('/system/user/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
