import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export type EducationEntity = 'schools' | 'classes' | 'people' | 'subjects' | 'devices'

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
