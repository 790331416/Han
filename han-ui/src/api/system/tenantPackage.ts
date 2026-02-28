import { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface TenantPackage {
  packageId: number
  packageName: string
  menuIds: number[]
  tenantCount: number
  status: number
  createTime: string
  remark: string
}

export interface TenantPackageForm {
  packageId?: number
  packageName: string
  menuIds?: number[]
  status: number
  remark?: string
}

// 查询套餐列表
export function listTenantPackage(query: { pageNum: number; pageSize: number; packageName?: string; status?: number }) {
  return get<PageResult<TenantPackage>>('/tenant/package/list', query)
}

// 查询所有有效套餐
export function listAllPackage() {
  return get<TenantPackage[]>('/tenant/package/all')
}

// 获取套餐详情
export function getTenantPackage(packageId: number) {
  return get<TenantPackage>(`/tenant/package/${packageId}`)
}

// 新增套餐
export function addTenantPackage(data: TenantPackageForm) {
  return post<number>('/tenant/package', data)
}

// 修改套餐
export function updateTenantPackage(data: TenantPackageForm) {
  return post<void>('/tenant/package/edit', data)
}

// 删除套餐
export function deleteTenantPackage(packageId: number) {
  return post<void>(`/tenant/package/remove/${packageId}`)
}

// 修改套餐状态
export function changeTenantPackageStatus(packageId: number, status: number) {
  return post<void>('/tenant/package/changeStatus', null, { params: { packageId, status } })
}

// 获取套餐菜单
export function getPackageMenus(packageId: number) {
  return get<number[]>(`/tenant/package/menus/${packageId}`)
}

// 更新套餐菜单
export function updatePackageMenus(packageId: number, menuIds: number[]) {
  return post<void>(`/tenant/package/menus/${packageId}`, menuIds)
}
