export interface TenantInfo {
  id: string
  name: string
}

export interface UserProfile {
  id: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  tenantId: string
  tenants: TenantInfo[]
}

export interface LoginRequestDto {
  username: string
  password: string
  tenantId?: string
  roleId: string
}

export interface RegisterRequestDto {
  username: string
  email: string
  password: string
}

export interface RoleDto {
  id: string
  code: string
  name: string
  description: string
  permissions: string[]
}

export interface UserListItemDto {
  id: string
  username: string
  displayName: string
  roleId: string
  roleCode: string
  roleName: string
  enabled: boolean
  createdAt: string
}

export interface UserListQueryDto {
  page: number
  pageSize: number
  keyword?: string
  roleId?: string
  enabled?: boolean
}

export interface AuthTokenDto {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface LoginResponseDto extends AuthTokenDto {
  tenantId: string
}
