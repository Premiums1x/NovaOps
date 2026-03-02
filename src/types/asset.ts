import type { TicketPriority, TicketStatus } from './ticket'

export type AssetStatus = 'stock' | 'in_use' | 'scrapped'

export type AssetType = 'server' | 'laptop' | 'network' | 'license'

export type AssetActionType = 'receive' | 'claim' | 'scrap'

export interface AssetListItemDto {
  id: string
  name: string
  type: AssetType
  status: AssetStatus
  owner: string
  location: string
  purchaseDate: string
  updatedAt: string
}

export interface RelatedTicketDto {
  id: string
  title: string
  status: TicketStatus
  priority: TicketPriority
  assignee: string
}

export interface AssetDetailDto extends AssetListItemDto {
  tenantId: string
  spec: string
  remark: string
  relatedTickets: RelatedTicketDto[]
}

export interface AssetSimpleDto {
  id: string
  name: string
  status: AssetStatus
}

export interface AssetListQueryDto {
  page?: number
  pageSize?: number
  status?: AssetStatus
  type?: AssetType
  keyword?: string
}

export interface CreateAssetDto {
  name: string
  type: AssetType
  location: string
  spec: string
  remark?: string
}

export interface UpdateAssetDto {
  name?: string
  type?: AssetType
  location?: string
  spec?: string
  remark?: string
}

export interface AssetActionDto {
  action: AssetActionType
  owner?: string
  remark?: string
}
