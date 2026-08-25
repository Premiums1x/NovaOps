import { defineStore } from 'pinia'
import { loginApi, meApi, refreshTokenApi, switchTenantApi } from '@/api/auth'
import type { LoginRequestDto, UserProfile } from '@/types/auth'
import { clearTokens, setTokens as setTokenCache } from '@/utils/request/token'

//用户信息
interface AuthState {
  accessToken: string
  refreshToken: string
  tenantId: string
  user: UserProfile | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: '',
    refreshToken: '',
    tenantId: '',
    user: null,
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    permissions: (state) => state.user?.permissions || [],
  },
  
  actions: {
    setTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      setTokenCache(accessToken, refreshToken)
    },

    clearAuth() {
      this.accessToken = ''
      this.refreshToken = ''
      this.tenantId = ''
      this.user = null
      clearTokens()
    },

    async login(payload: LoginRequestDto) {
      const data = await loginApi(payload)

      this.tenantId = data.tenantId

      this.setTokens(data.accessToken, data.refreshToken)

      await this.me()
    },

    async refresh() {
      if (!this.refreshToken) {
        throw new Error('Missing refresh token')
      }

      //有refreshToken就拿新的两个token
      const data = await refreshTokenApi(this.refreshToken)
      
      this.setTokens(data.accessToken, data.refreshToken)
    },

    async me() {
      const data = await meApi()
      //拿到用户信息对象
      this.user = data
      this.tenantId = data.tenantId
      return data
    },

    async switchTenant(tenantId: string) {

      const data = await switchTenantApi(tenantId)

      // 更新Auth内容
      this.tenantId = data.tenantId
      this.setTokens(data.accessToken, data.refreshToken)

      await this.me()
    },

    logout() {
      this.clearAuth()
    },
  },
  
  persist: {
    key: 'novaops_auth',
    pick: ['accessToken', 'refreshToken', 'tenantId'],
  },
})
