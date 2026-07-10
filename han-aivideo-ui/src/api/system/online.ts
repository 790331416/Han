import { get, post } from '@/utils/request'

export interface OnlineUser {
  tokenId: string
  userId: string | number
  username: string
  nickname: string
  ipAddr: string
  clientType: string
  loginTime: number
}

// 查询在线用户列表
export function listOnlineUser(query?: { username?: string; ipAddr?: string }) {
  return get<OnlineUser[]>('/system/online/list', query)
}

// 强制下线
export function forceLogout(tokenId: string) {
  return post<void>('/system/online/forceLogout', { tokenId })
}
