import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const store = reactive({
  conversationId: '',
  messages: [] as Array<Record<string, unknown>>,
  conversations: [],
  loading: false,
  lastError: '',
  loadConversations: vi.fn().mockResolvedValue(undefined),
})

const streamSse = vi.fn()

vi.mock('@/store/chat', () => ({ useChatStore: () => store }))
vi.mock('@/utils/sse', () => ({ streamSse: (...args: unknown[]) => streamSse(...args) }))
vi.mock('ant-design-vue', () => ({ message: { error: vi.fn() } }))

describe('useChat planning events', () => {
  beforeEach(() => {
    store.conversationId = ''
    store.messages = []
    store.loading = false
    store.lastError = ''
    store.loadConversations.mockClear()
    streamSse.mockReset()
  })

  it('stores the plan, updates a step by action, and folds it on done', async () => {
    streamSse.mockImplementation(async (_path, _body, onEvent) => {
      onEvent('plan', {
        conversationId: 'conv-1',
        steps: [
          { action: 'search_kb', label: '检索知识库', reason: '查找资料', status: 'pending' },
          { action: 'answer', label: '生成回答', reason: '组织答案', status: 'pending' },
        ],
      })
      onEvent('step', { action: 'search_kb', status: 'running' })
      onEvent('step', { action: 'search_kb', status: 'done', payload: { count: 3 } })
      onEvent('done', {})
    })
    const { useChat } = await import('./useChat')

    await useChat().send('如何使用当前知识库？')

    const assistant = store.messages[1]!
    expect(assistant.steps).toEqual([
      {
        action: 'search_kb',
        label: '检索知识库',
        reason: '查找资料',
        status: 'done',
        payload: { count: 3 },
      },
      { action: 'answer', label: '生成回答', reason: '组织答案', status: 'pending' },
    ])
    expect(assistant.reasoningExpanded).toBe(false)
  })
})
