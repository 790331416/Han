/**
 * 社交登录的浏览器会话绑定。
 *
 * 后端的 state 只在 Redis 里存了 `state -> provider`，不与发起方浏览器绑定，
 * 攻击者可以自己走一遍授权拿到合法的 code + state，再把回调链接投给受害者，
 * 受害者的浏览器就会被静默登录成攻击者的账号（登录 CSRF）。
 *
 * 这里在发起授权时把 state 写进 sessionStorage，回调页比对一致才继续，
 * 并在校验后立即清除，保证一次性。sessionStorage 是标签页级别的，
 * 与「同一标签页内跳走再跳回」的 OAuth 重定向流程天然匹配。
 */

const SOCIAL_STATE_KEY = 'han-social-login-state'

export interface SocialLoginState {
  provider: string
  /** 后端签发的 state；从授权 URL 中解析不到时为空串，此时只校验 provider */
  state: string
  /** 发起登录时的目标页，回调成功后跳回，避免第三方登录固定落到首页 */
  redirect: string
}

export function persistSocialState(payload: SocialLoginState): void {
  try {
    window.sessionStorage.setItem(SOCIAL_STATE_KEY, JSON.stringify(payload))
  } catch {
    // 隐私模式下 sessionStorage 不可用，回调页会因为读不到记录而要求重新发起登录
  }
}

export function consumeSocialState(): SocialLoginState | null {
  try {
    const raw = window.sessionStorage.getItem(SOCIAL_STATE_KEY)
    window.sessionStorage.removeItem(SOCIAL_STATE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed.provider !== 'string') return null
    return {
      provider: parsed.provider,
      state: typeof parsed.state === 'string' ? parsed.state : '',
      redirect: typeof parsed.redirect === 'string' ? parsed.redirect : '/'
    }
  } catch {
    return null
  }
}

/** 登录后跳转目标只接受站内绝对路径，拒绝 `//host` 与 `/\host` 这类协议相对地址 */
export function resolveSafeRedirect(target: unknown): string {
  if (typeof target !== 'string' || !target) return '/'
  if (!target.startsWith('/')) return '/'
  if (target.startsWith('//') || target.startsWith('/\\')) return '/'
  return target
}
