import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'
import { login as loginApi, logout as logoutApi, getUserInfo as getUserInfoApi } from '@/api/auth'
import type { LoginDTO, UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const userInfo = ref<UserInfo | null>(null)
  const tenantId = ref<number | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isLogin = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId)
  const username = computed(() => userInfo.value?.username)
  const nickname = computed(() => userInfo.value?.nickname)
  const avatar = computed(() => userInfo.value?.avatar)

  async function login(loginForm: LoginDTO) {
    const res = await loginApi(loginForm)
    token.value = res.data.accessToken
    setToken(res.data.accessToken)
    setRefreshToken(res.data.refreshToken)
    return res
  }

  async function getInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    tenantId.value = res.data.tenantId
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    return res.data
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (_e) {
      // 忽略登出错误
    } finally {
      token.value = ''
      userInfo.value = null
      roles.value = []
      permissions.value = []
      removeToken()
      removeRefreshToken()
    }
  }

  function resetToken() {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    removeToken()
    removeRefreshToken()
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
    token, userInfo, tenantId, roles, permissions,
    isLogin, userId, username, nickname, avatar,
    login, getInfo, logout, resetToken, hasPermission, hasRole
  }
}, {
  persist: {
    key: 'HAN-user',
    pick: ['token', 'tenantId']
  }
})
