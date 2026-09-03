import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AgentTasks from './tasks.vue'
import type * as agentTaskApi from '@/api/agentTask'

type EventHandler = (event: string, data: Record<string, unknown>) => void

const streamScripts: Array<Array<[string, Record<string, unknown>]>> = []

vi.mock('@/api/agentTask', () => ({
  listTasksApi: vi.fn().mockResolvedValue([]),
  getTaskApi: vi.fn(),
  getTaskAuditsApi: vi.fn().mockResolvedValue([]),
  getTaskStatsApi: vi.fn(),
  createTaskApi: vi.fn(),
  confirmTaskApi: vi.fn(),
  cancelTaskApi: vi.fn(),
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
  'a-card': { props: ['title'], template: '<div><slot name="title" />{{ title }}<slot /></div>' },
  'a-button': { props: ['loading'], template: '<button @click="$emit(\'click\', $event)"><slot /></button>' },
  'a-tag': { template: '<span><slot /></span>' },
  'a-alert': { template: '<div class="alert-stub"><slot name="message" />{{ message }}</div>', props: ['message'] },
  'a-empty': { template: '<div class="empty-stub">{{ description }}</div>', props: ['description'] },
  'a-list': {
    props: ['dataSource'],
    template:
      '<div><template v-for="item in dataSource || []" :key="item.id"><slot name="renderItem" :item="item" /></template><slot /></div>',
  },
  'a-list-item': { template: '<div @click="$emit(\'click\')"><slot /></div>' },
  'a-collapse': { template: '<div><slot /></div>' },
  'a-collapse-panel': { props: ['header'], template: '<div class="collapse-panel-stub">{{ header }}<slot /></div>' },
  'a-timeline': { template: '<div class="timeline-stub"><slot /></div>' },
  'a-timeline-item': { template: '<div class="timeline-item-stub"><slot /></div>' },
}

const mountTasks = async () => {
  const wrapper = mount(AgentTasks, { global: { plugins: [createPinia()], stubs } })
  await flushPromises()
  return wrapper
}

const doneTask = {
  id: 'task-done',
  goal: '查询工单摘要',
  status: 'DONE',
  resultText: '共 3 条工单',
  errorText: null,
  createdAt: '2026-09-03T08:00:00',
  updatedAt: '2026-09-03T08:01:00',
}
const failedTask = {
  id: 'task-failed',
  goal: '转派 VPN 工单',
  status: 'FAILED',
  resultText: null,
  errorText: '已达最大步数上限',
  createdAt: '2026-09-03T09:00:00',
  updatedAt: '2026-09-03T09:02:00',
}

const api = async () => await import('@/api/agentTask') as typeof agentTaskApi & {
  listTasksApi: ReturnType<typeof vi.fn>
  getTaskApi: ReturnType<typeof vi.fn>
  getTaskAuditsApi: ReturnType<typeof vi.fn>
  createTaskApi: ReturnType<typeof vi.fn>
}

describe('AgentTasks', () => {
  beforeEach(async () => {
    streamScripts.length = 0
    vi.clearAllMocks()
    const { listTasksApi, getTaskAuditsApi } = await api()
    vi.mocked(listTasksApi).mockResolvedValue([doneTask, failedTask])
    vi.mocked(getTaskAuditsApi).mockResolvedValue([])
  })

  it('lists tasks and filters by status', async () => {
    const wrapper = await mountTasks()

    expect(wrapper.findAll('.task-item')).toHaveLength(2)
    expect(wrapper.text()).toContain('查询工单摘要')

    await wrapper
      .findAll('.filter-chips button')
      .find((button) => button.text() === 'FAILED')!
      .trigger('click')

    expect(wrapper.findAll('.task-item')).toHaveLength(1)
    expect(wrapper.text()).toContain('转派 VPN 工单')
  })

  it('shows step timeline and audit details of a selected task', async () => {
    const { getTaskApi, getTaskAuditsApi } = await api()
    vi.mocked(getTaskApi).mockResolvedValue({
      task: doneTask,
      steps: [
        {
          id: 's1',
          seq: 1,
          kind: 'tool',
          toolName: 'ticket.search',
          status: 'DONE',
          argsJson: '{"keyword":"VPN"}',
          observationJson: '{"total":3}',
          createdAt: '2026-09-03T08:00:10',
        },
        {
          id: 's2',
          seq: 2,
          kind: 'summary',
          toolName: null,
          status: 'DONE',
          argsJson: null,
          observationJson: null,
          createdAt: '2026-09-03T08:00:20',
        },
      ],
    })
    vi.mocked(getTaskAuditsApi).mockResolvedValue([
      {
        id: 'aud-1',
        taskId: 'task-done',
        source: 'task',
        toolName: 'ticket.search',
        argsDigest: '{"keyword":"VPN"}',
        resultDigest: '{"total":3}',
        writeOperation: false,
        confirmed: null,
        allowed: true,
        createdAt: '2026-09-03T08:00:10',
      },
    ])

    const wrapper = await mountTasks()
    await wrapper.get('.task-item').trigger('click')
    await flushPromises()
    await vi.dynamicImportSettled()
    await flushPromises()

    expect(wrapper.findAll('.timeline-item-stub')).toHaveLength(2)
    expect(wrapper.text()).toContain('ticket.search')
    expect(wrapper.text()).toContain('审计明细')
    expect(wrapper.text()).toContain('放行')
    expect(wrapper.text()).toContain('共 3 条工单')
  })

  it('reruns a failed task with the same goal and streams the new task', async () => {
    const { createTaskApi } = await api()
    vi.mocked(createTaskApi).mockResolvedValue({ taskId: 'task-new' })
    streamScripts.push([
      ['plan', { steps: [{ seq: 1, tool: 'ticket.search', title: '检索工单' }] }],
      ['step', { seq: 1, tool: 'ticket.search', title: '检索工单', status: 'DONE', observation: '{"total":1}' }],
      ['result', { summary: '重跑完成' }],
    ])

    const wrapper = await mountTasks()
    await wrapper
      .findAll('.task-item')
      .find((item) => item.text().includes('转派 VPN 工单'))!
      .get('button')
      .trigger('click')
    await flushPromises()
    await vi.dynamicImportSettled()
    await flushPromises()

    expect(createTaskApi).toHaveBeenCalledWith('转派 VPN 工单')
    const { streamTaskEvents } = await api()
    expect(streamTaskEvents).toHaveBeenCalledWith('task-new', expect.any(Function), expect.anything())
    expect(wrapper.text()).toContain('检索工单')
    expect(wrapper.text()).toContain('重跑完成')
  })
})
