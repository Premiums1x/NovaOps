export type QueryRoute = 'METADATA' | 'RAG' | 'CHAT'
export type ValidationStatus = 'PASSED' | 'NOT_APPLICABLE' | 'NO_EVIDENCE' | 'FAILED' | 'SERVICE_UNAVAILABLE'
export interface CitationDto { index:number; documentId:string; documentName:string; chunkId:string; content:string; score:number }
export interface ChatMessageDto {
  id:string
  role:'user'|'assistant'
  content:string
  citations?:CitationDto[]
  evidence?:CitationDto[]
  route?:QueryRoute
  routeReason?:string
  retrievalExecuted?:boolean
  retrievedCount?:number
  validatedCount?:number
  validationStatus?:ValidationStatus
  validationReason?:string
  validationPassed?:boolean
  createdAt?:string
}
export interface ConversationDto { id:string; userId:string; title:string; createdAt:string; updatedAt:string }
export interface ConversationDetailDto { conversation:ConversationDto; messages:Array<{id:string;conversationId:string;role:'user'|'assistant';content:string;citationsJson?:string;validationPassed?:boolean;createdAt:string}> }
export type AgentSseEvent = 'route'|'delta'|'citation'|'evidence'|'meta'|'done'|'error'
