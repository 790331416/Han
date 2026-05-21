import { get, post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export interface AivideoProject {
  projectId: string | number
  projectName: string
  ownerUserId?: string | number
  topicType?: string
  targetPlatform?: string
  defaultRatio?: string
  defaultStyle?: string
  defaultShotDuration?: number
  candidateImageCount?: number
  previewMode?: string
  currentStage?: string
  projectStatus?: string
  budgetLimit?: number
  estimatedCost?: number
  actualCost?: number
  summary?: string
  createTime?: string
  updateTime?: string
  remark?: string
}

export interface AivideoProjectQuery extends PageQuery {
  projectName?: string
  projectStatus?: string
  currentStage?: string
}

export interface AivideoProjectForm {
  projectId?: string | number
  projectName: string
  topicType?: string
  targetPlatform?: string
  defaultRatio?: string
  defaultStyle?: string
  defaultShotDuration?: number
  candidateImageCount?: number
  previewMode?: string
  budgetLimit?: number
  summary?: string
  sourceType?: string
  fileId?: string | number
  fileName?: string
  rawText?: string
}

export interface AivideoSourceDocument {
  documentId: string | number
  projectId: string | number
  sourceType?: string
  fileId?: string | number
  fileName?: string
  rawText?: string
  parsedText?: string
  charCount?: number
  parseStatus?: string
  confirmed?: string
  createTime?: string
}

export interface AivideoTask {
  taskId: string | number
  projectId?: string | number
  tenantId?: string | number
  taskType?: string
  bizType?: string
  bizId?: string | number
  modelId?: string | number
  promptTemplateId?: string | number
  promptText?: string
  customPrompt?: string
  paramsJson?: string
  providerTaskId?: string
  jobId?: string | number
  taskStatus?: string
  progress?: number
  estimatedCost?: number
  actualCost?: number
  errorCode?: string
  errorMessage?: string
  startedTime?: string
  finishedTime?: string
  createTime?: string
  updateTime?: string
}

export interface AivideoTaskQuery extends PageQuery {
  projectId?: string | number
  tenantId?: string | number
  taskType?: string
  taskStatus?: string
}

export interface AivideoSetting {
  textModelId?: string | number
  imageModelId?: string | number
  videoModelId?: string | number
  defaultRatio?: string
  defaultResolution?: string
  imageCandidateCount?: number
  videoCandidateCount?: number
  defaultShotDuration?: number
  previewMode?: string
  contentAuditEnabled?: string
  remark?: string
}

export interface AivideoProjectDetail {
  project?: AivideoProject
  setting?: AivideoSetting
  documents?: AivideoSourceDocument[]
  latestTask?: AivideoTask
}

export function listAivideoProject(query: AivideoProjectQuery) {
  return get<PageResult<AivideoProject>>('/aivideo/studio/project/list', query)
}

export function getAivideoProject(projectId: string | number) {
  return get<AivideoProjectDetail>(`/aivideo/studio/project/${projectId}`)
}

export function addAivideoProject(data: AivideoProjectForm) {
  return post<string | number>('/aivideo/studio/project', data)
}

export function updateAivideoProject(data: AivideoProjectForm) {
  return post<void>('/aivideo/studio/project/edit', data)
}

export function saveAivideoDocument(data: Partial<AivideoSourceDocument>) {
  return post<string | number>('/aivideo/studio/document/save', data)
}

export function listAivideoTask(query: AivideoTaskQuery) {
  return get<PageResult<AivideoTask>>('/aivideo/admin/task/list', query)
}

export function getAivideoTask(taskId: string | number) {
  return get<AivideoTask>(`/aivideo/admin/task/${taskId}`)
}

export function getAivideoSetting() {
  return get<AivideoSetting>('/aivideo/admin/setting')
}

export function updateAivideoSetting(data: AivideoSetting) {
  return post<void>('/aivideo/admin/setting/edit', data)
}

export const aivideoProjectStageOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '原文已保存', value: 'DOCUMENT_SAVED' },
  { label: '文档已解析', value: 'DOCUMENT_PARSED' },
  { label: '润色已确认', value: 'POLISH_CONFIRMED' },
  { label: '剧本已确认', value: 'SCRIPT_CONFIRMED' },
  { label: '资产已确认', value: 'ASSET_CONFIRMED' },
  { label: '视频生成中', value: 'VIDEO_GENERATING' },
  { label: '视频已确认', value: 'VIDEO_CONFIRMED' }
]

export const aivideoProjectStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '进行中', value: 'RUNNING' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已归档', value: 'ARCHIVED' }
]

export const aivideoTaskStatusOptions = [
  { label: '待执行', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' }
]

export const ratioOptions = [
  { label: '9:16', value: '9:16' },
  { label: '16:9', value: '16:9' },
  { label: '1:1', value: '1:1' },
  { label: '4:3', value: '4:3' }
]

export const resolutionOptions = [
  { label: '720p', value: '720p' },
  { label: '1080p', value: '1080p' },
  { label: '2K', value: '2K' }
]
