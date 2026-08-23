import { defineStore } from 'pinia'
import { getConversationApi,getConversationsApi } from '@/api/agent'
import type { ChatMessageDto,CitationDto,ConversationDto } from '@/types/agent'
export const useChatStore=defineStore('chat',{
  state:()=>({conversationId:'' as string,conversations:[] as ConversationDto[],messages:[] as ChatMessageDto[],loading:false,lastError:''}),
  actions:{
    async loadConversations(){this.conversations=await getConversationsApi()},
    async openConversation(id:string){const data=await getConversationApi(id);this.conversationId=id;this.messages=data.messages.map(item=>({id:item.id,role:item.role,content:item.content,citations:item.citationsJson?JSON.parse(item.citationsJson) as CitationDto[]:undefined,validationPassed:item.validationPassed,createdAt:item.createdAt}))},
    newConversation(){this.conversationId='';this.messages=[];this.lastError=''},
  }
})
