import { getAccessToken } from '@/utils/request/token'
import type { AgentSseEvent } from '@/types/agent'

const API_BASE_URL=import.meta.env.VITE_API_BASE_URL||'/api'
export const streamSse=async(path:string,body:unknown,onEvent:(event:AgentSseEvent,data:Record<string,unknown>)=>void,signal:AbortSignal,retry=true):Promise<void>=>{
  const response=await fetch(`${API_BASE_URL}${path}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${getAccessToken()}`},body:JSON.stringify(body),signal})
  if(response.status===401&&retry){const {useAuthStore}=await import('@/store/auth');const {pinia}=await import('@/store');await useAuthStore(pinia).refresh();return streamSse(path,body,onEvent,signal,false)}
  if(!response.ok||!response.body)throw new Error(response.status===401?'登录状态已失效':'问答服务连接失败')
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer=''
  while(true){const {done,value}=await reader.read();buffer+=decoder.decode(value,{stream:!done}).replace(/\r\n/g,'\n');let boundary=buffer.indexOf('\n\n');while(boundary>=0){const frame=buffer.slice(0,boundary);buffer=buffer.slice(boundary+2);let event='message',data='';for(const line of frame.split('\n')){if(line.startsWith('event:'))event=line.slice(6).trim();if(line.startsWith('data:'))data+=line.slice(5).trim()}if(data&&['delta','citation','meta','done','error'].includes(event))onEvent(event as AgentSseEvent,JSON.parse(data));boundary=buffer.indexOf('\n\n')}if(done)break}
}
