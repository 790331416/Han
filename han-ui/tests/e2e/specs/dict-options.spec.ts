import { expect, test } from '@playwright/test'
import { findDictLabel, normalizeDictOptions } from '../../../src/utils/dict-options'

test.describe('dict option helpers', () => {
  test('normalizes enabled dict rows by sort order', () => {
    const options = normalizeDictOptions(
      [
        { dictLabel: '图片生成模型', dictValue: 'IMAGE', dictSort: 20, status: 0 },
        { dictLabel: '大语言模型', dictValue: 'LLM', dictSort: 10, status: 0 },
        { dictLabel: '停用类型', dictValue: 'DISABLED', dictSort: 5, status: 1 }
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

  test('uses fallback options when every dict row is disabled', () => {
    const options = normalizeDictOptions(
      [{ dictLabel: '停用类型', dictValue: 'DISABLED', dictSort: 5, status: '1' }],
      [{ label: '兜底', value: 'FALLBACK' }]
    )

    expect(options).toEqual([{ label: '兜底', value: 'FALLBACK' }])
  })

  test('deduplicates legacy duplicated dict values by keeping the first after sorting', () => {
    const options = normalizeDictOptions([
      { dictLabel: '大语言模型', dictValue: 'LLM', dictSort: 10, status: 0 },
      { dictLabel: '大语言模型（历史重复）', dictValue: 'LLM', dictSort: 20, status: 0 },
      { dictLabel: '图片生成模型', dictValue: 'IMAGE', dictSort: 30, status: 0 }
    ])

    expect(options).toEqual([
      { label: '大语言模型', value: 'LLM' },
      { label: '图片生成模型', value: 'IMAGE' }
    ])
  })

  test('finds label when backend status value is numeric', () => {
    const label = findDictLabel(
      [
        { label: '正常', value: '0' },
        { label: '停用', value: '1' }
      ],
      0
    )

    expect(label).toBe('正常')
  })

  test('falls back to the raw value, then to the empty text, when no option matches', () => {
    const options = [{ label: '正常', value: '0' }]

    expect(findDictLabel(options, '9')).toBe('9')
    expect(findDictLabel(options, undefined, '未配置')).toBe('未配置')
    expect(findDictLabel(options, '', '未配置')).toBe('未配置')
  })
})
