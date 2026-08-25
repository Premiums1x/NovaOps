export interface TenantDto {
  code: string
  name: string
}

export interface CreateTenantDto {
  code: string
  name: string
}

export type InvitationRoleCode = 'staff' | 'guest'

export interface InvitationDto {
  id: string
  tenantId: string
  tenantName: string
  roleCode: InvitationRoleCode
  createdBy: string
  expiresAt: string
  usedAt?: string | null
  createdAt: string
}

export interface CreatedInvitationDto extends InvitationDto {
  token: string
}

export interface CreateInvitationDto {
  tenantId: string
  roleCode: InvitationRoleCode
}
