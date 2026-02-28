import { get, post } from '@/utils/request'

export interface Menu {
  id: number
  parentId: number
  menuName: string
  menuType: string
  path?: string
  component?: string
  query?: string
  perms?: string
  icon?: string
  sort: number
  visible: number
  status: number
  isFrame: number
  isCache: number
  createTime?: string
  children?: Menu[]
}

export interface MenuQuery {
  menuName?: string
  status?: number
}

export interface MenuForm {
  id?: number
  parentId: number
  menuName: string
  menuType: string
  path?: string
  component?: string
  query?: string
  perms?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
  isFrame?: number
  isCache?: number
}

export function listMenu(query?: MenuQuery) {
  return get<Menu[]>('/system/menu/list', query)
}

export function getMenuTree() {
  return get<Menu[]>('/system/menu/tree')
}

export function getMenu(id: number) {
  return get<Menu>(`/system/menu/${id}`)
}

export function addMenu(data: MenuForm) {
  return post<void>('/system/menu', data)
}

export function updateMenu(data: MenuForm) {
  return post<void>('/system/menu/edit', data)
}

export function deleteMenu(id: number) {
  return post<void>(`/system/menu/remove/${id}`)
}
