import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setupAntd } from '@/plugins/antd'
import { getUserOptionsApi } from '@/api/auth'
import { getAssetBatchApi } from '@/api/asset'
import {
  getTicketCommentsApi,
  getTicketDetailApi,
  getTicketListApi,
  ticketActionApi,
} from '@/api/ticket'
import type { TicketDetailDto, TicketListItemDto } from '@/types/ticket'
import TicketDetail from './detail.vue'
import TicketList from './list.vue'

vi.mock('@/api/auth', () => ({
  getUserOptionsApi: vi.fn(),
}))

vi.mock('@/api/asset', () => ({
  getAssetBatchApi: vi.fn(),
}))

vi.mock('@/api/ticket', () => ({
  getTicketListApi: vi.fn(),
  getTicketDetailApi: vi.fn(),
  createTicketApi: vi.fn(),
  updateTicketApi: vi.fn(),
  ticketActionApi: vi.fn(),
  getTicketCommentsApi: vi.fn(),
  createTicketCommentApi: vi.fn(),
  uploadTicketAttachmentApi: vi.fn(),
}))

const ticket: TicketDetailDto = {
  id: 'A-TICKET-0001',
  title: '网络故障',
  description: '交换机端口异常',
  status: 'processing',
  priority: 'high',
  assigneeId: 'u-staff',
  assigneeName: 'Support Staff',
  creatorId: 'u-admin',
  creatorName: 'System Admin',
  createdAt: '2026-08-28T08:00:00Z',
  updatedAt: '2026-08-28T09:00:00Z',
  assetIds: [],
  timeline: [
    {
      id: 'tl-1',
      action: 'assign',
      operatorId: 'u-admin',
      operatorName: 'System Admin',
      fromStatus: 'pending',
      toStatus: 'processing',
      createdAt: '2026-08-28T09:00:00Z',
    },
  ],
  comments: [],
  attachments: [],
}

const antdPlugin = { install: setupAntd }
const globalOptions = {
  plugins: [antdPlugin],
  stubs: { Permission: { template: '<div><slot /></div>' } },
}

const mountPage = async (path: string, component: typeof TicketList | typeof TicketDetail) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path, component }],
  })
  const target = path.replace(':id', ticket.id)
  await router.push(target)
  await router.isReady()
  return mount(component, {
    global: { ...globalOptions, plugins: [antdPlugin, router] },
  })
}

describe('ticket pages person contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getUserOptionsApi).mockResolvedValue([
      { id: 'u-staff', username: 'staff', displayName: 'Support Staff' },
      { id: 'u-jerry', username: 'jerry', displayName: 'Jerry' },
    ])
    vi.mocked(getTicketListApi).mockResolvedValue({
      list: [ticket as TicketListItemDto],
      page: 1,
      pageSize: 10,
      total: 1,
    })
    vi.mocked(getTicketDetailApi).mockResolvedValue(ticket)
    vi.mocked(getTicketCommentsApi).mockResolvedValue([])
    vi.mocked(getAssetBatchApi).mockResolvedValue([])
    vi.mocked(ticketActionApi).mockResolvedValue(ticket)
  })

  it('renders person names and submits assigneeId from the list', async () => {
    const wrapper = await mountPage('/ticket/list', TicketList)
    await flushPromises()

    expect(wrapper.text()).toContain('Support Staff')
    expect(wrapper.text()).toContain('System Admin')

    const vm = wrapper.vm as unknown as {
      openAssignModal: (record: TicketListItemDto) => void
      assignForm: { assigneeId: string; remark: string }
      submitAssign: () => Promise<void>
    }
    vm.openAssignModal(ticket)
    vm.assignForm.assigneeId = 'u-jerry'
    await vm.submitAssign()

    expect(ticketActionApi).toHaveBeenCalledWith(ticket.id, {
      action: 'assign',
      assigneeId: 'u-jerry',
      remark: '列表页指派',
    })
  })

  it('renders person names and submits assigneeId for transfer in detail', async () => {
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail)
    await flushPromises()

    expect(wrapper.text()).toContain('Support Staff')
    expect(wrapper.text()).toContain('System Admin')

    const vm = wrapper.vm as unknown as {
      actionForm: { assigneeId: string; remark: string }
      doAction: (action: 'transfer') => Promise<void>
    }
    vm.actionForm.assigneeId = 'u-jerry'
    vm.actionForm.remark = '轮班转派'
    await vm.doAction('transfer')

    expect(ticketActionApi).toHaveBeenCalledWith(ticket.id, {
      action: 'transfer',
      assigneeId: 'u-jerry',
      remark: '轮班转派',
    })
  })
})
