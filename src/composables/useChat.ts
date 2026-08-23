import { onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import { useChatStore } from '@/store/chat'
import { streamSse } from '@/utils/sse'
import type { CitationDto } from '@/types/agent'
export const useChat=()=>{
  const store=useChatStore();let controller:AbortController|undefined
  const send=async(content:string)=>{
    const question=content.trim();if(!question||store.loading)return
    store.loading=true;store.lastError='';store.messages.push({id:`local-user-${Date.now()}`,role:'user',content:question});const assistant={id:`local-ai-${Date.now()}`,role:'assistant' as const,content:'',citations:[] as CitationDto[]};store.messages.push(assistant);controller=new AbortController()
    try{await streamSse('/agent/chat',{conversationId:store.conversationId||undefined,content:question},(event,data)=>{if(typeof data.conversationId==='string')store.conversationId=data.conversationId;if(event==='delta')assistant.content+=String(data.content||'');if(event==='citation')assistant.citations=(data.citations||[]) as CitationDto[];if(event==='meta')assistant.validationPassed=Boolean(data.validationPassed);if(event==='error')throw new Error(String(data.message||'问答失败'))},controller.signal);await store.loadConversations()}
    catch(error){if((error as Error).name!=='AbortError'){store.lastError=(error as Error).message;assistant.content=assistant.content||'服务暂时不可用，请稍后重试。';message.error(store.lastError)}}finally{store.loading=false;controller=undefined}
  }
  const stop=()=>controller?.abort();onBeforeUnmount(stop);return{store,send,stop}
}
