import { expect, test } from '@playwright/test'
import { resolvePageResult } from '../../../src/utils/page-result'

test.describe('page result helper', () => {
  test('supports legacy rows payload', () => {
    const result = resolvePageResult<{ id: number }>({
      rows: [{ id: 1 }, { id: 2 }],
      total: 2
    })

    expect(result.rows).toEqual([{ id: 1 }, { id: 2 }])
    expect(result.total).toBe(2)
  })

  test('supports records payload returned by new pages', () => {
    const result = resolvePageResult<{ id: number }>({
      records: [{ id: 7 }],
      total: 1
    })

    expect(result.rows).toEqual([{ id: 7 }])
    expect(result.total).toBe(1)
  })

  test('supports direct array payload from all-list endpoints', () => {
    const result = resolvePageResult<{ id: number }>([{ id: 3 }, { id: 4 }])

    expect(result.rows).toEqual([{ id: 3 }, { id: 4 }])
    expect(result.total).toBe(2)
  })
})
