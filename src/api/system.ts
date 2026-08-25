import request from '@/utils/request'
import type {
  CreateInvitationDto,
  CreatedInvitationDto,
  CreateTenantDto,
  InvitationDto,
  TenantDto,
} from '@/types/system'

export const getTenantsApi = () => request.get<TenantDto[]>('/system/tenants')

export const createTenantApi = (payload: CreateTenantDto) =>
  request.post<TenantDto, CreateTenantDto>('/system/tenants', payload)

export const getInvitationsApi = () => request.get<InvitationDto[]>('/system/invitations')

export const createInvitationApi = (payload: CreateInvitationDto) =>
  request.post<CreatedInvitationDto, CreateInvitationDto>('/system/invitations', payload)
