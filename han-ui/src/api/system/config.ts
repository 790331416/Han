import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Config {
  id: number
  configName: string
  configKey: string
  configValue: string
  configType: string
  remark?: string
  createTime?: string
}

export interface ConfigQuery extends PageQuery {
  configName?: string
  configKey?: string
  configType?: string
}

export interface ConfigForm {
  id?: number
  configName: string
  configKey: string
  configValue: string
  configType?: string
  remark?: string
}

export function listConfig(query: ConfigQuery) {
  return get<PageResult<Config>>('/system/config/list', query)
}

export function getConfig(id: number) {
  return get<Config>(`/system/config/${id}`)
}

export function getConfigByKey(configKey: string) {
  return get<string>(`/system/config/key/${configKey}`)
}

export function addConfig(data: ConfigForm) {
  return post<void>('/system/config', data)
}

export function updateConfig(data: ConfigForm) {
  return post<void>('/system/config/edit', data)
}

export function deleteConfig(id: number) {
  return post<void>(`/system/config/remove/${id}`)
}
