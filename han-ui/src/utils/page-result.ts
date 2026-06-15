/**
 * 统一分页数据的标准返回结构。
 *
 * <p>Han 现网同时存在 `rows`、`records` 以及“直接返回数组”三种列表载荷，
 * 这里做一次公共收口，避免每个页面都手写一遍兼容分支。 */
export interface NormalizedPageResult<T> {
  rows: T[]
  total: number
}

/**
 * 把接口返回的分页或列表载荷统一转换为 `rows + total` 结构。
 *
 * <p>兼容范围：
 * 1. `{ rows, total }`
 * 2. `{ records, total }`
 * 3. 直接返回数组 `[]`
 * 4. 空值或异常结构时返回空数组
 */
export function resolvePageResult<T>(payload: unknown): NormalizedPageResult<T> {
  if (Array.isArray(payload)) {
    return {
      rows: payload as T[],
      total: payload.length
    }
  }

  const pageLike = (payload || {}) as {
    rows?: T[]
    records?: T[]
    list?: T[]
    items?: T[]
    total?: number
  }

  const rows = pageLike.rows || pageLike.records || pageLike.list || pageLike.items || []
  const safeRows = Array.isArray(rows) ? rows : []

  return {
    rows: safeRows,
    total: typeof pageLike.total === 'number' ? pageLike.total : safeRows.length
  }
}
