import request from '@/utils/request'
import type { ConversationDetailDto,ConversationDto } from '@/types/agent'
export const getConversationsApi=()=>request.get<ConversationDto[]>('/agent/conversations')
export const getConversationApi=(id:string)=>request.get<ConversationDetailDto>(`/agent/conversations/${id}`)
