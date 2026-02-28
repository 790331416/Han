import { get, post } from '@/utils/request'

export interface OnlineUser {
  tokenKey: string
  userId: number
  username: string
  nickname: string
  deptId: number
  loginIp: string
  loginTime: number
}

// 查询在线用户列表
export function listOnlineUser(query?: { username?: string; ipaddr?: string }) {
  return get<OnlineUser[]>('/system/online/list', query)
}

// 强制下线
export function forceLogout(tokenKey: string) {
  return post<void>('/system/online/forceLogout', { tokenKey })
}
