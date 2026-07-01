import { get, post } from '@/utils/request'
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

// 下载代码
export function downloadCode(id: string | number) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  window.open(`${baseUrl}/gen/download/${id}`, '_blank')
}
