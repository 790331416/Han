import { get, post } from '@/utils/request'

export type BrandDisplayMode = 'FULL_NAME' | 'SHORT_NAME'

export interface SystemBrand {
  fullName: string
  shortName: string
  displayMode: BrandDisplayMode
  displayName: string
  loginSubtitle: string
  logoUrl: string
}

export interface SystemBrandForm {
  fullName: string
  shortName: string
  displayMode: BrandDisplayMode
  loginSubtitle: string
  allowInsecureVendorRegistration?: boolean
}

/** 管理端专用系统设置；测试安全开关不属于公开品牌信息。 */
export interface SystemBrandSettings extends SystemBrand {
  allowInsecureVendorRegistration: boolean
}

/** 由系统设置菜单权限控制，服务端会再次校验。 */
export function getSystemBrand() {
  return get<SystemBrandSettings>('/system/brand')
}

export function updateSystemBrand(data: SystemBrandForm) {
  return post<SystemBrandSettings>('/system/brand', data)
}

export function uploadSystemBrandLogo(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<SystemBrandSettings>('/system/brand/logo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
