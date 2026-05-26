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
  chapterJson?: string
  charCount?: number
  parseStatus?: string
  confirmed?: string
  createTime?: string
}

export interface AivideoContentVersion {
  versionId: string | number
  projectId?: string | number
  documentId?: string | number
  contentType?: string
  versionNo?: number
  title?: string
  contentText?: string
  contentJson?: string
  promptTemplateId?: string | number
  customPrompt?: string
  modelId?: string | number
  taskId?: string | number
  selected?: string
  confirmStatus?: string
  createTime?: string
}

export interface AivideoCharacter {
  characterId: string | number
  characterName?: string
  gender?: string
  ageDesc?: string
  identityDesc?: string
  personalityTags?: string
  storyRole?: string
  appearance?: string
  promptText?: string
  confirmStatus?: string
}

export interface AivideoScene {
  sceneId: string | number
  sceneName?: string
  sceneType?: string
  episodeNo?: number
  timeDesc?: string
  weather?: string
  atmosphere?: string
  visualFeatures?: string
  promptText?: string
  confirmStatus?: string
}

export interface AivideoShot {
  shotId: string | number
  episodeNo?: number
  shotNo?: number
  durationSec?: number
  sceneId?: string | number
  characterIds?: string
  shotType?: string
  cameraPosition?: string
  cameraMovement?: string
  actionDesc?: string
  dialogue?: string
  voiceOver?: string
  emotion?: string
  promptText?: string
  confirmStatus?: string
  generationStatus?: string
}

export interface AivideoAssetSummary {
  characters?: AivideoCharacter[]
  scenes?: AivideoScene[]
  shots?: AivideoShot[]
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
  polishPromptTemplateId?: string | number
  scriptPromptTemplateId?: string | number
  characterPromptTemplateId?: string | number
  scenePromptTemplateId?: string | number
  shotPromptTemplateId?: string | number
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
  contentVersions?: AivideoContentVersion[]
  characters?: AivideoCharacter[]
  scenes?: AivideoScene[]
  shots?: AivideoShot[]
  latestTask?: AivideoTask
}

export interface AivideoPromptPreview {
  promptTemplateId?: string | number
  systemPrompt?: string
  userPrompt?: string
  customPrompt?: string
  effectivePrompt?: string
}

export const AIVIDEO_POLISH_STREAM_PATH = '/aivideo/studio/text/polish/generate/stream'
export const AIVIDEO_SCRIPT_STREAM_PATH = '/aivideo/studio/text/script/generate/stream'

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

export function confirmAivideoDocument(data: {
  projectId: string | number
  documentId?: string | number
  parsedText?: string
  chapterJson?: string
  comment?: string
}) {
  return post<void>('/aivideo/studio/document/confirm', data)
}

export function generateAivideoPolish(data: {
  projectId: string | number
  documentId?: string | number
  customPrompt?: string
}) {
  return post<AivideoContentVersion>('/aivideo/studio/text/polish/generate', data)
}

export function previewAivideoPolishPrompt(data: {
  projectId: string | number
  documentId?: string | number
  customPrompt?: string
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/text/polish/prompt-preview', data, { silentError: true })
}

export function confirmAivideoPolish(data: {
  projectId: string | number
  versionId: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/text/polish/confirm', data)
}

export function generateAivideoScript(data: {
  projectId: string | number
  customPrompt?: string
}) {
  return post<AivideoContentVersion>('/aivideo/studio/text/script/generate', data)
}

export function previewAivideoScriptPrompt(data: {
  projectId: string | number
  customPrompt?: string
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/text/script/prompt-preview', data, { silentError: true })
}

export function confirmAivideoScript(data: {
  projectId: string | number
  versionId: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/text/script/confirm', data)
}

export function extractAivideoAssets(data: {
  projectId: string | number
  customPrompt?: string
}) {
  return post<AivideoAssetSummary>('/aivideo/studio/assets/extract', data)
}

export function getAivideoAssets(projectId: string | number) {
  return get<AivideoAssetSummary>(`/aivideo/studio/assets/summary/${projectId}`)
}

export function confirmAivideoAsset(data: {
  projectId: string | number
  targetType: string
  targetId?: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/assets/confirm', data)
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
  { label: '文档已确认', value: 'DOCUMENT_PARSED' },
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
