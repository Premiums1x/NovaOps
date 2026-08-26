import dayjs from 'dayjs'
import type { PageResult } from '@/types/api'
import type {
  CreateCommentDto,
  CreateTicketDto,
  TicketActionDto,
  TicketActionType,
  TicketAttachmentDto,
  TicketCommentDto,
  TicketDetailDto,
  TicketListItemDto,
  TicketListQueryDto,
  TicketPriority,
  TicketStatus,
  TicketTimelineItemDto,
  UpdateTicketDto,
  UploadAttachmentDto,
} from '@/types/ticket'
import type { RelatedTicketDto } from '@/types/asset'

const statusFlow: TicketStatus[] = ['pending', 'processing', 'review', 'done']
const priorities: TicketPriority[] = ['low', 'medium', 'high', 'urgent']
const assignees = ['Tom', 'Jerry', 'Alice', 'Nova Team']

const tickets: TicketDetailDto[] = []

const uuid = (prefix: string) => {
  return `${prefix}_${Math.random().toString(36).slice(2, 8)}_${Date.now()}`
}

const formatTicketId = (index: number) => {
  return `A-TICKET-${String(index).padStart(4, '0')}`
}

const createTimelineItem = (payload: Partial<TicketTimelineItemDto>): TicketTimelineItemDto => {
  return {
    id: payload.id || uuid('tl'),
    action: payload.action || 'update',
    operator: payload.operator || 'system',
    remark: payload.remark,
    fromStatus: payload.fromStatus,
    toStatus: payload.toStatus,
    createdAt: payload.createdAt || dayjs().toISOString(),
  }
}

const seedTickets = () => {
  if (tickets.length > 0) {
    return
  }

  for (let index = 0; index < 24; index += 1) {
    const status = statusFlow[index % statusFlow.length] || 'pending'
    const priority = priorities[index % priorities.length] || 'medium'
    const createdAt = dayjs().subtract(index, 'day')
    const updatedAt = createdAt.add((index % 6) + 1, 'hour')
    const ticketId = formatTicketId(index + 1)
    const assignee = assignees[index % assignees.length] || 'Nova Team'
    tickets.push({
      id: ticketId,
      title: `A 网络与终端巡检异常 #${index + 1}`,
      description: `巡检发现异常指标，需排查交换机/终端策略。工单编号 ${ticketId}。`,
      status,
      priority,
      assignee,
      creator: 'admin',
      createdAt: createdAt.toISOString(),
      updatedAt: updatedAt.toISOString(),
      dueDate: createdAt.add(3, 'day').toISOString(),
      assetIds: [`ASSET-${index + 1}`, `ASSET-${index + 101}`],
      timeline: [
        createTimelineItem({
          action: 'create',
          operator: 'admin',
          toStatus: 'pending',
          remark: '创建工单',
          createdAt: createdAt.toISOString(),
        }),
        createTimelineItem({
          action: 'advance',
          operator: 'Tom',
          fromStatus: 'pending',
          toStatus: status === 'pending' ? 'pending' : 'processing',
          remark: '首次响应',
          createdAt: createdAt.add(2, 'hour').toISOString(),
        }),
      ],
      comments: [
        {
          id: uuid('cm'),
          author: 'Tom',
          content: '收到，正在排查日志。',
          createdAt: createdAt.add(3, 'hour').toISOString(),
        },
        {
          id: uuid('cm'),
          author: 'Alice',
          content: '已补充现场截图。',
          createdAt: createdAt.add(6, 'hour').toISOString(),
        },
      ],
      attachments: [],
    })
  }
}

seedTickets()

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const findTicket = (ticketId: string) => {
  return tickets.find((item) => item.id === ticketId) || null
}

const toListItem = (ticket: TicketDetailDto): TicketListItemDto => {
  return {
    id: ticket.id,
    title: ticket.title,
    status: ticket.status,
    priority: ticket.priority,
    assignee: ticket.assignee,
    creator: ticket.creator,
    createdAt: ticket.createdAt,
    updatedAt: ticket.updatedAt,
    assetIds: ticket.assetIds,
  }
}

export const queryTickets = (
  query: TicketListQueryDto
): PageResult<TicketListItemDto> => {
  const page = Math.max(Number(query.page || 1), 1)
  const pageSize = Math.max(Number(query.pageSize || 10), 1)

  let filtered = [...tickets]

  if (query.status) {
    filtered = filtered.filter((ticket) => ticket.status === query.status)
  }
  if (query.priority) {
    filtered = filtered.filter((ticket) => ticket.priority === query.priority)
  }
  if (query.keyword) {
    const keyword = query.keyword.toLowerCase()
    filtered = filtered.filter((ticket) => {
      return (
        ticket.title.toLowerCase().includes(keyword) ||
        ticket.description.toLowerCase().includes(keyword) ||
        ticket.id.toLowerCase().includes(keyword)
      )
    })
  }
  if (query.startDate && query.endDate) {
    const start = dayjs(query.startDate)
    const end = dayjs(query.endDate)
    filtered = filtered.filter((ticket) => {
      const createdAt = dayjs(ticket.createdAt)
      return (createdAt.isAfter(start) || createdAt.isSame(start)) && (createdAt.isBefore(end) || createdAt.isSame(end))
    })
  }

  const sorted = filtered.sort((a, b) => dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf())
  const total = sorted.length
  const startIndex = (page - 1) * pageSize
  const list = sorted.slice(startIndex, startIndex + pageSize).map(toListItem)

  return {
    list: clone(list),
    page,
    pageSize,
    total,
  }
}

export const getTicketDetail = (ticketId: string) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  return clone(ticket)
}

export const createTicket = (
  username: string,
  payload: CreateTicketDto
): TicketDetailDto => {
  const now = dayjs().toISOString()
  const nextId = formatTicketId(tickets.length + 1)
  const ticket: TicketDetailDto = {
    id: nextId,
    title: payload.title,
    description: payload.description,
    status: 'pending',
    priority: payload.priority,
    assignee: payload.assignee || 'Unassigned',
    creator: username,
    createdAt: now,
    updatedAt: now,
    dueDate: payload.dueDate,
    assetIds: payload.assetIds || [],
    attachments: [],
    comments: [],
    timeline: [
      createTimelineItem({
        action: 'create',
        operator: username,
        toStatus: 'pending',
        remark: '新建工单',
        createdAt: now,
      }),
    ],
  }
  tickets.unshift(ticket)
  return clone(ticket)
}

export const updateTicket = (
  ticketId: string,
  username: string,
  payload: UpdateTicketDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  ticket.title = payload.title || ticket.title
  ticket.description = payload.description || ticket.description
  ticket.priority = payload.priority || ticket.priority
  ticket.assignee = payload.assignee || ticket.assignee
  ticket.dueDate = payload.dueDate || ticket.dueDate
  if (payload.assetIds) {
    ticket.assetIds = payload.assetIds
  }
  ticket.updatedAt = dayjs().toISOString()
  ticket.timeline.unshift(
    createTimelineItem({
      action: 'update',
      operator: username,
      remark: '更新工单信息',
      createdAt: ticket.updatedAt,
    })
  )
  return clone(ticket)
}

const ILLEGAL_TRANSITION_MSG: Record<string, string> = {
  assign: '已完成的工单不可再指派',
  transfer: '已完成的工单不可再转派',
  advance: '仅处理中的工单可提交复核',
  approve: '仅待复核的工单可复核通过',
  reject: '仅待复核的工单可驳回',
  close: '待处理的工单不可直接关闭，或工单已关闭',
}

export const actionTicket = (
  ticketId: string,
  username: string,
  payload: TicketActionDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const previousStatus = ticket.status
  const now = dayjs().toISOString()

  // 状态机前置校验（与后端 TicketService.validateTransition 保持一致）
  const validate = (action: TicketActionType, status: TicketStatus): boolean => {
    switch (action) {
      case 'assign':
      case 'transfer':
        return status !== 'done'
      case 'advance':
        return status === 'processing'
      case 'approve':
        return status === 'review'
      case 'reject':
        return status === 'review'
      case 'close':
        return status === 'processing' || status === 'review'
      default:
        return false
    }
  }
  if (!validate(payload.action, ticket.status)) {
    throw new Error(ILLEGAL_TRANSITION_MSG[payload.action] || '非法状态流转')
  }

  switch (payload.action) {
    case 'assign':
      ticket.assignee = payload.assignee || ticket.assignee
      if (ticket.status === 'pending') {
        ticket.status = 'processing'
      }
      break
    case 'transfer':
      ticket.assignee = payload.targetUser || ticket.assignee
      if (ticket.status === 'review') {
        ticket.status = 'processing'
      }
      break
    case 'advance':
      ticket.status = 'review' // processing → review（提交复核）
      break
    case 'approve':
      ticket.status = 'done' // review → done（复核通过）
      break
    case 'reject':
      ticket.status = 'processing' // review → processing（驳回）
      break
    case 'close':
      ticket.status = 'done' // processing/review → done（关闭）
      break
    default:
      break
  }

  ticket.updatedAt = now
  ticket.timeline.unshift(
    createTimelineItem({
      action: payload.action,
      operator: username,
      remark: payload.remark || undefined,
      fromStatus: previousStatus,
      toStatus: ticket.status,
      createdAt: now,
    })
  )
  return clone(ticket)
}

export const listTicketComments = (ticketId: string): TicketCommentDto[] | null => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  return clone(ticket.comments.sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf()))
}

export const createTicketComment = (
  ticketId: string,
  username: string,
  payload: CreateCommentDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const comment: TicketCommentDto = {
    id: uuid('cm'),
    author: username,
    content: payload.content,
    createdAt: dayjs().toISOString(),
  }
  ticket.comments.unshift(comment)
  ticket.updatedAt = dayjs().toISOString()
  return clone(comment)
}

export const uploadTicketAttachment = (
  ticketId: string,
  payload: UploadAttachmentDto
): TicketAttachmentDto | null => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const attachment: TicketAttachmentDto = {
    id: uuid('att'),
    name: payload.filename,
    size: payload.size,
    url: `/mock-attachments/${ticketId}/${encodeURIComponent(payload.filename)}`,
    createdAt: dayjs().toISOString(),
  }
  ticket.attachments.unshift(attachment)
  ticket.updatedAt = dayjs().toISOString()
  return clone(attachment)
}

export const listRelatedTicketsByAsset = (assetId: string): RelatedTicketDto[] => {
  const related = tickets
    .filter((ticket) => ticket.assetIds.includes(assetId))
    .sort((a, b) => dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf())
    .slice(0, 8)
    .map((ticket) => ({
      id: ticket.id,
      title: ticket.title,
      status: ticket.status,
      priority: ticket.priority,
      assignee: ticket.assignee,
    }))
  return clone(related)
}
