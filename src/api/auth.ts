import request from '@/utils/request'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, RoleDto, UserListItemDto, UserListQueryDto, UserProfile } from '@/types/auth'
import type { PageResult } from '@/types/api'
import type { MenuDataDto } from '@/types/menu'

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

export const menuApi = () => {
  return request.get<MenuDataDto>('/auth/menu')
}

export const switchTenantApi = (tenantId: string) => {
  return request.post<LoginResponseDto, { tenantId: string }>('/auth/switch-tenant', { tenantId })
}

export const getRolesApi = () => {
  return request.get<RoleDto[]>('/auth/roles')
}

export const getUsersApi = (params: UserListQueryDto) => request.get<PageResult<UserListItemDto>>('/auth/users', { params })
export const updateUserStatusApi = (id: string, enabled: boolean) => request.put<void, { enabled: boolean }>(`/auth/users/${id}/status`, { enabled })
export const updateUserRoleApi = (id: string, roleId: string) => request.put<void, { roleId: string }>(`/auth/users/${id}/role`, { roleId })
export const resetUserPasswordApi = (id: string, password: string) => request.put<void, { password: string }>(`/auth/users/${id}/password`, { password })
