/* eslint-disable vue/one-component-per-file */
import { defineComponent, reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChatCore from './ChatCore.vue'

const store = reactive({
  messages: [] as Array<{ id: string; role: 'user' | 'assistant'; content: string }>,
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
})
