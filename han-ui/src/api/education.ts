import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export type EducationEntity = 'schools' | 'classes' | 'people' | 'subjects' | 'devices' | 'rooms'

export interface EducationRecord {
  id?: string | number
  sourceSystem?: string
  status?: number
  remark?: string
  [key: string]: any
}

export interface EducationQuery extends PageQuery {
  keyword?: string
  status?: number | ''
  schoolId?: string | number
  roomId?: string | number
  personType?: string
}

export function listEducation(entity: EducationEntity, query: EducationQuery) {
  return get<PageResult<EducationRecord>>(`/system/education/${entity}/list`, query)
}

export function addEducation(entity: EducationEntity, data: EducationRecord) {
  return post<string | number>(`/system/education/${entity}`, data)
}

export function updateEducation(entity: EducationEntity, data: EducationRecord) {
  return post<string | number>(`/system/education/${entity}/edit`, data)
}

// 学期不走通用 CRUD 页：它没有 source_system 列，而且有日期区间和阶段三态要单独渲染。
export type SemesterLifecycle = 'NOT_STARTED' | 'IN_PROGRESS' | 'FINISHED'

export interface Semester {
  id?: string | number
  semesterCode: string
  semesterName: string
  beginDate: string
  endDate: string
  currentFlag: number
  status: number
  lifecycleStatus?: SemesterLifecycle
  remark?: string
}

export interface SemesterQuery extends PageQuery {
  keyword?: string
  status?: number | ''
  lifecycleStatus?: SemesterLifecycle | ''
}

export function listSemesters(query: SemesterQuery) {
  return get<PageResult<Semester>>('/system/education/semesters/list', query)
}

export function addSemester(data: Semester) {
  return post<string | number>('/system/education/semesters', data)
}

export function updateSemester(data: Semester) {
  return post<string | number>('/system/education/semesters/edit', data)
}
