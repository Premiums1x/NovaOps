export interface CitationDto { index:number; documentId:string; documentName:string; chunkId:string; content:string; score:number }
export type AgentPlanAction = 'search_kb'|'answer'|'validate'
export type AgentPlanStepStatus = 'pending'|'running'|'done'|'failed'
export interface AgentPlanStepDto { action:AgentPlanAction; label:string; query?:string; reason:string; status:AgentPlanStepStatus; payload?:Record<string,unknown> }
export interface ChatMessageDto { id:string; role:'user'|'assistant'; content:string; citations?:CitationDto[]; validationPassed?:boolean; steps?:AgentPlanStepDto[]; reasoningExpanded?:boolean; createdAt?:string }
export interface ConversationDto { id:string; userId:string; title:string; createdAt:string; updatedAt:string }
export interface ConversationDetailDto { conversation:ConversationDto; messages:Array<{id:string;conversationId:string;role:'user'|'assistant';content:string;citationsJson?:string;validationPassed?:boolean;createdAt:string}> }
export type AgentSseEvent = 'plan'|'step'|'delta'|'citation'|'meta'|'done'|'error'
