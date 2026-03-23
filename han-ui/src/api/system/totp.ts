import { get, post } from '@/utils/request'

export interface TotpSetup {
  secret: string
  qrCode: string
  otpAuthUrl: string
}

export interface TotpStatus {
  enabled: boolean
}

// 获取 TOTP 绑定信息（密钥 + 二维码）
export function getTotpSetup() {
  return get<TotpSetup>('/auth/totp/setup')
}

// 确认绑定 TOTP
export function bindTotp(secret: string, code: string) {
  return post<void>('/auth/totp/bind', { secret, code })
}

// 解绑 TOTP
export function unbindTotp(password: string) {
  return post<void>('/auth/totp/unbind', { password })
}

// 获取 2FA 状态
export function getTotpStatus() {
  return get<TotpStatus>('/auth/totp/status')
}
