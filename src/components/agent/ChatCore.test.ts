/* eslint-disable vue/one-component-per-file */
import { defineComponent, reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChatCore from './ChatCore.vue'

const store = reactive({
  messages: [] as Array<Record<string, unknown> & { id: string; role: 'user' | 'assistant'; content: string }>,
  loading: false,
})

vi.mock('@/composables/useChat', () => ({
  useChat: () => ({ store, send: vi.fn(), stop: vi.fn() }),
}))

vi.mock('vue-element-plus-x', async () => {
  const { defineComponent } = await import('vue')
  return {
    BubbleList: defineComponent({
      props: { list: { type: Array, required: true } },
      template: '<div class="bubble-list-stub"><div v-for="item in list" :key="item.key" class="message" :class="item.role"><slot name="avatar" :item="item"/><slot name="content" :item="item"/><slot name="footer" :item="item"/></div></div>',
    }),
    Welcome: defineComponent({ template: '<div class="welcome-stub" />' }),
    XSender: defineComponent({ template: '<div class="x-sender-stub" />' }),
  }
})

vi.mock('md-editor-v3', () => ({
  MdPreview: defineComponent({
    name: 'MdPreview',
    props: { editorId: { type: String, required: true }, modelValue: { type: String, required: true } },
    template: '<div class="md-preview-component">{{ modelValue }}</div>',
  }),
}))

describe('ChatCore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    store.messages = [
      { id: 'user-1', role: 'user', content: '第一行\n第二行 **不应解析**' },
      { id: 'assistant-1', role: 'assistant', content: '这是 **Markdown** 回复' },
    ]
  })

  it('renders user content as plain text and assistant content with the markdown preview', async () => {
    const wrapper = mount(ChatCore, {
      global: {
        stubs: {
          'a-tag': true,
          'a-alert': true,
          UserOutlined: true,
          RobotOutlined: true,
        },
      },
    })
    await vi.dynamicImportSettled()
    await flushPromises()

    const userMessage = wrapper.get('.message.user')
    expect(userMessage.get('.user-message').text()).toBe('第一行\n第二行 **不应解析**')
    expect(userMessage.find('.markdown-preview').exists()).toBe(false)
    expect(wrapper.get('.message.assistant').get('.md-preview-component').text()).toBe('这是 **Markdown** 回复')
    expect(wrapper.find('.x-sender-stub').exists()).toBe(true)
  })

  it('renders the real evidence chunk returned by the backend', async () => {
    store.messages = [{
      id: 'assistant-evidence',
      role: 'assistant',
      content: '使用 pnpm 安装。',
      route: 'RAG',
      retrievalExecuted: true,
      retrievedCount: 2,
      validatedCount: 1,
      validationStatus: 'PASSED',
      citations: [{ index: 1, documentId: 'doc-1', documentName: 'Guide.md', chunkId: 'chunk-12', content: 'pnpm add package', score: 0.86 }],
      evidence: [{ index: 1, documentId: 'doc-1', documentName: 'Guide.md', chunkId: 'chunk-12', content: 'pnpm add package', score: 0.86 }],
    }]
    const wrapper = mount(ChatCore, {
      global: {
        stubs: {
          'a-tag': { template: '<span><slot /></span>' },
          'a-alert': true,
          'a-collapse': { template: '<div><slot /></div>' },
          'a-collapse-panel': { template: '<div><slot /></div>' },
          UserOutlined: true,
          RobotOutlined: true,
        },
      },
    })
    await vi.dynamicImportSettled()
    await flushPromises()

    expect(wrapper.text()).toContain('知识检索')
    expect(wrapper.text()).toContain('chunk-12')
    expect(wrapper.text()).toContain('pnpm add package')
    expect(wrapper.text()).toContain('检索 2 条 · 有效 1 条')
  })

  it('renders a collapsible execution summary for assistant plan steps', async () => {
    store.messages = [
      {
        id: 'assistant-plan',
        role: 'assistant',
        content: '回答内容',
        reasoningExpanded: true,
        steps: [
          { action: 'search_kb', label: '检索知识库', reason: '查找资料', status: 'done', payload: { count: 3 } },
          { action: 'answer', label: '生成回答', reason: '组织答案', status: 'running' },
        ],
      },
    ] as Array<Record<string, unknown> & { id: string; role: 'user' | 'assistant'; content: string }>
    const wrapper = mount(ChatCore, {
      global: {
        stubs: {
          'a-collapse': { template: '<div class="collapse-stub"><slot /></div>' },
          'a-collapse-panel': { template: '<div><slot name="header"/><slot /></div>' },
          'a-steps': {
            props: ['items'],
            template: '<div class="steps-stub"><div v-for="item in items" :key="item.key">{{ item.title }} {{ item.description }}</div></div>',
          },
          'a-tag': true,
          'a-alert': true,
          UserOutlined: true,
          RobotOutlined: true,
        },
      },
    })
    await vi.dynamicImportSettled()
    await flushPromises()

    expect(wrapper.get('.reasoning-panel').text()).toContain('执行过程')
    expect(wrapper.get('.steps-stub').text()).toContain('检索知识库')
    expect(wrapper.get('.steps-stub').text()).toContain('检索到 3 条资料')
  })
})
