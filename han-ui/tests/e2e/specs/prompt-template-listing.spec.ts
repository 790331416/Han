import { expect, test } from '@playwright/test'
import {
  paginatePromptTemplates,
  shouldFallbackToAllPromptTemplates
} from '../../../src/views/ai/prompt/listing'

test.describe('prompt template listing helpers', () => {
  test('falls back to all endpoint only for first page without filters', () => {
    expect(
      shouldFallbackToAllPromptTemplates(
        { templateName: '', category: '', status: '', pageNum: 1, pageSize: 10 },
        []
      )
    ).toBe(true)

    expect(
      shouldFallbackToAllPromptTemplates(
        { templateName: '分镜', category: '', status: '', pageNum: 1, pageSize: 10 },
        []
      )
    ).toBe(false)

    expect(
      shouldFallbackToAllPromptTemplates(
        { templateName: '', category: '', status: '', pageNum: 2, pageSize: 10 },
        []
      )
    ).toBe(false)
  })

  test('keeps current list when current page already has data', () => {
    expect(
      shouldFallbackToAllPromptTemplates(
        { templateName: '', category: '', status: '', pageNum: 1, pageSize: 10 },
        [{ templateId: 1 }]
      )
    ).toBe(false)
  })

  test('paginates all-template payload with project page settings', () => {
    const result = paginatePromptTemplates(
      [{ templateId: 1 }, { templateId: 2 }, { templateId: 3 }],
      { pageNum: 2, pageSize: 2 }
    )

    expect(result.rows).toEqual([{ templateId: 3 }])
    expect(result.total).toBe(3)
  })
})
