export type QueryRoute = 'METADATA' | 'RAG' | 'CHAT'
export type ValidationStatus = 'PASSED' | 'NOT_APPLICABLE' | 'NO_EVIDENCE' | 'FAILED' | 'SERVICE_UNAVAILABLE'
export interface CitationDto { index:number; documentId:string; documentName:string; chunkId:string; content:string; score:number }
export type AgentPlanAction = 'search_kb'|'answer'|'validate'
export type AgentPlanStepStatus = 'pending'|'running'|'done'|'failed'
export interface AgentPlanStepDto { action:AgentPlanAction; label:string; query?:string; reason:string; status:AgentPlanStepStatus; payload?:Record<string,unknown> }
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
  steps?:AgentPlanStepDto[]
  reasoningExpanded?:boolean
  createdAt?:string
}
export interface ConversationDto { id:string; userId:string; title:string; createdAt:string; updatedAt:string }
export interface ConversationDetailDto { conversation:ConversationDto; messages:Array<{id:string;conversationId:string;role:'user'|'assistant';content:string;citationsJson?:string;validationPassed?:boolean;createdAt:string}> }
export type AgentSseEvent = 'route'|'plan'|'step'|'delta'|'citation'|'evidence'|'meta'|'done'|'error'
