import { get, post } from '@/utils/request'

export interface Dept {
  id: string | number
  parentId: string | number
  deptName: string
  leaderId?: string | number
  leaderName?: string
  phone?: string
  email?: string
  sort: number
  status: number
  createTime?: string
  children?: Dept[]
}

export interface DeptQuery {
  deptName?: string
  status?: number
}

export interface DeptForm {
  deptId?: string | number
  parentId: string | number
  deptName: string
  leaderId?: string | number
  phone?: string
  email?: string
  sort?: number
  status?: number
}

export function listDept(query?: DeptQuery) {
  return get<Dept[]>('/system/dept/list', query)
}

export function getDeptTree(query?: DeptQuery) {
  return get<Dept[]>('/system/dept/tree', query)
}

export function getDept(id: string | number) {
  return get<Dept>(`/system/dept/info/${id}`)
}

export function addDept(data: DeptForm) {
  return post<void>('/system/dept', data)
}

export function updateDept(data: DeptForm) {
  return post<void>('/system/dept/edit', data)
}

export function deleteDept(id: string | number) {
  return post<void>(`/system/dept/remove/${id}`)
}
