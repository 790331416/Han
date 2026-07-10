import { ref, type Ref } from 'vue'
import {
  actionIntensityOptions as fallbackActionIntensityOptions,
  audioModeOptions as fallbackAudioModeOptions,
  aivideoProjectStageOptions as fallbackProjectStageOptions,
  aivideoProjectStatusOptions as fallbackProjectStatusOptions,
  aivideoTaskStatusOptions as fallbackTaskStatusOptions,
  characterDesignTypeOptions as fallbackCharacterDesignTypeOptions,
  continuityLevelOptions as fallbackContinuityLevelOptions,
  generationStrategyOptions as fallbackGenerationStrategyOptions,
  mediaAccessPolicyOptions as fallbackMediaAccessPolicyOptions,
  multiRoleStrategyOptions as fallbackMultiRoleStrategyOptions,
  ratioOptions as fallbackRatioOptions,
  referenceStrategyOptions as fallbackReferenceStrategyOptions,
  resolutionOptions as fallbackResolutionOptions,
  subtitleModeOptions as fallbackSubtitleModeOptions,
  visualStyleOptions as fallbackVisualStyleOptions
} from '@/api/aivideo'
import {
  AIVIDEO_ACTION_INTENSITY_DICT,
  AIVIDEO_AUDIO_MODE_DICT,
  AIVIDEO_CHARACTER_DESIGN_TYPE_DICT,
  AIVIDEO_CONTINUITY_LEVEL_DICT,
  AIVIDEO_GENERATION_STRATEGY_DICT,
  AIVIDEO_MEDIA_ACCESS_POLICY_DICT,
  AIVIDEO_MULTI_ROLE_STRATEGY_DICT,
  AIVIDEO_PROJECT_STAGE_DICT,
  AIVIDEO_PROJECT_STATUS_DICT,
  AIVIDEO_RATIO_DICT,
  AIVIDEO_REFERENCE_STRATEGY_DICT,
  AIVIDEO_RESOLUTION_DICT,
  AIVIDEO_SUBTITLE_MODE_DICT,
  AIVIDEO_TASK_STATUS_DICT,
  AIVIDEO_VISUAL_STYLE_DICT,
  findDictLabel,
  loadDictOptionSet,
  type DictOption
} from '@/utils/dict-options'

/**
 * AIVideo 领域页面共用的字典状态。
 *
 * <p>把“项目状态、任务状态、策略枚举、角色造型、素材访问策略”等
 * 页面级下拉统一收敛到这里，避免每个页面都重复维护一套加载逻辑。
 */
export interface AivideoDictOptionState {
  projectStageOptions: Ref<DictOption[]>
  projectStatusOptions: Ref<DictOption[]>
  taskStatusOptions: Ref<DictOption[]>
  ratioOptions: Ref<DictOption[]>
  resolutionOptions: Ref<DictOption[]>
  visualStyleOptions: Ref<DictOption[]>
  generationStrategyOptions: Ref<DictOption[]>
  audioModeOptions: Ref<DictOption[]>
  subtitleModeOptions: Ref<DictOption[]>
  referenceStrategyOptions: Ref<DictOption[]>
  actionIntensityOptions: Ref<DictOption[]>
  continuityLevelOptions: Ref<DictOption[]>
  multiRoleStrategyOptions: Ref<DictOption[]>
  characterDesignTypeOptions: Ref<DictOption[]>
  mediaAccessPolicyOptions: Ref<DictOption[]>
  loadStatusOptions: () => Promise<void>
  loadStrategyOptions: () => Promise<void>
  loadSettingOptions: () => Promise<void>
  loadAllOptions: () => Promise<void>
  labelOf: (options: Ref<DictOption[]>, value?: string, emptyText?: string) => string
}

/**
 * 创建 AIVideo 页面可复用的字典状态。
 */
export function createAivideoDictOptionState(): AivideoDictOptionState {
  const projectStageOptions = ref<DictOption[]>([...fallbackProjectStageOptions])
  const projectStatusOptions = ref<DictOption[]>([...fallbackProjectStatusOptions])
  const taskStatusOptions = ref<DictOption[]>([...fallbackTaskStatusOptions])
  const ratioOptions = ref<DictOption[]>([...fallbackRatioOptions])
  const resolutionOptions = ref<DictOption[]>([...fallbackResolutionOptions])
  const visualStyleOptions = ref<DictOption[]>([...fallbackVisualStyleOptions])
  const generationStrategyOptions = ref<DictOption[]>([...fallbackGenerationStrategyOptions])
  const audioModeOptions = ref<DictOption[]>([...fallbackAudioModeOptions])
  const subtitleModeOptions = ref<DictOption[]>([...fallbackSubtitleModeOptions])
  const referenceStrategyOptions = ref<DictOption[]>([...fallbackReferenceStrategyOptions])
  const actionIntensityOptions = ref<DictOption[]>([...fallbackActionIntensityOptions])
  const continuityLevelOptions = ref<DictOption[]>([...fallbackContinuityLevelOptions])
  const multiRoleStrategyOptions = ref<DictOption[]>([...fallbackMultiRoleStrategyOptions])
  const characterDesignTypeOptions = ref<DictOption[]>([...fallbackCharacterDesignTypeOptions])
  const mediaAccessPolicyOptions = ref<DictOption[]>([...fallbackMediaAccessPolicyOptions])

  /**
   * 加载项目状态、项目阶段、任务状态等通用状态字典。
   */
  async function loadStatusOptions() {
    const options = await loadDictOptionSet({
      projectStageOptions: { dictType: AIVIDEO_PROJECT_STAGE_DICT, fallback: fallbackProjectStageOptions },
      projectStatusOptions: { dictType: AIVIDEO_PROJECT_STATUS_DICT, fallback: fallbackProjectStatusOptions },
      taskStatusOptions: { dictType: AIVIDEO_TASK_STATUS_DICT, fallback: fallbackTaskStatusOptions }
    })
    projectStageOptions.value = options.projectStageOptions
    projectStatusOptions.value = options.projectStatusOptions
    taskStatusOptions.value = options.taskStatusOptions
  }

  /**
   * 加载 AIVideo 生成策略相关字典。
   */
  async function loadStrategyOptions() {
    const options = await loadDictOptionSet({
      ratioOptions: { dictType: AIVIDEO_RATIO_DICT, fallback: fallbackRatioOptions },
      visualStyleOptions: { dictType: AIVIDEO_VISUAL_STYLE_DICT, fallback: fallbackVisualStyleOptions },
      generationStrategyOptions: { dictType: AIVIDEO_GENERATION_STRATEGY_DICT, fallback: fallbackGenerationStrategyOptions },
      audioModeOptions: { dictType: AIVIDEO_AUDIO_MODE_DICT, fallback: fallbackAudioModeOptions },
      subtitleModeOptions: { dictType: AIVIDEO_SUBTITLE_MODE_DICT, fallback: fallbackSubtitleModeOptions },
      referenceStrategyOptions: { dictType: AIVIDEO_REFERENCE_STRATEGY_DICT, fallback: fallbackReferenceStrategyOptions },
      actionIntensityOptions: { dictType: AIVIDEO_ACTION_INTENSITY_DICT, fallback: fallbackActionIntensityOptions },
      continuityLevelOptions: { dictType: AIVIDEO_CONTINUITY_LEVEL_DICT, fallback: fallbackContinuityLevelOptions },
      multiRoleStrategyOptions: { dictType: AIVIDEO_MULTI_ROLE_STRATEGY_DICT, fallback: fallbackMultiRoleStrategyOptions },
      characterDesignTypeOptions: { dictType: AIVIDEO_CHARACTER_DESIGN_TYPE_DICT, fallback: fallbackCharacterDesignTypeOptions }
    })
    ratioOptions.value = options.ratioOptions
    visualStyleOptions.value = options.visualStyleOptions
    generationStrategyOptions.value = options.generationStrategyOptions
    audioModeOptions.value = options.audioModeOptions
    subtitleModeOptions.value = options.subtitleModeOptions
    referenceStrategyOptions.value = options.referenceStrategyOptions
    actionIntensityOptions.value = options.actionIntensityOptions
    continuityLevelOptions.value = options.continuityLevelOptions
    multiRoleStrategyOptions.value = options.multiRoleStrategyOptions
    characterDesignTypeOptions.value = options.characterDesignTypeOptions
  }

  /**
   * 加载设置页所需的全部字典，包括清晰度和素材访问策略。
   */
  async function loadSettingOptions() {
    await loadStrategyOptions()
    const options = await loadDictOptionSet({
      resolutionOptions: { dictType: AIVIDEO_RESOLUTION_DICT, fallback: fallbackResolutionOptions },
      mediaAccessPolicyOptions: { dictType: AIVIDEO_MEDIA_ACCESS_POLICY_DICT, fallback: fallbackMediaAccessPolicyOptions }
    })
    resolutionOptions.value = options.resolutionOptions
    mediaAccessPolicyOptions.value = options.mediaAccessPolicyOptions
  }

  /**
   * 一次性加载工作台等复杂页面需要的全部 AIVideo 字典。
   */
  async function loadAllOptions() {
    await Promise.all([loadStatusOptions(), loadSettingOptions()])
  }

  /**
   * 统一做字典标签展示，避免页面重复写查找逻辑。
   */
  function labelOf(options: Ref<DictOption[]>, value?: string, emptyText = '') {
    return findDictLabel(options.value, value, emptyText)
  }

  return {
    projectStageOptions,
    projectStatusOptions,
    taskStatusOptions,
    ratioOptions,
    resolutionOptions,
    visualStyleOptions,
    generationStrategyOptions,
    audioModeOptions,
    subtitleModeOptions,
    referenceStrategyOptions,
    actionIntensityOptions,
    continuityLevelOptions,
    multiRoleStrategyOptions,
    characterDesignTypeOptions,
    mediaAccessPolicyOptions,
    loadStatusOptions,
    loadStrategyOptions,
    loadSettingOptions,
    loadAllOptions,
    labelOf
  }
}
