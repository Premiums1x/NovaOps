import type { AxiosRequestConfig } from 'axios'

// 在 Axios 原有的请求配置基础上，额外加了几个自定义选项
export interface RequestConfig extends AxiosRequestConfig {
  requestId?: string
  skipErrorToast?: boolean
  skipAuthRefresh?: boolean
  _retry?: boolean
}
