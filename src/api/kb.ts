import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { KbChunkDto, KbDetailDto, KbDocumentDto, KbDocumentQueryDto, KbListItemDto, KbListQueryDto, KbVersionDto, SaveKbDto } from '@/types/kb'

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

export const getKbDocumentsApi = (params: KbDocumentQueryDto) => request.get<PageResult<KbDocumentDto>>('/kb/documents', { params })
export const uploadKbDocumentApi = (file: File, title?: string) => { const data=new FormData(); data.append('file',file); if(title) data.append('title',title); return request.post<KbDocumentDto,FormData>('/kb/documents',data,{ timeout:60000 }) }
export const getKbDocumentChunksApi = (id:string) => request.get<KbChunkDto[]>(`/kb/documents/${id}/chunks`)
export const updateKbDocumentTitleApi = (id:string,title:string) => request.put<void,{title:string}>(`/kb/documents/${id}`,{title})
export const deleteKbDocumentApi = (id:string) => request.delete<void>(`/kb/documents/${id}`)
export const replaceKbDocumentApi = (id:string,file:File,title?:string) => { const data=new FormData();data.append('file',file);if(title)data.append('title',title);return request.post<KbDocumentDto,FormData>(`/kb/documents/${id}/replace`,data,{timeout:60000}) }
