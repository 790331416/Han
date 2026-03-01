import { get, post } from '@/utils/request'

export interface Dept {
  id: number
  parentId: number
  deptName: string
  leader?: string
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
  id?: number
  parentId: number
  deptName: string
  leader?: string
  phone?: string
  email?: string
  sort?: number
  status?: number
}

export function listDept(query?: DeptQuery) {
  return get<Dept[]>('/system/dept/list', query)
}

export function getDeptTree() {
  return get<Dept[]>('/system/dept/tree')
}

export function getDept(id: number) {
  return get<Dept>(`/system/dept/info/${id}`)
}

export function addDept(data: DeptForm) {
  return post<void>('/system/dept', data)
}

export function updateDept(data: DeptForm) {
  return post<void>('/system/dept/edit', data)
}

export function deleteDept(id: number) {
  return post<void>(`/system/dept/remove/${id}`)
}
