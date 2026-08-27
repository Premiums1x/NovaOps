import { onBeforeUnmount, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useChatStore } from '@/store/chat'
import { streamSse } from '@/utils/sse'
import type { AgentPlanStepDto, ChatMessageDto, CitationDto } from '@/types/agent'

// 模块级共享：独立对话页与全局浮窗同时挂载时，"停止"按钮必须能
// 中断当前真正在跑的那一条流，而不是各自实例里那个已失效的引用
let sharedController: AbortController | undefined

export const useChat = () => {
  const store = useChatStore()

  const send = async (content: string) => {
    const question = content.trim()
    if (!question || store.loading) return

    store.loading = true
    store.lastError = ''
    store.messages.push({ id: `local-user-${Date.now()}`, role: 'user', content: question })
    //必须先包一层 reactive 再 push：push 之后如果继续改闭包里的原始对象，
    //Vue 感知不到变更，delta 增量不会触发界面更新，流式效果失效
    const assistant = reactive<ChatMessageDto>({
      id: `local-ai-${Date.now()}`,
      role: 'assistant',
      content: '',
      citations: [] as CitationDto[],
      steps: [] as AgentPlanStepDto[],
      reasoningExpanded: false,
    })
    store.messages.push(assistant)
    sharedController = new AbortController()

    try {
      await streamSse(
        '/agent/chat',
        { conversationId: store.conversationId || undefined, content: question },
        (event, data) => {
          if (typeof data.conversationId === 'string') store.conversationId = data.conversationId
          if (event === 'plan' && Array.isArray(data.steps)) {
            assistant.steps = data.steps as AgentPlanStepDto[]
            assistant.reasoningExpanded = true
          }
          if (event === 'step' && typeof data.action === 'string' && typeof data.status === 'string') {
            const update = data as unknown as Partial<AgentPlanStepDto> & Pick<AgentPlanStepDto, 'action'|'status'>
            const existing = assistant.steps?.find((step) => step.action === update.action)
            if (existing) {
              assistant.steps = assistant.steps?.map((step) =>
                step.action === update.action ? { ...step, ...update } : step,
              )
            }
          }
          if (event === 'delta') assistant.content += String(data.content || '')
          if (event === 'citation') assistant.citations = (data.citations || []) as CitationDto[]
          if (event === 'meta') {
            if (typeof data.validationPassed === 'boolean') {
              assistant.validationPassed = data.validationPassed
            }
          }
          if (event === 'done') assistant.reasoningExpanded = false
          if (event === 'error') throw new Error(String(data.message || '问答失败'))
        },
        sharedController.signal,
      )
      await store.loadConversations()
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        assistant.content = assistant.content || '已停止生成。'
      } else {
        store.lastError = (error as Error).message
        assistant.content = assistant.content || '服务暂时不可用，请稍后重试。'
        message.error(store.lastError)
      }
      assistant.steps = assistant.steps?.map((step) =>
        step.status === 'running' ? { ...step, status: 'failed' } : step,
      )
      assistant.reasoningExpanded = false
    } finally {
      store.loading = false
      sharedController = undefined
    }
  }

  const stop = () => sharedController?.abort()
  onBeforeUnmount(stop)
  return { store, send, stop }
}
