import request from '@/utils/request'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, UserProfile } from '@/types/auth'

export const loginApi = (payload: LoginRequestDto) => {
  return request.post<LoginResponseDto, LoginRequestDto>('/auth/login', payload, {
    skipAuthRefresh: true,
  })
}

export const refreshTokenApi = (refreshToken: string) => {
  return request.post<AuthTokenDto, { refreshToken: string }>(
    '/auth/refresh',
    { refreshToken },
    { skipAuthRefresh: true }
  )
}

export const meApi = () => {
  return request.get<UserProfile>('/auth/me')
}
