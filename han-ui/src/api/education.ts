import { get, post, postParams } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

// 不含 semesters：学期走 EducationCalendarController 与独立页面，不进通用 CRUD。
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
  phone?: string
  dutyCode?: string
}

export type OrganizationType = 'EDU_BUREAU' | 'SCHOOL'

/** 教育局、中心校、校区统一使用同一棵组织树。 */
export interface EducationOrganizationNode {
  id: string | number
  parentId?: string | number | null
  schoolCode: string
  schoolName: string
  orgType: OrganizationType
  schoolManageType?: string
  schoolProperty?: string
  regionId?: string | number | null
  regionCode?: string
  regionName?: string
  nodeLevel?: number
  autoUpgradeEnabled: number
  status: number
  children: EducationOrganizationNode[]
}

export interface EducationOrganizationForm {
  id?: string | number
  parentId?: string | number | null
  schoolName: string
  orgType: OrganizationType
  schoolManageType?: string
  schoolProperty?: string
  regionId?: string | number | null
  autoUpgradeEnabled: number
  status: number
  remark?: string
}

export function listOrganizationTree(status?: number) {
  return get<EducationOrganizationNode[]>('/system/education/organizations/tree', status === undefined ? {} : { status })
}

export function addOrganization(data: EducationOrganizationForm) {
  return post<string | number>('/system/education/organizations', data)
}

export function updateOrganization(data: EducationOrganizationForm) {
  return post<string | number>('/system/education/organizations/edit', data)
}
export function removeOrganizations(ids: (string | number)[]) {
  return post<number>('/system/education/organizations/remove', { ids })
}

export interface EducationRegionNode {
  id: string | number
  parentId?: string | number | null
  regionCode: string
  regionName: string
  regionLevel: string
  sourceSystem?: string
  nodeLevel?: number
  sort: number
  status: number
  children: EducationRegionNode[]
}
export interface EducationRegionForm {
  id?: string | number
  parentId?: string | number | null
  regionName: string
  regionLevel: string
  sort: number
  status: number
  remark?: string
}
export function listRegionTree(status?: number) {
  return get<EducationRegionNode[]>('/system/education/regions/tree', status === undefined ? {} : { status })
}
export interface EduRegionOption {
  id: string | number
  regionCode: string
  regionName: string
  regionLevel: string
  nodeLevel?: number
  sourceSystem?: string
  status?: number
  parentId?: string | number | null
}
export interface EducationRegionSearchOption extends EduRegionOption { pathLabel: string }
export function listRegionOptions(keyword?: string, parentId?: string | number) {
  const params: Record<string, string | number> = {}
  if (keyword) params.keyword = keyword
  if (parentId !== undefined && parentId !== null) params.parentId = parentId
  return get<EduRegionOption[]>('/system/education/regions/options', params)
}
export function searchRegionOptions(keyword: string) {
  return get<EducationRegionSearchOption[]>('/system/education/regions/options/search', { keyword })
}
export function listRegionChildren(parentId?: string | number, status?: number) {
  const params: Record<string, string | number> = {}
  if (parentId !== undefined && parentId !== null) params.parentId = parentId
  if (status !== undefined) params.status = status
  return get<EduRegionOption[]>('/system/education/regions/children', params)
}
export function listRegionPath(regionId: string | number) {
  return get<EduRegionOption[]>('/system/education/regions/path', { regionId })
}
export function addRegion(data: EducationRegionForm) { return post<string | number>('/system/education/regions', data) }
export function updateRegion(data: EducationRegionForm) { return post<string | number>('/system/education/regions/edit', data) }
export function removeRegions(ids: (string | number)[]) { return post<number>('/system/education/regions/remove', { ids }) }

export type AcademicYearStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED'

export interface AcademicYear {
  id?: string | number
  schoolId: string | number
  yearCode: string
  yearName: string
  beginDate: string
  endDate: string
  status: AcademicYearStatus
  remark?: string
}

export interface AcademicYearQuery extends PageQuery {
  schoolId?: string | number
  keyword?: string
  status?: AcademicYearStatus | ''
}

export function listAcademicYears(query: AcademicYearQuery) {
  return get<PageResult<AcademicYear>>('/system/education/academic-years/list', query)
}

export function addAcademicYear(data: AcademicYear) {
  return post<string | number>('/system/education/academic-years', data)
}

export function updateAcademicYear(data: AcademicYear) {
  return post<string | number>('/system/education/academic-years/edit', data)
}

export function removeAcademicYears(ids: (string | number)[]) {
  return post<number>('/system/education/academic-years/remove', { ids })
}

export type TeachingNodeType = 'GRADE' | 'MAJOR' | 'CLASS'
export interface EducationClassTreeNode {
  id: string | number
  schoolId: string | number
  parentId?: string | number | null
  academicYearId?: string | number | null
  classCode: string
  className: string
  gradeCode?: string
  branchCode?: string
  nodeType: TeachingNodeType
  cohortYear?: number
  nodeLevel?: number
  sort: number
  status: number
  children: EducationClassTreeNode[]
}
export interface EducationClassTreeForm {
  id?: string | number
  schoolId: string | number
  parentId?: string | number | null
  academicYearId?: string | number | null
  className: string
  nodeType: TeachingNodeType
  branchCode?: string
  cohortYear?: number
  classRole?: string
  sort: number
  status: number
  remark?: string
}
export function listClassTree(params: { schoolId: string | number; academicYearId?: string | number; status?: number }) {
  return get<EducationClassTreeNode[]>('/system/education/class-tree', params)
}
export function addClassTreeNode(data: EducationClassTreeForm) { return post<string | number>('/system/education/class-tree', data) }
export function updateClassTreeNode(data: EducationClassTreeForm) { return post<string | number>('/system/education/class-tree/edit', data) }
export function removeClassTreeNodes(ids: (string | number)[]) { return post<number>('/system/education/class-tree/remove', { ids }) }
export interface EducationClassTreeRange {
  schoolId: string | number
  academicYearId: string | number
  parentId?: string | number | null
  nodeType: 'GRADE' | 'CLASS'
  cohortYear?: number
  startNo: number
  endNo: number
  status: number
}
export function batchCreateClassTreeNodes(data: EducationClassTreeRange) { return post<number>('/system/education/class-tree/batch', data) }

export type PlaceNodeType = 'BUILDING' | 'FLOOR' | 'PLACE'
export interface EducationPlaceTreeNode { id:string|number; schoolId:string|number; parentId?:string|number|null; roomCode:string; roomName:string; aliasName?:string; roomType?:string; nodeType:PlaceNodeType; nodeLevel?:number; sort:number; capacity?:number; status:number; children:EducationPlaceTreeNode[] }
export interface EducationPlaceTreeForm { id?:string|number; schoolId:string|number; parentId?:string|number|null; roomName:string; nodeType:PlaceNodeType; aliasName?:string; roomType?:string; capacity?:number; sort:number; status:number; remark?:string }
export function listPlaceTree(params:{schoolId:string|number;status?:number}) { return get<EducationPlaceTreeNode[]>('/system/education/place-tree',params) }
export function addPlaceTreeNode(data:EducationPlaceTreeForm) { return post<string|number>('/system/education/place-tree',data) }
export function updatePlaceTreeNode(data:EducationPlaceTreeForm) { return post<string|number>('/system/education/place-tree/edit',data) }
export function removePlaceTreeNodes(ids: (string | number)[]) { return post<number>('/system/education/place-tree/remove', { ids }) }
export interface EducationFloorRange { schoolId: string | number; buildingId: string | number; startNo: number; endNo: number; status: number }
export function batchCreateFloors(data: EducationFloorRange) { return post<number>('/system/education/place-tree/batch-floors', data) }

export type EducationScopeType = 'ORG'
export interface EducationScopeItem {
  id?: string | number
  userId?: string | number
  scopeType: EducationScopeType
  scopeId: string | number
  includeChildren: number
  remark?: string
}
export function listEducationScopes(userId: string | number) {
  return get<EducationScopeItem[]>('/system/education/scopes/list', { userId })
}
export function replaceEducationScopes(userId: string | number, items: EducationScopeItem[]) {
  return post<number>('/system/education/scopes/replace', { userId, items })
}

export type PromotionAction = 'PROMOTE' | 'GRADUATE'
export interface PromotionMapping {
  sourceClassId: string | number
  targetClassId?: string | number | null
  action: PromotionAction
}
export interface PromotionBatch {
  id: string | number
  schoolId: string | number
  sourceAcademicYearId: string | number
  targetAcademicYearId: string | number
  status: 'DRAFT' | 'EXECUTING' | 'CONFIRMED' | 'PARTIAL'
  totalCount: number
  successCount: number
  failedCount: number
  confirmedAt?: string
  createTime?: string
  remark?: string
}
export function listPromotionBatches(schoolId: string | number) {
  return get<PromotionBatch[]>('/system/education/promotions/list', { schoolId })
}
export function previewPromotion(data: { schoolId: string | number; sourceAcademicYearId: string | number; targetAcademicYearId: string | number; mappings: PromotionMapping[]; remark?: string }) {
  return post<PromotionBatch>('/system/education/promotions/preview', data)
}
export function confirmPromotion(batchId: string | number) {
  return post<PromotionBatch>('/system/education/promotions/confirm', { batchId })
}

export interface EducationCourseRule {
  ruleId: string
  templateId: string
  templateName: string
  startTime: string
  endTime: string
  classSection: string
  status: string
  createName?: string
  updateName?: string
}

export interface EducationCourseRuleForm {
  id?: string
  templateName: string
  startTime: string
  endTime: string
  classSection: string
}

export function listCourseRules() {
  return get<EducationCourseRule[]>('/system/education/course-rules/list')
}
export function addCourseRule(data: EducationCourseRuleForm) {
  return post<string>('/system/education/course-rules', data)
}
export function updateCourseRule(data: EducationCourseRuleForm) {
  return post<void>('/system/education/course-rules/edit', data)
}
export function updateCourseRuleStatus(id: string, status: string) {
  return post<void>('/system/education/course-rules/status', { id, status })
}
export function removeCourseRules(ids: string[]) {
  return post<number>('/system/education/course-rules/remove', { ids })
}

/** 人员统一入口的写入结果；initialPassword 只在服务端生成初始口令时返回一次。 */
export interface PersonResult {
  personId: string | number
  userId?: string | number
  username?: string
  initialPassword?: string
}

/**
 * 「关联已有账号」模式下供确认的候选账号。
 *
 * 由窄接口按手机号精确查询，只返回一条**服务端已脱敏**的信息；
 * 前端禁止再调用 {@code /system/user/simple-list} 下载全租户账号。
 */
export interface LinkableAccount {
  userId: string | number
  nickname?: string
  /** 服务端已脱敏的手机号，前端直接展示，不再二次脱敏。 */
  phone?: string
  email?: string
}

/** 按手机号精确查询一个可关联的已有账号（服务端脱敏，只返回一条）。 */
export function getLinkableAccount(phone: string) {
  return get<LinkableAccount>('/system/education/people/linkable-account', { phone })
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

// 学期不走通用 CRUD 页：它没有 source_system 列，而且有日期区间和阶段三态要单独渲染。
export type SemesterLifecycle = 'NOT_STARTED' | 'IN_PROGRESS' | 'FINISHED'

export interface Semester {
  id?: string | number
  schoolId: string | number
  academicYearId: string | number
  /** 系统由学期名称生成；编辑时只读展示。 */
  semesterCode?: string
  semesterName: string
  beginDate: string
  endDate: string
  currentFlag: number
  status: number
  lifecycleStatus?: SemesterLifecycle
  remark?: string
}

export interface SemesterQuery extends PageQuery {
  schoolId?: string | number
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
export function removeSemesters(ids: (string | number)[]) { return post<number>('/system/education/semesters/remove', { ids }) }

export function removeEducation(entity: EducationEntity, ids: (string | number)[]) {
  return post<number>(`/system/education/${entity}/remove`, { ids })
}

export function addPerson(data: EducationRecord) {
  return post<PersonResult>('/system/education/people', data)
}

export function updatePerson(data: EducationRecord) {
  return post<PersonResult>('/system/education/people/edit', data)
}

export function resetPersonPassword(personId: string | number, password: string) {
  return postParams<void>('/system/education/people/reset-password', { personId, password })
}

export function unbindClientUser(userId: string | number) {
  return postParams<void>('/system/education/people/unbind', { userId })
}

export interface PersonImportResult {
  rowNumber: number
  personName?: string
  phone?: string
  success: boolean
  message: string
  personId?: string | number
  userId?: string | number
}

export function downloadPersonImportTemplate(schoolId: string | number) {
  return get<Blob>('/system/education/people/import-template', { schoolId }, { responseType: 'blob' })
}

export function importPeople(file: File, schoolId: string | number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('schoolId', String(schoolId))
  return post<PersonImportResult[]>('/system/education/people/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
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
