import type { AxiosRequestConfig } from 'axios'

export interface RequestConfig extends AxiosRequestConfig {
  requestId?: string
  skipErrorToast?: boolean
  skipAuthRefresh?: boolean
  _retry?: boolean
}
