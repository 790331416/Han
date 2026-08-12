import { getToken } from '@/utils/auth'
import { clearSessionAndRedirectToLogin } from '@/utils/session-refresh'
import { useUserStore } from '@/stores/user'

/** 与 `utils/auth.ts` 保持一致的令牌键名。 */
const TOKEN_KEY = 'Admin-Token'

let sessionSyncBound = false

/**
 * 多标签页会话同步。
 *
 * 原来任何一个标签页登出或切租户，其他标签页都不感知，仍带着旧身份继续请求，
 * 要多走一次 401 才回到登录页。`storage` 事件只在**其他**标签页触发，
 * 正好用来把这条链路补上。
 */
export function setupSessionSync(): void {
  if (sessionSyncBound || typeof window === 'undefined') {
    return
  }
  sessionSyncBound = true

  window.addEventListener('storage', (event) => {
    if (event.key !== TOKEN_KEY) {
      return
    }

    const latestToken = getToken()

    // 别的标签页登出了：本标签页立刻清会话回登录页，不要等下一次 401。
    if (!latestToken) {
      clearSessionAndRedirectToLogin()
      return
    }

    // 别的标签页换了账号或切了租户：同步令牌，避免本页继续用旧身份发请求。
    // 这里刻意不自动刷新页面——用户可能正在填表单，强制重载会丢数据。
    try {
      const userStore = useUserStore()
      if (userStore.token !== latestToken) {
        userStore.token = latestToken
        console.warn('[session-sync] 检测到其他标签页切换了登录身份，当前页数据可能已过期')
      }
    } catch {
      // Pinia 尚未激活时忽略，下一次页面加载会从 localStorage 读到最新令牌。
    }
  })
}
