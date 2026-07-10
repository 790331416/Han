import type { AiPromptTemplate } from '@/api/ai'

/**
 * Prompt 模板列表页当前需要的查询条件。
 *
 * <p>单独抽出轻量类型，方便页面逻辑和回归测试共用，不把整页状态直接耦合进测试。 */
export interface PromptTemplateQueryLike {
  templateName?: string
  category?: string
  status?: string | number | null
  pageNum?: number
  pageSize?: number
}

/**
 * 判断当前是否应该从 `/all` 接口兜底恢复模板列表。
 *
 * <p>只在“第一页、无筛选、分页接口返回空”的情况下启用，
 * 避免覆盖用户主动搜索或翻页后的真实空结果。 */
export function shouldFallbackToAllPromptTemplates(
  query: PromptTemplateQueryLike,
  rows: AiPromptTemplate[]
): boolean {
  if (rows.length > 0) {
    return false
  }

  const templateName = String(query.templateName || '').trim()
  const category = String(query.category || '').trim()
  const status = query.status === undefined || query.status === null ? '' : String(query.status).trim()
  const pageNum = Math.max(Number(query.pageNum || 1), 1)

  return !templateName && !category && !status && pageNum === 1
}

/**
 * 按页面当前分页参数切片 `/all` 返回的模板数据。
 */
export function paginatePromptTemplates(
  rows: AiPromptTemplate[],
  query: PromptTemplateQueryLike
): { rows: AiPromptTemplate[]; total: number } {
  const pageNum = Math.max(Number(query.pageNum || 1), 1)
  const pageSize = Math.max(Number(query.pageSize || 10), 1)
  const start = (pageNum - 1) * pageSize
  const end = start + pageSize

  return {
    rows: rows.slice(start, end),
    total: rows.length
  }
}
