import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AgentConsole from './console.vue'

type EventHandler = (event: string, data: Record<string, unknown>) => void

const streamScripts: Array<Array<[string, Record<string, unknown>]>> = []
const confirmCalls: Array<{ id: string; confirmationId: string; approved: boolean }> = []

vi.mock('@/api/agentTask', () => ({
  createTaskApi: vi.fn().mockResolvedValue({ taskId: 'task-1' }),
  confirmTaskApi: vi.fn().mockImplementation(async (id: string, confirmationId: string, approved: boolean) => {
    confirmCalls.push({ id, confirmationId, approved })
    return { approved }
  }),
  cancelTaskApi: vi.fn(),
  getTaskApi: vi.fn(),
  listTasksApi: vi.fn().mockResolvedValue([]),
  streamTaskEvents: vi.fn().mockImplementation(async (_id: string, onEvent: EventHandler) => {
    for (const [event, data] of streamScripts.shift() ?? []) {
      onEvent(event, data)
    }
  }),
}))

vi.mock('md-editor-v3', () => ({
  MdPreview: defineComponent({
    name: 'MdPreview',
    props: { editorId: { type: String, required: true }, modelValue: { type: String, required: true } },
    template: '<div class="md-preview-component">{{ modelValue }}</div>',
  }),
}))

const stubs = {
  'a-card': { template: '<div><slot name="title" /><slot /></div>' },
  'a-textarea': {
    props: ['value'],
    template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
  },
  'a-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'a-steps': { template: '<div class="steps-stub"><slot /></div>' },
  'a-step': {
    props: ['title', 'description'],
    template: '<div class="step-stub">{{ title }} {{ description }}</div>',
  },
  'a-timeline': { template: '<div class="timeline-stub"><slot /></div>' },
  'a-timeline-item': { template: '<div class="timeline-item-stub"><slot /></div>' },
  'a-tag': { template: '<span><slot /></span>' },
  'a-collapse': { template: '<div><slot /></div>' },
  'a-collapse-panel': { template: '<div><slot /></div>' },
  'a-alert': { template: '<div class="alert-stub"><slot name="message" />{{ message }}</div>', props: ['message'] },
  'a-modal': {
    props: ['open'],
    template: '<div class="modal-stub" v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">确认</button><button class="modal-cancel" @click="$emit(\'cancel\')">拒绝</button></div>',
  },
  'a-list': { template: '<div><slot /></div>' },
  'a-list-item': { template: '<div><slot /></div>' },
  'a-empty': { template: '<div class="empty-stub" />' },
}

const mountConsole = async () => {
  const wrapper = mount(AgentConsole, { global: { plugins: [createPinia()], stubs } })
  await flushPromises()
  return wrapper
}

describe('AgentConsole', () => {
  beforeEach(() => {
    streamScripts.length = 0
    confirmCalls.length = 0
    vi.clearAllMocks()
  })

  it('runs a task and renders plan, steps and result', async () => {
    streamScripts.push([
      ['plan', { steps: [{ seq: 1, tool: 'ticket.search', title: '检索工单', why: '找到目标工单' }] }],
      ['step', { seq: 1, tool: 'ticket.search', title: '检索工单', status: 'DONE', observation: '{"total":1}' }],
      ['result', { summary: '任务完成报告' }],
    ])
    const wrapper = await mountConsole()

    await wrapper.get('textarea').setValue('查一下 VPN 工单')
    await wrapper.findAll('button').find((button) => button.text() === '启动任务')!.trigger('click')
    await flushPromises()
    await vi.dynamicImportSettled()
    await flushPromises()

    expect(wrapper.text()).toContain('检索工单')
    expect(wrapper.text()).toContain('找到目标工单')
    expect(wrapper.text()).toContain('任务完成报告')
    expect(wrapper.get('.md-preview-component').text()).toContain('任务完成报告')
  })

  it('asks for confirmation on confirm_required and sends approval', async () => {
    streamScripts.push([
      ['confirm_required', {
        seq: 1,
        tool: 'ticket.assign',
        title: '指派工单',
        confirmationId: 'confirm-123',
        preview: { ticketId: 'A-1', assignee: 'Jerry' },
      }],
    ])
    const wrapper = await mountConsole()

    await wrapper.get('textarea').setValue('指派工单')
    await wrapper.findAll('button').find((button) => button.text() === '启动任务')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('.modal-stub').text()).toContain('指派工单')
    expect(wrapper.get('.modal-stub').text()).toContain('Jerry')

    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(confirmCalls).toEqual([{ id: 'task-1', confirmationId: 'confirm-123', approved: true }])
  })

  it('renders error events as alert', async () => {
    streamScripts.push([
      ['error', { message: '已达最大步数上限' }],
    ])
    const wrapper = await mountConsole()

    await wrapper.get('textarea').setValue('会失败的任务')
    await wrapper.findAll('button').find((button) => button.text() === '启动任务')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('.alert-stub').text()).toContain('已达最大步数上限')
  })
})
