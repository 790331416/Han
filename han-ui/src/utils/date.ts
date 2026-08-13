/**
 * 日期时间格式化工具。
 *
 * 这些函数原来挂在 `utils/request.ts`（HTTP 层）里，属于职责错位。
 * 现在收口到这里，`utils/request.ts` 保留同名再导出以兼容既有 import 路径。
 */

/**
 * 把后端返回的 ISO 时间串转成 `YYYY-MM-DD HH:mm:ss`，不修改原始值。
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').replace(/\.\d+$/, '')
}

/**
 * 只保留到分钟，用于通知、日志一类空间紧张的列表。
 */
export function formatDateMinute(value: string | null | undefined): string {
  const formatted = formatDate(value)
  return formatted ? formatted.substring(0, 16) : ''
}
