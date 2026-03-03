import { get, post, postParams } from '@/utils/request'

export interface ProfileInfo {
  userId: string | number
  username: string
  nickname: string
  phone?: string
  email?: string
  sex?: number
  avatar?: string
  deptId?: string | number
  createTime?: string
}

export interface ProfileForm {
  nickname?: string
  phone?: string
  email?: string
  sex?: number
}

export function getProfile() {
  return get<ProfileInfo>('/system/user/profile')
}

export function updateProfile(data: ProfileForm) {
  return post<void>('/system/user/profile/edit', data)
}

export function updatePassword(oldPassword: string, newPassword: string) {
  return postParams<void>('/system/user/profile/updatePwd', { oldPassword, newPassword })
}

export function updateAvatar(avatarUrl: string) {
  return postParams<void>('/system/user/profile/avatar', { avatarUrl })
}
