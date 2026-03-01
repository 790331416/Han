import { get, post, postParams } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Role {
  id: number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope: string
  menuCheckStrictly: number
  deptCheckStrictly: number
  status: number
  remark?: string
  createTime?: string
  menuIds?: number[]
  deptIds?: number[]
}

export interface RoleQuery extends PageQuery {
  roleName?: string
  roleKey?: string
  status?: number
}

export interface RoleForm {
  id?: number
  roleName: string
  roleKey: string
  roleSort?: number
  dataScope?: string
  status?: number
  remark?: string
  menuIds?: number[]
  deptIds?: number[]
}

export function listRole(query: RoleQuery) {
  return get<PageResult<Role>>('/system/role/list', query)
}

export function listAllRoles() {
  return get<Role[]>('/system/role/all')
}

export function getRole(id: number) {
  return get<Role>(`/system/role/info/${id}`)
}

export function addRole(data: RoleForm) {
  return post<void>('/system/role', data)
}

export function updateRole(data: RoleForm) {
  return post<void>('/system/role/edit', data)
}

export function deleteRole(id: number) {
  return post<void>(`/system/role/remove/${id}`)
}

export function changeRoleStatus(roleId: number, status: number) {
  return postParams<void>('/system/role/changeStatus', { roleId, status })
}

export function getRoleMenuIds(roleId: number) {
  return get<number[]>(`/system/role/menuIds/${roleId}`)
}
