import { get, post } from '@/utils/request'

// 获取服务器信息
export function getServerInfo() {
  return get<any>('/system/monitor/server')
}

export function getServerJvmInfo() {
  return get<any>('/system/monitor/server/jvm')
}

export function getServerSystemInfo() {
  return get<any>('/system/monitor/server/system')
}

// 获取缓存信息
export function getCacheInfo() {
  return get<any>('/system/monitor/cache')
}

// 获取缓存键列表
export function getCacheKeys(pattern?: string) {
  return get<any[]>('/system/monitor/cache/keys', { pattern })
}

// 删除缓存键
export function deleteCache(key: string) {
  return post<void>('/system/monitor/cache/delete', { key })
}
