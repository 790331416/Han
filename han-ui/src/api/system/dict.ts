import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// ==================== 字典类型 ====================

export interface DictType {
  dictId: number
  dictName: string
  dictType: string
  status: number
  remark?: string
  createTime?: string
}

export interface DictTypeQuery extends PageQuery {
  dictName?: string
  dictType?: string
  status?: number
}

export interface DictTypeForm {
  dictId?: number
  dictName: string
  dictType: string
  status?: number
  remark?: string
}

export function listDictType(query: DictTypeQuery) {
  return get<PageResult<DictType>>('/system/dict/type/list', query)
}

export function listAllDictTypes() {
  return get<DictType[]>('/system/dict/type/all')
}

export function getDictType(id: number) {
  return get<DictType>(`/system/dict/type/${id}`)
}

export function addDictType(data: DictTypeForm) {
  return post<void>('/system/dict/type', data)
}

export function updateDictType(data: DictTypeForm) {
  return post<void>('/system/dict/type/edit', data)
}

export function deleteDictType(id: number) {
  return post<void>(`/system/dict/type/remove/${id}`)
}

// ==================== 字典数据 ====================

export interface DictData {
  dictCode: number
  dictSort: number
  dictLabel: string
  dictValue: string
  dictType: string
  cssClass?: string
  listClass?: string
  isDefault?: string
  status: number
  remark?: string
  createTime?: string
}

export interface DictDataQuery extends PageQuery {
  dictType?: string
  dictLabel?: string
  status?: number
}

export interface DictDataForm {
  dictCode?: number
  dictSort?: number
  dictLabel: string
  dictValue: string
  dictType: string
  cssClass?: string
  listClass?: string
  isDefault?: string
  status?: number
  remark?: string
}

export function listDictData(query: DictDataQuery) {
  return get<PageResult<DictData>>('/system/dict/data/list', query)
}

export function listDictDataByType(dictType: string) {
  return get<DictData[]>(`/system/dict/data/type/${dictType}`)
}

export function getDictData(id: number) {
  return get<DictData>(`/system/dict/data/${id}`)
}

export function addDictData(data: DictDataForm) {
  return post<void>('/system/dict/data', data)
}

export function updateDictData(data: DictDataForm) {
  return post<void>('/system/dict/data/edit', data)
}

export function deleteDictData(id: number) {
  return post<void>(`/system/dict/data/remove/${id}`)
}
