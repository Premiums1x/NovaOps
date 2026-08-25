export interface CitationDto { index:number; documentId:string; documentName:string; chunkId:string; content:string; score:number }
export interface ChatMessageDto { id:string; role:'user'|'assistant'; content:string; citations?:CitationDto[]; validationPassed?:boolean; answerMode?:'general_chat'|'rag'; createdAt?:string }
export interface ConversationDto { id:string; tenantId:string; userId:string; title:string; createdAt:string; updatedAt:string }
export interface ConversationDetailDto { conversation:ConversationDto; messages:Array<{id:string;conversationId:string;role:'user'|'assistant';content:string;citationsJson?:string;validationPassed?:boolean;createdAt:string}> }
export type AgentSseEvent = 'delta'|'citation'|'meta'|'done'|'error'
