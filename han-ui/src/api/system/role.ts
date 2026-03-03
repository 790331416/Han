import { get, post, postParams } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Role {
  id: string | number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope: string
  menuCheckStrictly: number
  deptCheckStrictly: number
  status: number
  remark?: string
  createTime?: string
  menuIds?: (string | number)[]
  deptIds?: (string | number)[]
}

export interface RoleQuery extends PageQuery {
  roleName?: string
  roleKey?: string
  status?: number
}

export interface RoleForm {
  roleId?: string | number
  roleName: string
  roleKey: string
  roleSort?: number
  dataScope?: string
  status?: number
  remark?: string
  menuIds?: (string | number)[]
  deptIds?: (string | number)[]
}

export function listRole(query: RoleQuery) {
  return get<PageResult<Role>>('/system/role/list', query)
}

export function listAllRoles() {
  return get<Role[]>('/system/role/all')
}

export function getRole(id: string | number) {
  return get<Role>(`/system/role/info/${id}`)
}

export function addRole(data: RoleForm) {
  return post<void>('/system/role', data)
}

export function updateRole(data: RoleForm) {
  return post<void>('/system/role/edit', data)
}

export function deleteRole(id: string | number) {
  return post<void>(`/system/role/remove/${id}`)
}

export function changeRoleStatus(roleId: string | number, status: number) {
  return postParams<void>('/system/role/changeStatus', { roleId, status })
}

export function getRoleMenuIds(roleId: string | number) {
  return get<(string | number)[]>(`/system/role/menuIds/${roleId}`)
}

// ==================== 角色分配用户 ====================

export interface AllocatedUserQuery extends PageQuery {
  roleId: string | number
  username?: string
  phone?: string
}

export interface AllocatedUser {
  id: string | number
  username: string
  nickname: string
  phone?: string
  status: number
  createTime?: string
}

export function listAllocatedUsers(query: AllocatedUserQuery) {
  return get<PageResult<AllocatedUser>>('/system/role/authUser/list', query)
}

export function listUnallocatedUsers(query: AllocatedUserQuery) {
  return get<PageResult<AllocatedUser>>('/system/role/authUser/unallocated', query)
}

export function authUserSelectAll(roleId: string | number, userIds: (string | number)[]) {
  return post<void>(`/system/role/authUser/selectAll?roleId=${roleId}`, userIds)
}

export function authUserCancel(roleId: string | number, userIds: (string | number)[]) {
  return post<void>(`/system/role/authUser/cancel?roleId=${roleId}`, userIds)
}
