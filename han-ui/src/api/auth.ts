import { post, get } from '@/utils/request'
import type { LoginDTO, LoginVO, UserInfo, RouteMenu, IdentityVO } from '@/types'

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

// ==================== 学校身份选择 / 切换 ====================

// 登录返回 requireIdentity 后，凭一次性票据选择身份换取正式 Token
export function selectIdentity(data: { identityTicket: string; identityId: string | number }) {
  return post<LoginVO>('/auth/identity/select', data)
}

// 当前账号仍有效的学校身份列表（含 current 标记当前身份）
export function getMyIdentities() {
  return get<IdentityVO[]>('/auth/identities')
}

// 切换学校身份：作废旧 Token 与旧身份课堂凭证后换发新 Token
export function switchIdentity(identityId: string | number) {
  return post<LoginVO>('/auth/identity/switch', { identityId })
}

// ==================== 社交登录（GitHub / 微信扫码） ====================

// 多租户绑定场景下的可选租户
export interface SocialTenantOption {
  tenantId: string | number
  tenantName: string
}

// 社交登录回调三态结果：
// 1. 已绑定单账号：bound=true 且 login 为登录态
// 2. 多租户绑定：bound=true 且 multiTenant=true，凭 ticket 选租户登录
// 3. 未绑定：bound=false，凭 ticket 走账号密码绑定
export interface SocialCallbackResult {
  bound: boolean
  login?: LoginVO
  multiTenant?: boolean
  ticket?: string
  tenants?: SocialTenantOption[]
  provider?: string
  nickname?: string
  avatar?: string
}

// 获取已启用的社交登录方式（键为 provider，如 github / wechat）
export function getSocialProviders() {
  return get<Record<string, boolean>>('/auth/social/providers', undefined, { silentError: true })
}

// 获取第三方授权跳转 URL（含一次性 state）
export function getSocialAuthorizeUrl(provider: string, redirectUri: string) {
  return get<{ authorizeUrl: string }>(`/auth/social/${provider}/authorize`, { redirectUri })
}

// OAuth 回调：按绑定情况返回登录态 / 租户列表 / 绑定 ticket
export function socialCallback(provider: string, code: string, state: string) {
  return post<SocialCallbackResult>(`/auth/social/${provider}/callback`, { code, state }, { silentError: true })
}

// 账号密码绑定社交身份并直接登录（密码按登录页同样方式 RSA 加密传输）
export function socialBind(data: { ticket: string; username: string; password: string; tenantId?: string }) {
  return post<LoginVO>('/auth/social/bind', data, { silentError: true })
}

// 多租户绑定场景：选择租户后凭 ticket 登录
export function socialLoginByTicket(ticket: string, tenantId: string | number) {
  return post<LoginVO>('/auth/social/loginByTicket', { ticket, tenantId: String(tenantId) }, { silentError: true })
}
