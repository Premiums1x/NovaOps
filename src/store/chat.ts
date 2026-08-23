import { defineStore } from 'pinia'
import { getConversationApi, getConversationsApi } from '@/api/agent'
import type { ChatMessageDto, CitationDto, ConversationDto } from '@/types/agent'

//citationsJson 是历史遗留的 JSON 字符串，一条脏数据只影响该条消息的引用展示，
//不能让整个会话打不开
const safeParseCitations = (raw: string): CitationDto[] | undefined => {
  try {
    return JSON.parse(raw) as CitationDto[]
  } catch {
    return undefined
  }
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversationId: '' as string,
    conversations: [] as ConversationDto[],
    messages: [] as ChatMessageDto[],
    loading: false,
    lastError: '',
  }),
  actions: {
    async loadConversations() {
      this.conversations = await getConversationsApi()
    },
    async openConversation(id: string) {
      const data = await getConversationApi(id)
      this.conversationId = id
      this.messages = data.messages.map((item) => ({
        id: item.id,
        role: item.role,
        content: item.content,
        citations: item.citationsJson ? safeParseCitations(item.citationsJson) : undefined,
        validationPassed: item.validationPassed,
        createdAt: item.createdAt,
      }))
    },
    newConversation() {
      this.conversationId = ''
      this.messages = []
      this.lastError = ''
    },
  },
})
