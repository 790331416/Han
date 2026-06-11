import { get, post, request } from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'

export interface AivideoProject {
  projectId: string | number
  projectName: string
  ownerUserId?: string | number
  topicType?: string
  targetPlatform?: string
  defaultRatio?: string
  defaultStyle?: string
  generationStrategy?: string
  audioMode?: string
  subtitleMode?: string
  referenceStrategy?: string
  actionIntensity?: string
  continuityLevel?: string
  multiRoleStrategy?: string
  characterDesignType?: string
  globalPrompt?: string
  polishPrompt?: string
  scriptPrompt?: string
  assetPrompt?: string
  characterImagePrompt?: string
  sceneImagePrompt?: string
  shotVideoPrompt?: string
  defaultShotDuration?: number
  candidateImageCount?: number
  videoCandidateCount?: number
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
  generationStrategy?: string
  audioMode?: string
  subtitleMode?: string
  referenceStrategy?: string
  actionIntensity?: string
  continuityLevel?: string
  multiRoleStrategy?: string
  characterDesignType?: string
  globalPrompt?: string
  polishPrompt?: string
  scriptPrompt?: string
  assetPrompt?: string
  characterImagePrompt?: string
  sceneImagePrompt?: string
  shotVideoPrompt?: string
  defaultShotDuration?: number
  candidateImageCount?: number
  videoCandidateCount?: number
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
  lockedMediaId?: string | number
  voiceMode?: string
  voiceType?: string
  voiceName?: string
  voiceDesc?: string
  voiceReferenceMediaId?: string | number
  voiceSampleText?: string
  voiceSpeedRatio?: number
  voiceVolumeRatio?: number
  voicePitchRatio?: number
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
  lockedMediaId?: string | number
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
  transitionBeforeType?: string
  transitionBeforeDesc?: string
  transitionEffect?: string
  stitchGroupNo?: number
  actionDesc?: string
  dialogue?: string
  voiceOver?: string
  emotion?: string
  bgmCue?: string
  sfxCues?: string
  ttsStartMs?: number
  ttsEndMs?: number
  ttsSpeaker?: string
  ttsVoiceType?: string
  promptText?: string
  confirmStatus?: string
  generationStatus?: string
  referenceMediaIds?: string
  keyframeMediaId?: string | number
  videoMediaId?: string | number
  tailFrameMediaId?: string | number
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

export interface AivideoProjectEditClip {
  shotId: string | number
  episodeNo?: number
  shotNo?: number
  durationSec?: number
  stitchGroupNo?: number
  transitionBeforeType?: string
  transitionBeforeDesc?: string
  transitionEffect?: string
  actionDesc?: string
  bgmCue?: string
  sfxCues?: string
  videoMediaId?: string | number
  videoUrl?: string
  ttsAudioMediaId?: string | number
  ttsAudioUrl?: string
  ttsSpeaker?: string
  ttsVoiceType?: string
  ttsStartMs?: number
  ttsEndMs?: number
  ttsTimelineStartMs?: number
  ttsTimelineEndMs?: number
  sfxAudioMediaId?: string | number
  sfxAudioUrl?: string
  timelineStartMs?: number
  timelineEndMs?: number
}

export interface AivideoProjectEditPreflight {
  ready?: boolean
  clipCount?: number
  missingShotCount?: number
  totalDurationSec?: number
  bgmAudioMediaId?: string | number
  bgmAudioUrl?: string
  audioTrackCount?: number
  clips?: AivideoProjectEditClip[]
  warnings?: string[]
  errors?: string[]
}

export interface AivideoProjectEditGenerateRequest {
  projectId: string | number
  videoName?: string
  includeAudio?: boolean
  priority?: number
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
  characterImagePromptTemplateId?: string | number
  sceneImagePromptTemplateId?: string | number
  shotPromptTemplateId?: string | number
  videoPromptTemplateId?: string | number
  defaultRatio?: string
  defaultResolution?: string
  imageCandidateCount?: number
  videoCandidateCount?: number
  defaultShotDuration?: number
  previewMode?: string
  contentAuditEnabled?: string
  mediaAccessPolicy?: string
  paramsJson?: string
  defaultStyle?: string
  generationStrategy?: string
  audioMode?: string
  subtitleMode?: string
  referenceStrategy?: string
  actionIntensity?: string
  continuityLevel?: string
  multiRoleStrategy?: string
  characterDesignType?: string
  globalPrompt?: string
  polishPrompt?: string
  scriptPrompt?: string
  assetPrompt?: string
  characterImagePrompt?: string
  sceneImagePrompt?: string
  shotVideoPrompt?: string
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

export interface AivideoMediaAsset {
  mediaId: string | number
  projectId?: string | number
  assetType?: string
  bizType?: string
  bizId?: string | number
  fileId?: string | number
  fileUrl?: string
  thumbnailFileId?: string | number
  promptText?: string
  negativePrompt?: string
  modelId?: string | number
  taskId?: string | number
  paramsJson?: string
  candidateNo?: number
  selected?: string
  assetStatus?: string
  createTime?: string
}

export interface AivideoUploadedFile {
  id?: string | number
  name?: string
  url?: string
}

export const AIVIDEO_POLISH_STREAM_PATH = '/aivideo/studio/text/polish/generate/stream'
export const AIVIDEO_SCRIPT_STREAM_PATH = '/aivideo/studio/text/script/generate/stream'
export const AIVIDEO_ASSET_STREAM_PATH = '/aivideo/studio/assets/extract/stream'
export const AIVIDEO_CHARACTER_IMAGE_STREAM_PATH = '/aivideo/studio/media/character/generate/stream'
export const AIVIDEO_SCENE_IMAGE_STREAM_PATH = '/aivideo/studio/media/scene/generate/stream'
export const AIVIDEO_SHOT_VIDEO_STREAM_PATH = '/aivideo/studio/media/shot/video/generate/stream'

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

export function cancelConfirmAivideoPolish(data: {
  projectId: string | number
  versionId: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/text/polish/confirm/cancel', data)
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

export function cancelConfirmAivideoScript(data: {
  projectId: string | number
  versionId: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/text/script/confirm/cancel', data)
}

export function extractAivideoAssets(data: {
  projectId: string | number
  customPrompt?: string
}) {
  return post<AivideoAssetSummary>('/aivideo/studio/assets/extract', data)
}

export function previewAivideoAssetPrompt(data: {
  projectId: string | number
  customPrompt?: string
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/assets/prompt-preview', data, { silentError: true })
}

export function getAivideoAssets(projectId: string | number) {
  return get<AivideoAssetSummary>(`/aivideo/studio/assets/summary/${projectId}`)
}

export function getAivideoStudioTask(taskId: string | number) {
  return get<AivideoTask>(`/aivideo/studio/task/${taskId}`)
}

export function getLatestAivideoAssetTask(projectId: string | number) {
  return get<AivideoTask>('/aivideo/studio/task/assets/latest', { projectId }, { silentError: true })
}

export function confirmAivideoAsset(data: {
  projectId: string | number
  targetType: string
  targetId?: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/assets/confirm', data)
}

export function cancelConfirmAivideoAsset(data: {
  projectId: string | number
  targetType: string
  targetId?: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/assets/confirm/cancel', data)
}

export function updateAivideoShotScene(data: {
  projectId: string | number
  shotId: string | number
  sceneId: string | number
}) {
  return post<void>('/aivideo/studio/assets/shot/scene', data)
}

export function updateAivideoCharacterVoice(data: {
  projectId: string | number
  characterId: string | number
  voiceMode?: string
  voiceType?: string
  voiceName?: string
  voiceDesc?: string
  voiceReferenceMediaId?: string | number
  voiceSampleText?: string
  voiceSpeedRatio?: number
  voiceVolumeRatio?: number
  voicePitchRatio?: number
}) {
  return post<void>('/aivideo/studio/assets/character/voice', data)
}

export function previewAivideoSceneImagePrompt(data: {
  projectId: string | number
  sceneId: string | number
  candidateCount?: number
  modelId?: string | number
  ratio?: string
  resolution?: string
  size?: string
  defaultStyle?: string
  referenceImageUrl?: string
  referenceMediaIds?: Array<string | number>
  referenceImageUrls?: string[]
  customPrompt?: string
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/media/scene/prompt-preview', data, { silentError: true })
}

export function previewAivideoCharacterImagePrompt(data: {
  projectId: string | number
  characterId: string | number
  candidateCount?: number
  modelId?: string | number
  ratio?: string
  resolution?: string
  size?: string
  defaultStyle?: string
  characterDesignType?: string
  referenceImageUrl?: string
  referenceMediaIds?: Array<string | number>
  referenceImageUrls?: string[]
  customPrompt?: string
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/media/character/prompt-preview', data, { silentError: true })
}

export function previewAivideoShotVideoPrompt(data: {
  projectId: string | number
  shotId: string | number
  candidateCount?: number
  modelId?: string | number
  ratio?: string
  resolution?: string
  durationSec?: number
  customPrompt?: string
  defaultStyle?: string
  generationStrategy?: string
  audioMode?: string
  subtitleMode?: string
  referenceStrategy?: string
  actionIntensity?: string
  continuityLevel?: string
  multiRoleStrategy?: string
  characterDesignType?: string
  referenceMediaIds?: Array<string | number>
}) {
  return post<AivideoPromptPreview>('/aivideo/studio/media/shot/video/prompt-preview', data, { silentError: true })
}

export function listAivideoShotVideoTasks(query: {
  projectId: string | number
  shotId: string | number
}) {
  return get<AivideoTask[]>('/aivideo/studio/media/shot/video/tasks', query)
}

export function listAivideoMedia(query: {
  projectId: string | number
  assetType?: string
  bizType?: string
  bizId?: string | number
}) {
  return get<AivideoMediaAsset[]>('/aivideo/studio/media/list', query)
}

export function generateAivideoShotTtsAudio(data: {
  projectId: string | number
  shotId: string | number
  text?: string
  voiceType?: string
  speaker?: string
  ttsStartMs?: number
  ttsEndMs?: number
  speedRatio?: number
  volumeRatio?: number
  pitchRatio?: number
}) {
  return post<AivideoMediaAsset>('/aivideo/studio/media/shot/tts/generate', data)
}

export function getAivideoProjectEditPreflight(projectId: string | number) {
  return get<AivideoProjectEditPreflight>('/aivideo/studio/edit/preflight', { projectId }, { silentError: true })
}

export function generateAivideoProjectEdit(data: AivideoProjectEditGenerateRequest) {
  return post<AivideoTask>('/aivideo/studio/edit/generate', data)
}

export function pollAivideoProjectEditTask(projectId: string | number, taskId: string | number) {
  return get<AivideoTask>(`/aivideo/studio/edit/task/${taskId}/poll`, { projectId }, { silentError: true })
}

export function listAivideoProjectEditTasks(projectId: string | number) {
  return get<AivideoTask[]>('/aivideo/studio/edit/tasks', { projectId }, { silentError: true })
}

export function previewAivideoMedia(mediaId: string | number) {
  return request<Blob>({
    url: `/aivideo/studio/media/${mediaId}/preview`,
    method: 'GET',
    responseType: 'blob',
    silentError: true
  })
}

export function getPublicAivideoMediaPreviewUrl(mediaId: string | number) {
  return `/aivideo/public/media/${mediaId}/preview`
}

export function uploadAivideoReferenceImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<AivideoUploadedFile>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    silentError: true
  })
}

export function selectAivideoMedia(data: {
  projectId: string | number
  mediaId: string | number
  bizType?: string
  bizId?: string | number
  comment?: string
}) {
  return post<void>('/aivideo/studio/media/select', data)
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

export const visualStyleOptions = [
  { label: '写实电影感', value: '写实电影感' },
  { label: '3D 国漫 CG', value: '3D 国漫 CG' },
  { label: '2D 日漫', value: '2D 日漫' },
  { label: '复古胶片', value: '复古胶片' },
  { label: '赛博朋克', value: '赛博朋克' },
  { label: '童话绘本', value: '童话绘本' },
  { label: '国风水墨', value: '国风水墨' }
]

export const generationStrategyOptions = [
  { label: '自动', value: 'AUTO' },
  { label: '视频延长', value: 'VIDEO_EXTEND' },
  { label: '分段拼接', value: 'SEGMENT_STITCH' },
  { label: '轨道补齐', value: 'TRACK_FILL' }
]

export const audioModeOptions = [
  { label: '静音', value: 'SILENT' },
  { label: '原生有声', value: 'NATIVE_AUDIO' },
  { label: '参考音频有声', value: 'REFERENCE_AUDIO' },
  { label: '后期 TTS', value: 'POST_TTS' }
]

export const subtitleModeOptions = [
  { label: '无字幕', value: 'NONE' },
  { label: '底部字幕', value: 'BOTTOM' },
  { label: '气泡台词', value: 'BUBBLE' },
  { label: '标题文字', value: 'TITLE' }
]

export const referenceStrategyOptions = [
  { label: '角色锚定', value: 'CHARACTER_ANCHOR' },
  { label: '场景定调', value: 'SCENE_TONE' },
  { label: '运镜参考', value: 'CAMERA_REFERENCE' },
  { label: '动作参考', value: 'ACTION_REFERENCE' },
  { label: '音频参考', value: 'AUDIO_REFERENCE' },
  { label: '角色 + 场景', value: 'CHARACTER_SCENE' }
]

export const actionIntensityOptions = [
  { label: '低缓动作', value: 'LOW' },
  { label: '普通动作', value: 'NORMAL' },
  { label: '强动作', value: 'STRONG' }
]

export const continuityLevelOptions = [
  { label: '普通', value: 'NORMAL' },
  { label: '严格', value: 'STRICT' },
  { label: '极严格', value: 'ULTRA_STRICT' }
]

export const multiRoleStrategyOptions = [
  { label: '单角色优先', value: 'SINGLE_FIRST' },
  { label: '多角色允许', value: 'MULTI_ALLOWED' },
  { label: '超过 4 人自动拆镜', value: 'SPLIT_OVER_FOUR' }
]

export const characterDesignTypeOptions = [
  { label: '自动', value: 'AUTO' },
  { label: '写实自然比例', value: 'REALISTIC_NATURAL' },
  { label: '半写实卡通', value: 'SEMI_REAL_CARTOON' },
  { label: '3D动漫/国漫CG', value: 'THREE_D_ANIME_CG' },
  { label: '2D动漫/日漫', value: 'TWO_D_ANIME' },
  { label: 'Q版萌系全身', value: 'CHIBI_FULL_BODY' },
  { label: '低龄儿童绘本', value: 'CHILDREN_PICTURE_BOOK' },
  { label: '动物本体萌化', value: 'ANIMAL_BODY_CUTE' },
  { label: '拟人化角色', value: 'ANTHROPOMORPHIC' },
  { label: '怪物/夸张反派', value: 'MONSTER_VILLAIN' }
]
