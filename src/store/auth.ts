import { defineStore } from 'pinia'
import { loginApi, meApi, refreshTokenApi } from '@/api/auth'
import type { LoginRequestDto, UserProfile } from '@/types/auth'
import { clearTokens, setTokens as setTokenCache } from '@/utils/request/token'

//用户信息
interface AuthState {
  accessToken: string
  refreshToken: string
  user: UserProfile | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: '',
    refreshToken: '',
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
      this.user = null
      clearTokens()
    },

    async login(payload: LoginRequestDto) {
      const data = await loginApi(payload)

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
      return data
    },

    logout() {
      this.clearAuth()
    },
  },
  
  persist: {
    key: 'novaops_auth',
    pick: ['accessToken', 'refreshToken'],
  },
})
