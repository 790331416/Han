import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export type EducationEntity =
  | 'schools'
  | 'classes'
  | 'people'
  | 'subjects'
  | 'devices'
  | 'semesters'
  | 'rooms'

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

/** 人员统一入口的写入结果；initialPassword 只在服务端生成初始口令时返回一次。 */
export interface PersonResult {
  personId: string | number
  userId?: string | number
  username?: string
  initialPassword?: string
}

export interface PersonMembership {
  id: string | number
  personId: string | number
  classId: string | number
  membershipRole: string
}

export interface PersonAssignment {
  id: string | number
  personId: string | number
  subjectId: string | number
  classId?: string | number
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

export function removeEducation(entity: EducationEntity, ids: (string | number)[]) {
  return post<number>(`/system/education/${entity}/remove`, { ids })
}

export function addPerson(data: EducationRecord) {
  return post<PersonResult>('/system/education/people', data)
}

export function updatePerson(data: EducationRecord) {
  return post<PersonResult>('/system/education/people/edit', data)
}

export function listPersonMemberships(personId: string | number) {
  return get<PersonMembership[]>('/system/education/people/memberships', { personId })
}

export function replacePersonMemberships(
  personId: string | number,
  classIds: (string | number)[],
  membershipRole?: string
) {
  return post<number>('/system/education/people/memberships', { personId, classIds, membershipRole })
}

export function listPersonAssignments(personId: string | number) {
  return get<PersonAssignment[]>('/system/education/people/subjects', { personId })
}

/** 读回人员登录账号已有的角色，编辑时必须回填，否则提交空数组会清空角色。 */
export function listPersonRoles(personId: string | number) {
  return get<(string | number)[]>('/system/education/people/roles', { personId })
}

export function replacePersonAssignments(
  personId: string | number,
  subjectIds: (string | number)[],
  classId?: string | number
) {
  return post<number>('/system/education/people/subjects', { personId, subjectIds, classId })
}
