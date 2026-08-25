import { defineStore } from 'pinia'
import { loginApi, meApi, refreshTokenApi, registerApi } from '@/api/auth'
import type { LoginRequestDto, LoginResponseDto, RegisterRequestDto, UserProfile } from '@/types/auth'
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
    async establishSession(data: LoginResponseDto) {
      this.tenantId = data.tenantId
      this.setTokens(data.accessToken, data.refreshToken)
      await this.me()
    },

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
      await this.establishSession(data)
    },

    async register(payload: RegisterRequestDto) {
      const data = await registerApi(payload)
      await this.establishSession(data)
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

    logout() {
      this.clearAuth()
    },
  },
  
  persist: {
    key: 'novaops_auth',
    pick: ['accessToken', 'refreshToken', 'tenantId'],
  },
})
