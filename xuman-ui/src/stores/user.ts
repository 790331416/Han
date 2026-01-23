import { defineStore } from 'pinia'
import { getToken, setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'
import { login as loginApi, logout as logoutApi, getUserInfo as getUserInfoApi } from '@/api/auth'
import type { LoginDTO, UserInfo } from '@/types'

interface UserState {
  token: string
  userInfo: UserInfo | null
  tenantId: number | null
  roles: string[]
  permissions: string[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken() || '',
    userInfo: null,
    tenantId: null,
    roles: [],
    permissions: []
  }),

  getters: {
    isLogin: (state) => !!state.token,
    userId: (state) => state.userInfo?.userId,
    username: (state) => state.userInfo?.username,
    nickname: (state) => state.userInfo?.nickname,
    avatar: (state) => state.userInfo?.avatar
  },

  actions: {
    // 登录
    async login(loginForm: LoginDTO) {
      const res = await loginApi(loginForm)
      this.token = res.data.accessToken
      setToken(res.data.accessToken)
      setRefreshToken(res.data.refreshToken)
      return res
    },

    // 获取用户信息
    async getInfo() {
      const res = await getUserInfoApi()
      this.userInfo = res.data
      this.tenantId = res.data.tenantId
      this.roles = res.data.roles || []
      this.permissions = res.data.permissions || []
      return res.data
    },

    // 登出
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略登出错误
      } finally {
        this.token = ''
        this.userInfo = null
        this.roles = []
        this.permissions = []
        removeToken()
        removeRefreshToken()
      }
    },

    // 重置Token
    resetToken() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      removeToken()
      removeRefreshToken()
    },

    // 判断是否有权限
    hasPermission(permission: string): boolean {
      if (this.permissions.includes('*:*:*')) return true
      return this.permissions.includes(permission)
    },

    // 判断是否有角色
    hasRole(role: string): boolean {
      if (this.roles.includes('admin')) return true
      return this.roles.includes(role)
    }
  },

  persist: {
    key: 'xuman-user',
    paths: ['token', 'tenantId']
  }
})
