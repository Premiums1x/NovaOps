import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import type { AuthTokenDto } from '@/types/auth'
import type { ApiResponse } from '@/types/api'
import type { RequestConfig } from './types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './token'

interface PendingRequest {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

type RequestInternalConfig = InternalAxiosRequestConfig & RequestConfig

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const controllers = new Map<string, AbortController>()
const refreshQueue: PendingRequest[] = []
let isRefreshing = false

const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

const syncStoreTokens = async (accessToken: string, refreshToken: string) => {
  const { useAuthStore } = await import('@/store/auth')
  const { pinia } = await import('@/store')
  const authStore = useAuthStore(pinia)
  authStore.setTokens(accessToken, refreshToken)
}

const clearAuthState = async () => {
  const { useAuthStore } = await import('@/store/auth')
  const { pinia } = await import('@/store')
  const authStore = useAuthStore(pinia)
  authStore.clearAuth()
}

const flushRefreshQueue = (error: unknown, token = '') => {
  refreshQueue.forEach((request) => {
    if (error) {
      request.reject(error)
      return
    }
    request.resolve(token)
  })
  refreshQueue.length = 0
}

const getRequestId = (config: RequestConfig) => {
  const method = (config.method || 'get').toUpperCase()
  const url = config.url || ''
  return config.requestId || `${method}:${url}:${Date.now()}`
}

const clearControllerByConfig = (config: RequestConfig) => {
  if (config.requestId) {
    controllers.delete(config.requestId)
  }
}

const parseJwtClaims = (token: string) => {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payload = parts[1] || ''
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    return JSON.parse(window.atob(padded)) as {
      username?: string
      tenantId?: string
    }
  } catch {
    return null
  }
}

const setAuthorizationHeader = (config: RequestConfig, accessToken: string) => {
  if (config.headers instanceof AxiosHeaders) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
    const claims = parseJwtClaims(accessToken)
    if (claims?.tenantId) {
      config.headers.set('X-NovaOps-Tenant-Id', claims.tenantId)
    }
    if (claims?.username) {
      config.headers.set('X-NovaOps-Username', claims.username)
    }
    return
  }

  const claims = parseJwtClaims(accessToken)
  config.headers = {
    ...config.headers,
    Authorization: `Bearer ${accessToken}`,
    ...(claims?.tenantId ? { 'X-NovaOps-Tenant-Id': claims.tenantId } : {}),
    ...(claims?.username ? { 'X-NovaOps-Username': claims.username } : {}),
  }
}

const handleUnauthorized = async (config: RequestConfig) => {
  if (config.skipAuthRefresh || config._retry) {
    await clearAuthState()
    clearTokens()
    window.location.replace('/login')
    return Promise.reject(new Error('Unauthorized'))
  }

  config._retry = true

  if (!isRefreshing) {
    isRefreshing = true
    try {
      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        throw new Error('Missing refresh token')
      }
      const response = await axios.post<ApiResponse<AuthTokenDto>>(
        `${API_BASE_URL}/auth/refresh`,
        { refreshToken },
        { timeout: 10000 }
      )
      if (response.data.code !== 0) {
        throw new Error(response.data.message || 'Refresh failed')
      }
      const tokens = response.data.data
      setTokens(tokens.accessToken, tokens.refreshToken)
      await syncStoreTokens(tokens.accessToken, tokens.refreshToken)
      flushRefreshQueue(null, tokens.accessToken)
    } catch (error) {
      flushRefreshQueue(error)
      await clearAuthState()
      clearTokens()
      window.location.replace('/login')
      throw error
    } finally {
      isRefreshing = false
    }
  }

  return new Promise((resolve, reject) => {
    refreshQueue.push({
      resolve: (token) => {
        setAuthorizationHeader(config, token)
        resolve(http(config))
      },
      reject: (error) => reject(error),
    })
  })
}

const onRequest = (config: InternalAxiosRequestConfig) => {
  const requestConfig = config as RequestInternalConfig
  const accessToken = getAccessToken()
  if (accessToken) {
    setAuthorizationHeader(requestConfig, accessToken)
  }

  if (!requestConfig.signal) {
    const requestId = getRequestId(requestConfig)
    const controller = new AbortController()
    requestConfig.requestId = requestId
    requestConfig.signal = controller.signal
    controllers.set(requestId, controller)
  }

  return requestConfig
}

const onResponse = async (response: AxiosResponse<ApiResponse<unknown>>) => {
  const config = response.config as RequestConfig
  clearControllerByConfig(config)

  const { code, message: msg, data } = response.data
  if (code === 0) {
    return data
  }
  if (code === 401) {
    return handleUnauthorized(config)
  }
  if (!config.skipErrorToast) {
    message.error(msg || 'Request failed')
  }
  return Promise.reject(new Error(msg || 'Request failed'))
}

const onResponseError = async (error: AxiosError<ApiResponse<unknown>>) => {
  const config = (error.config || {}) as RequestConfig
  clearControllerByConfig(config)

  if (axios.isCancel(error)) {
    return Promise.reject(error)
  }

  const responseCode = error.response?.data?.code
  if (responseCode === 401) {
    return handleUnauthorized(config)
  }

  if (!config.skipErrorToast) {
    message.error(error.response?.data?.message || error.message || 'Network error')
  }
  return Promise.reject(error)
}

http.interceptors.request.use(onRequest)
http.interceptors.response.use(onResponse as never, onResponseError as never)

const request = {
  get<T>(url: string, config?: RequestConfig) {
    return http.get<ApiResponse<T>, T>(url, config)
  },
  post<T, D = unknown>(url: string, data?: D, config?: RequestConfig) {
    return http.post<ApiResponse<T>, T, D>(url, data, config)
  },
  put<T, D = unknown>(url: string, data?: D, config?: RequestConfig) {
    return http.put<ApiResponse<T>, T, D>(url, data, config)
  },
  delete<T>(url: string, config?: RequestConfig) {
    return http.delete<ApiResponse<T>, T>(url, config)
  },
  request<T>(config: RequestConfig) {
    return http.request<ApiResponse<T>, T>(config)
  },
  cancelRequest(requestId: string) {
    const controller = controllers.get(requestId)
    if (controller) {
      controller.abort()
      controllers.delete(requestId)
    }
  },
  cancelAll() {
    controllers.forEach((controller) => controller.abort())
    controllers.clear()
  },
}

export default request
