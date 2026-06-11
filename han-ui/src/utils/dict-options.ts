export interface DictOption {
  label: string
  value: string
}

interface DictLike {
  dictSort?: number
  dictLabel?: string
  dictValue?: string
  status?: number | string
}

export const AI_MODEL_TYPE_DICT = 'ai_model_type'
export const AI_MODEL_PROVIDER_DICT = 'ai_model_provider'
export const AI_PROMPT_CATEGORY_DICT = 'ai_prompt_category'

function isEnabledDict(row: DictLike) {
  return row.status === undefined || row.status === 0 || String(row.status) === '0'
}

export function normalizeDictOptions(rows: DictLike[] = [], fallback: DictOption[] = []): DictOption[] {
  const options = rows
    .filter((row) => isEnabledDict(row) && row.dictLabel && row.dictValue)
    .sort((left, right) => Number(left.dictSort || 0) - Number(right.dictSort || 0))
    .map((row) => ({
      label: String(row.dictLabel),
      value: String(row.dictValue)
    }))

  return options.length > 0 ? options : [...fallback]
}

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
