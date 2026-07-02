import { post, get } from '@/utils/request'
import type { LoginDTO, LoginVO, UserInfo, RouteMenu } from '@/types'

// 登录
export function login(data: LoginDTO) {
  return post<LoginVO>('/auth/login', data)
}

// App登录
export function appLogin(data: LoginDTO) {
  return post<LoginVO>('/auth/app/login', data)
}

// 微信小程序登录
export function wechatMpLogin(data: LoginDTO) {
  return post<LoginVO>('/auth/wechat/mp/login', data)
}

// 微信公众号登录
export function wechatOaLogin(data: LoginDTO) {
  return post<LoginVO>('/auth/wechat/oa/login', data)
}

// 刷新Token
export function refreshToken(refreshToken: string) {
  return post<LoginVO>('/auth/refresh', null, {
    headers: { 'X-Refresh-Token': refreshToken }
  })
}

// 登出
export function logout() {
  return post<void>('/auth/logout')
}

// 获取用户信息
export function getUserInfo() {
  return get<UserInfo>('/system/user/current')
}

// 获取路由菜单
export function getRouters() {
  return get<RouteMenu[]>('/system/menu/routers')
}

// 获取验证码（enabled 为 'false' 时表示后台已关闭验证码，uuid/img 不返回）
export function getCaptcha() {
  return get<{ enabled?: string; uuid?: string; img?: string }>('/auth/captcha')
}

// 获取 RSA 公钥（密码加密传输）
export function getPublicKey() {
  return get<{ enabled: boolean; publicKey?: string }>('/auth/publicKey')
}

// ==================== 租户切换 ====================

export interface TenantSimple {
  tenantId: string | number
  tenantName: string
  status: number
  current: boolean
}

// 查询当前用户在所有租户的账号列表
export function getMyTenants() {
  return get<TenantSimple[]>('/auth/myTenants')
}

// 切换租户
export function switchTenant(tenantId: string | number) {
  return post<LoginVO>(`/auth/switchTenant?tenantId=${tenantId}`)
}
