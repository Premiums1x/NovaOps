export interface KbListItemDto {
  id: string
  title: string
  tags: string[]
  author: string
  updatedAt: string
}

export interface KbVersionDto {
  id: string
  title: string
  tags: string[]
  content: string
  editor: string
  createdAt: string
}

export interface KbDetailDto extends KbListItemDto {
  tenantId: string
  content: string
}

export interface KbListQueryDto {
  page?: number
  pageSize?: number
  keyword?: string
  tag?: string
}

export interface SaveKbDto {
  id?: string
  title: string
  tags: string[]
  content: string
}
