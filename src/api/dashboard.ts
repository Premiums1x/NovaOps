import dayjs from 'dayjs'
import { getTicketListApi } from '@/api/ticket'
import type { DashboardMetricsDto, DashboardMetricsQueryDto } from '@/types/dashboard'
import type { TicketPriority, TicketStatus } from '@/types/ticket'

const PAGE_SIZE = 100
const statusLabels: Record<TicketStatus, string> = {
  pending: '待处理',
  processing: '处理中',
  review: '待复核',
  done: '已完成',
}
const priorityLabels: Record<TicketPriority, string> = {
  urgent: '紧急',
  high: '高优先级',
  medium: '中优先级',
  low: '低优先级',
}

const round = (value: number) => Math.round(value * 10) / 10

const loadAllTickets = async (params: DashboardMetricsQueryDto) => {
  const query = { pageSize: PAGE_SIZE, startDate: params.startDate, endDate: params.endDate }
  const first = await getTicketListApi({ ...query, page: 1 })
  const pageCount = Math.ceil(first.total / PAGE_SIZE)
  if (pageCount <= 1) return first.list

  const remaining = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) =>
      getTicketListApi({ ...query, page: index + 2 })
    )
  )
  return [first, ...remaining].flatMap((page) => page.list)
}

export const getDashboardMetricsApi = async (
  params: DashboardMetricsQueryDto
): Promise<DashboardMetricsDto> => {
  const start = dayjs(params.startDate).startOf('day')
  const end = dayjs(params.endDate).endOf('day')
  const tickets = await loadAllTickets({
    startDate: start.toISOString(),
    endDate: end.toISOString(),
  })
  const doneTickets = tickets.filter((ticket) => ticket.status === 'done')
  const urgentCount = tickets.filter((ticket) => ticket.priority === 'urgent').length

  const dates: string[] = []
  const createdByDate = new Map<string, number>()
  const doneByDate = new Map<string, number>()
  for (let cursor = start; !cursor.isAfter(end, 'day'); cursor = cursor.add(1, 'day')) {
    dates.push(cursor.format('MM-DD'))
  }
  tickets.forEach((ticket) => {
    const date = dayjs(ticket.createdAt).format('MM-DD')
    createdByDate.set(date, (createdByDate.get(date) || 0) + 1)
    if (ticket.status === 'done') doneByDate.set(date, (doneByDate.get(date) || 0) + 1)
  })

  const statusCounts = new Map<TicketStatus, number>()
  const durationByPriority = new Map<TicketPriority, number[]>()
  tickets.forEach((ticket) => {
    statusCounts.set(ticket.status, (statusCounts.get(ticket.status) || 0) + 1)
    const duration = Math.max(
      0,
      dayjs(ticket.updatedAt).diff(dayjs(ticket.createdAt), 'minute') / 60
    )
    const values = durationByPriority.get(ticket.priority) || []
    values.push(duration)
    durationByPriority.set(ticket.priority, values)
  })

  const averageHours = doneTickets.length
    ? doneTickets.reduce(
        (sum, ticket) =>
          sum + Math.max(0, dayjs(ticket.updatedAt).diff(dayjs(ticket.createdAt), 'minute') / 60),
        0
      ) / doneTickets.length
    : 0

  return {
    range: { startDate: start.toISOString(), endDate: end.toISOString() },
    overview: {
      ticketTotal: tickets.length,
      doneRate: tickets.length ? round((doneTickets.length * 100) / tickets.length) : 0,
      avgHandleHours: round(averageHours),
      urgentRate: tickets.length ? round((urgentCount * 100) / tickets.length) : 0,
    },
    trend: {
      dates,
      created: dates.map((date) => createdByDate.get(date) || 0),
      closed: dates.map((date) => doneByDate.get(date) || 0),
    },
    categories: (Object.keys(statusLabels) as TicketStatus[]).map((status) => ({
      name: statusLabels[status],
      value: statusCounts.get(status) || 0,
    })),
    durations: (Object.keys(priorityLabels) as TicketPriority[]).map((priority) => {
      const values = durationByPriority.get(priority) || []
      return {
        name: priorityLabels[priority],
        hours: values.length
          ? round(values.reduce((sum, value) => sum + value, 0) / values.length)
          : 0,
      }
    }),
  }
}
