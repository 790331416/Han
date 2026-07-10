import service, { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface GenTable {
  id: string | number
  tableName: string
  tableComment: string
  packageName: string
  moduleName: string
  businessName: string
  functionName: string
  author: string
  parentMenuId: string | number | null
  createTime: string
  updateTime: string
  columns?: GenTableColumn[]
}

export interface GenTableColumn {
  id: string | number
  tableId: string | number
  columnName: string
  columnComment: string
  columnType: string
  javaType: string
  javaField: string
  isPk: number
  isIncrement: number
  isRequired: number
  isInsert: number
  isEdit: number
  isList: number
  isQuery: number
  queryType: string
  htmlType: string
  dictType: string
  sort: number
}

export interface DbTableInfo {
  tableName: string
  tableComment: string
}

// 查询已导入的表列表
export function listGenTable(query: { pageNum: number; pageSize: number; tableName?: string }) {
  return get<PageResult<GenTable>>('/gen/list', query)
}

// 查询数据库中未导入的表
export function listDbTable(tableName?: string) {
  return get<DbTableInfo[]>('/gen/db/list', { tableName })
}

// 导入表
export function importTable(tableNames: string[]) {
  return post<void>('/gen/importTable', tableNames)
}

// 查询表详情
export function getGenTable(id: string | number) {
  return get<GenTable>(`/gen/${id}`)
}

// 修改表配置
export function updateGenTable(data: GenTable) {
  return post<void>('/gen/edit', data)
}

// 删除表配置
export function deleteGenTable(id: string | number) {
  return post<void>(`/gen/remove/${id}`)
}

// 预览代码
export function previewCode(id: string | number) {
  return get<Record<string, string>>(`/gen/preview/${id}`)
}

// 下载代码（ZIP）
// 必须走 axios 带 Authorization 头：window.open 的导航请求无 token，
// 且 Accept:text/html 会被 nginx SPA 回退到 index.html 导致前端路由 404
export async function downloadCode(id: string | number) {
  const response = await service({
    url: `/gen/download/${id}`,
    method: 'GET',
    responseType: 'blob'
  })
  const blob = new Blob([response.data], { type: 'application/zip' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = 'gen-code.zip'
  link.click()
  URL.revokeObjectURL(link.href)
}
