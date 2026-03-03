import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { KbDetailDto, KbListItemDto, KbListQueryDto, KbVersionDto, SaveKbDto } from '@/types/kb'

export const getKbListApi = (params: KbListQueryDto) => {
  return request.get<PageResult<KbListItemDto>>('/kb', { params })
}

export const getKbDetailApi = (id: string) => {
  return request.get<KbDetailDto>(`/kb/${id}`)
}

export const saveKbApi = (payload: SaveKbDto) => {
  return request.post<KbDetailDto, SaveKbDto>('/kb/save', payload)
}

export const getKbVersionsApi = (id: string) => {
  return request.get<KbVersionDto[]>(`/kb/${id}/versions`)
}
