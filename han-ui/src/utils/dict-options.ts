/**
 * 通用字典下拉项。
 */
export interface DictOption {
  label: string
  value: string
}

/**
 * 字典接口返回的轻量结构。
 */
interface DictLike {
  dictSort?: number
  dictLabel?: string
  dictValue?: string
  status?: number | string
}

/**
 * 系统通用启停状态。
 */
export const SYS_NORMAL_DISABLE_DICT = 'sys_normal_disable'
export const OPEN_IDENTITY_SCOPE_DICT = 'open_identity_scope'

/**
 * AI 模型与模板相关字典。
 */
export const AI_MODEL_TYPE_DICT = 'ai_model_type'
export const AI_MODEL_PROVIDER_DICT = 'ai_model_provider'
export const AI_PROMPT_CATEGORY_DICT = 'ai_prompt_category'
export const AI_KB_TYPE_DICT = 'ai_kb_type'
export const AI_MCP_TRANSPORT_TYPE_DICT = 'ai_mcp_transport_type'
export const AI_WORKFLOW_TYPE_DICT = 'ai_workflow_type'
export const AI_KNOWLEDGE_INDEX_STATUS_DICT = 'ai_knowledge_index_status'

/**
 * AIVideo 公共策略字典。
 */
export const AIVIDEO_PROJECT_STAGE_DICT = 'aivideo_project_stage'
export const AIVIDEO_PROJECT_STATUS_DICT = 'aivideo_project_status'
export const AIVIDEO_TASK_STATUS_DICT = 'aivideo_task_status'
export const AIVIDEO_RATIO_DICT = 'aivideo_ratio'
export const AIVIDEO_RESOLUTION_DICT = 'aivideo_resolution'
export const AIVIDEO_VISUAL_STYLE_DICT = 'aivideo_visual_style'
export const AIVIDEO_GENERATION_STRATEGY_DICT = 'aivideo_generation_strategy'
export const AIVIDEO_AUDIO_MODE_DICT = 'aivideo_audio_mode'
export const AIVIDEO_SUBTITLE_MODE_DICT = 'aivideo_subtitle_mode'
export const AIVIDEO_REFERENCE_STRATEGY_DICT = 'aivideo_reference_strategy'
export const AIVIDEO_ACTION_INTENSITY_DICT = 'aivideo_action_intensity'
export const AIVIDEO_CONTINUITY_LEVEL_DICT = 'aivideo_continuity_level'
export const AIVIDEO_MULTI_ROLE_STRATEGY_DICT = 'aivideo_multi_role_strategy'
export const AIVIDEO_CHARACTER_DESIGN_TYPE_DICT = 'aivideo_character_design_type'
export const AIVIDEO_MEDIA_ACCESS_POLICY_DICT = 'aivideo_media_access_policy'

/**
 * 一组字典加载任务定义。
 */
export interface DictOptionRequest {
  dictType: string
  fallback?: DictOption[]
}

/**
 * 判断字典值是否启用。
 */
function isEnabledDict(row: DictLike) {
  return row.status === undefined || row.status === 0 || String(row.status) === '0'
}

/**
 * 把字典接口结果标准化为页面下拉选项。
 *
 * <p>当接口没有返回有效数据时，回退到调用方传入的兜底选项，
 * 这样可以保证数据库未初始化时页面也不会直接失效。
 */
export function normalizeDictOptions(rows: DictLike[] = [], fallback: DictOption[] = []): DictOption[] {
  const options = rows
    .filter((row) => isEnabledDict(row) && row.dictLabel && row.dictValue)
    .sort((left, right) => Number(left.dictSort || 0) - Number(right.dictSort || 0))
    .map((row) => ({
      label: String(row.dictLabel),
      value: String(row.dictValue)
    }))

  if (options.length === 0) {
    return [...fallback]
  }

  /**
   * 字典表存在历史重复数据时，统一按 dictValue 去重，保留排序后的第一条，
   * 避免所有业务下拉框都出现成对重复选项。
   */
  const uniqueOptions = new Map<string, DictOption>()
  for (const option of options) {
    if (!uniqueOptions.has(option.value)) {
      uniqueOptions.set(option.value, option)
    }
  }

  return Array.from(uniqueOptions.values())
}

/**
 * 根据字典值查找用于展示的中文标签。
 *
 * <p>公共页面统一通过这个方法做展示，避免各页面重复维护
 * `find(...)?label` 一类的小逻辑，也便于以后切换缓存策略。
 */
export function findDictLabel(options: DictOption[] = [], value?: string | number, emptyText = ''): string {
  const normalizedValue = value === undefined || value === null ? '' : String(value)
  return options.find((item) => item.value === normalizedValue)?.label || normalizedValue || emptyText
}

/**
 * 加载单个字典类型的选项。
 */
export async function loadDictOptions(dictType: string, fallback: DictOption[] = []): Promise<DictOption[]> {
  try {
    const { listDictDataByType } = await import('@/api/system/dict')
    const res = await listDictDataByType(dictType)
    return normalizeDictOptions(res.data || [], fallback)
  } catch (error) {
    console.error(`加载字典 ${dictType} 失败，已使用兜底选项:`, error)
    return [...fallback]
  }
}

/**
 * 批量加载多个字典类型。
 *
 * <p>适合一个页面同时依赖多个下拉字典的场景，避免每个页面重复写
 * Promise.all 和对象映射逻辑。
 */
export async function loadDictOptionSet<T extends Record<string, DictOptionRequest>>(
  requests: T
): Promise<{ [K in keyof T]: DictOption[] }> {
  const entries = await Promise.all(
    Object.entries(requests).map(async ([key, request]) => {
      const options = await loadDictOptions(request.dictType, request.fallback || [])
      return [key, options] as const
    })
  )

  return Object.fromEntries(entries) as { [K in keyof T]: DictOption[] }
}
