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
  username: 'admin' | 'staff' | 'guest'
  password: string
  tenantId?: string
}

export interface AuthTokenDto {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface LoginResponseDto extends AuthTokenDto {
  tenantId: string
}
