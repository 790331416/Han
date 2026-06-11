import { expect, test } from '@playwright/test'
import { normalizeDictOptions } from '../../../src/utils/dict-options'

test.describe('dict option helpers', () => {
  test('normalizes enabled dict rows by sort order', () => {
    const options = normalizeDictOptions(
      [
        { id: 2, dictType: 'ai_model_type', dictLabel: '图片生成模型', dictValue: 'IMAGE', dictSort: 20, status: 0 },
        { id: 1, dictType: 'ai_model_type', dictLabel: '大语言模型', dictValue: 'LLM', dictSort: 10, status: 0 },
        { id: 3, dictType: 'ai_model_type', dictLabel: '停用类型', dictValue: 'DISABLED', dictSort: 5, status: 1 }
      ],
      [{ label: '兜底', value: 'FALLBACK' }]
    )

    expect(options).toEqual([
      { label: '大语言模型', value: 'LLM' },
      { label: '图片生成模型', value: 'IMAGE' }
    ])
  })

  test('uses fallback options when dict rows are empty', () => {
    const options = normalizeDictOptions([], [{ label: '大语言模型', value: 'LLM' }])

    expect(options).toEqual([{ label: '大语言模型', value: 'LLM' }])
  })
})
