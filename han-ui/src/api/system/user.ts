import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// 用户类型
export interface User {
  userId: number
  tenantId: number
  deptId: number
  deptName?: string
  username: string
  nickname: string
  phone: string
  email: string
  sex: number
  status: number
  avatar: string
  createTime: string
  roleIds?: number[]
  postIds?: number[]
}

export interface UserQuery extends PageQuery {
  username?: string
  nickname?: string
  phone?: string
  status?: number
  deptId?: number
}

export interface UserForm {
  userId?: number
  deptId?: number
  username: string
  nickname: string
  password?: string
  phone?: string
  email?: string
  sex?: number
  status?: number
  roleIds?: number[]
  postIds?: number[]
  remark?: string
}

// 获取用户列表
export function listUser(query: UserQuery) {
  return get<PageResult<User>>('/system/user/list', query)
}

// 获取用户详情
export function getUser(userId: number) {
  return get<User>(`/system/user/${userId}`)
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
export function deleteUser(userId: number) {
  return post<void>(`/system/user/remove/${userId}`)
}

// 批量删除用户
export function deleteUsers(userIds: number[]) {
  return post<void>('/system/user/remove', userIds)
}

// 重置密码
export function resetUserPwd(userId: number, password: string) {
  return post<void>('/system/user/resetPwd', { userId, password })
}

// 修改用户状态
export function changeUserStatus(userId: number, status: number) {
  return post<void>('/system/user/changeStatus', { userId, status })
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
