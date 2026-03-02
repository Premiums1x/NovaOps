export type TicketStatus = 'pending' | 'processing' | 'review' | 'done'

export type TicketPriority = 'low' | 'medium' | 'high' | 'urgent'

export type TicketActionType = 'assign' | 'close' | 'reject' | 'transfer' | 'advance'

export interface TicketAttachmentDto {
  id: string
  name: string
  url: string
  size: number
  createdAt: string
}

export interface TicketCommentDto {
  id: string
  author: string
  content: string
  createdAt: string
}

export interface TicketTimelineItemDto {
  id: string
  action: TicketActionType | 'create' | 'update'
  operator: string
  remark?: string
  fromStatus?: TicketStatus
  toStatus?: TicketStatus
  createdAt: string
}

export interface TicketListItemDto {
  id: string
  title: string
  status: TicketStatus
  priority: TicketPriority
  assignee: string
  creator: string
  createdAt: string
  updatedAt: string
  assetIds: string[]
}

export interface TicketDetailDto extends TicketListItemDto {
  tenantId: string
  description: string
  dueDate?: string
  timeline: TicketTimelineItemDto[]
  comments: TicketCommentDto[]
  attachments: TicketAttachmentDto[]
}

export interface TicketListQueryDto {
  page?: number
  pageSize?: number
  status?: TicketStatus
  priority?: TicketPriority
  keyword?: string
  startDate?: string
  endDate?: string
}

export interface CreateTicketDto {
  title: string
  description: string
  priority: TicketPriority
  assignee?: string
  dueDate?: string
  assetIds?: string[]
}

export interface UpdateTicketDto {
  title?: string
  description?: string
  priority?: TicketPriority
  assignee?: string
  dueDate?: string
  assetIds?: string[]
}

export interface TicketActionDto {
  action: TicketActionType
  assignee?: string
  targetUser?: string
  remark?: string
}

export interface CreateCommentDto {
  content: string
}

export interface UploadAttachmentDto {
  filename: string
  size: number
}
