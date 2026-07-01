import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'
import { login as loginApi, logout as logoutApi, getUserInfo as getUserInfoApi } from '@/api/auth'
import type { LoginDTO, UserInfo } from '@/types'

/**
 * 用户会话 Store。
 *
 * 约束：
 * 1. token、租户和调试身份锚点统一在这里维护，避免不同入口只更新一部分状态。
 * 2. `_userId` 仅用于本地调试绕过网关时的可选身份锚点，线上默认不依赖该字段。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const _userId = ref<string | number | null>(null)
  const userInfo = ref<UserInfo | null>(null)
  const tenantId = ref<string | number | null>(null)
  const tenantName = ref<string>('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isLogin = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId ?? null)
  const username = computed(() => userInfo.value?.username ?? '')
  const nickname = computed(() => userInfo.value?.nickname ?? '')
  const avatar = computed(() => userInfo.value?.avatar ?? '')

  /**
   * 统一写入访问令牌、刷新令牌和调试身份锚点。
   */
  function applySession(accessToken: string, refreshToken?: string | null, runtimeUserId?: string | number | null) {
    token.value = accessToken
    _userId.value = runtimeUserId ?? null
    setToken(accessToken)
    if (refreshToken) {
      setRefreshToken(refreshToken)
    }
  }

  /**
   * 统一清理当前用户上下文，确保令牌、租户、权限与调试身份一起失效。
   */
  function clearUserContext() {
    token.value = ''
    _userId.value = null
    userInfo.value = null
    tenantId.value = null
    tenantName.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
    removeRefreshToken()
  }

  async function login(loginForm: LoginDTO) {
    const res = await loginApi(loginForm)
    applySession(res.data.accessToken, res.data.refreshToken, res.data.userInfo?.userId ?? null)
    return res
  }

  /**
   * 刷新用户资料时同步更新 `_userId`，避免调试身份锚点长期保留旧值。
   */
  async function getInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    _userId.value = res.data.userId ?? null
    tenantId.value = res.data.tenantId
    tenantName.value = (res.data as UserInfo & { tenantName?: string }).tenantName || ''
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    return res.data
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (_error) {
      // 退出登录接口失败时，前端仍然要确保本地会话彻底清空。
    } finally {
      clearUserContext()
    }
  }

  function resetToken() {
    clearUserContext()
  }

  function hasPermission(permission: string): boolean {
    if (permissions.value.includes('*:*:*')) return true
    return permissions.value.includes(permission)
  }

  function hasRole(role: string): boolean {
    if (roles.value.includes('admin')) return true
    return roles.value.includes(role)
  }

  return {
    token,
    _userId,
    userInfo,
    tenantId,
    tenantName,
    roles,
    permissions,
    isLogin,
    userId,
    username,
    nickname,
    avatar,
    applySession,
    clearUserContext,
    login,
    getInfo,
    logout,
    resetToken,
    hasPermission,
    hasRole
  }
}, {
  persist: {
    key: 'HAN-user',
    pick: ['token', 'tenantId', '_userId']
  }
})
