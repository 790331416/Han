import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'
import { login as loginApi, logout as logoutApi, getUserInfo as getUserInfoApi, getMyIdentities as getMyIdentitiesApi } from '@/api/auth'
import type { LoginDTO, UserInfo, IdentityVO } from '@/types'

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

  // 当前学校身份（一账号多学校身份，按身份隔离展示与切换）。
  const identityId = ref<string | number | null>(null)
  const schoolId = ref<string | number | null>(null)
  const schoolName = ref('')
  const personType = ref('')
  const dutyCode = ref('')
  const dutyName = ref('')
  const identityDisplayName = ref('')
  const identityList = ref<IdentityVO[]>([])

  const isLogin = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId ?? null)
  const username = computed(() => userInfo.value?.username ?? '')
  const nickname = computed(() => userInfo.value?.nickname ?? '')
  const avatar = computed(() => userInfo.value?.avatar ?? '')

  /** 管理端可用性：以后端返回的 managementAvailable 为准，不再按 dutyCode 推导。 */
  function identityManagementAvailable(identity?: IdentityVO | null) {
    return identity?.managementAvailable === true
  }

  /** 管理端不可用原因：优先展示后端返回的原因，缺省给通用文案。 */
  function identityManagementUnavailableReason(identity?: IdentityVO | null) {
    return identity?.managementUnavailableReason || '无管理端权限'
  }

  /** 把一条身份摘要写入当前身份字段（identity 为空则清空）。 */
  function applyIdentity(identity?: IdentityVO | null) {
    identityId.value = identity?.identityId ?? null
    schoolId.value = identity?.schoolId ?? null
    schoolName.value = identity?.schoolName ?? ''
    personType.value = identity?.personType ?? ''
    dutyCode.value = identity?.dutyCode ?? ''
    dutyName.value = identity?.dutyName ?? ''
    identityDisplayName.value = identity?.identityDisplayName ?? ''
  }

  /** 拉取当前账号有效身份列表，并把 current 身份写回 Store。 */
  async function loadIdentities() {
    const res = await getMyIdentitiesApi()
    const list = res.data || []
    identityList.value = list
    const current = list.find((item) => item.current) ?? (list.length === 1 ? list[0] : null)
    applyIdentity(current)
    return list
  }

  /**
   * 统一写入访问令牌、刷新令牌和调试身份锚点。
   */
  function applySession(accessToken: string, refreshToken?: string | null, runtimeUserId?: string | number | null) {
    // 切换账号后必须让路由守卫重新拉取菜单和权限，不能复用上一个账号的动态路由。
    userInfo.value = null
    tenantId.value = null
    tenantName.value = ''
    roles.value = []
    permissions.value = []
    // 身份字段随会话一并失效，避免新会话误用上一个账号/身份。
    identityList.value = []
    applyIdentity(null)
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
    identityList.value = []
    applyIdentity(null)
    removeToken()
    removeRefreshToken()
  }

  async function login(loginForm: LoginDTO) {
    const res = await loginApi(loginForm)
    // 多学校身份待选择时后端不签发正式 Token，不能在 Store 里写入空令牌。
    if (!res.data?.requireIdentity) {
      applySession(res.data.accessToken, res.data.refreshToken, res.data.userInfo?.userId ?? null)
    }
    return res
  }

  /**
   * 刷新用户资料时同步更新 `_userId`，避免调试身份锚点长期保留旧值。
   * 当前身份不在 userinfo 内，额外拉取 /auth/identities（失败不阻断登录）。
   */
  async function getInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    _userId.value = res.data.userId ?? null
    tenantId.value = res.data.tenantId
    tenantName.value = (res.data as UserInfo & { tenantName?: string }).tenantName || ''
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    try {
      await loadIdentities()
    } catch (_error) {
      // 身份接口不可用时保留空身份，不影响菜单与权限加载。
    }
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
    identityId,
    schoolId,
    schoolName,
    personType,
    dutyCode,
    dutyName,
    identityDisplayName,
    identityList,
    isLogin,
    userId,
    username,
    nickname,
    avatar,
    applyIdentity,
    loadIdentities,
    identityManagementAvailable,
    identityManagementUnavailableReason,
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
