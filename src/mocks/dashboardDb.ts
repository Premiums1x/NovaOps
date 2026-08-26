import dayjs from 'dayjs'
import type { DashboardMetricsDto } from '@/types/dashboard'

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const hashSeed = (tenantId: string, day: string) => {
  const raw = `${tenantId}-${day}`
  let hash = 0
  for (let i = 0; i < raw.length; i += 1) {
    hash = (hash * 31 + raw.charCodeAt(i)) % 9973
  }
  return hash
}

const normalizeRange = (startDate?: string, endDate?: string) => {
  const end = endDate ? dayjs(endDate) : dayjs().endOf('day')
  const start = startDate ? dayjs(startDate) : end.subtract(6, 'day').startOf('day')
  const safeEnd = end.isBefore(start) ? start.endOf('day') : end
  return {
    start: start.startOf('day'),
    end: safeEnd.endOf('day'),
  }
}

const buildDateAxis = (start: dayjs.Dayjs, end: dayjs.Dayjs) => {
  const dates: string[] = []
  let cursor = start.clone()
  while (cursor.isBefore(end) || cursor.isSame(end, 'day')) {
    dates.push(cursor.format('MM-DD'))
    cursor = cursor.add(1, 'day')
  }
  return dates
}

export const buildDashboardMetrics = (
  tenantId: string,
  startDate?: string,
  endDate?: string
): DashboardMetricsDto => {
  const { start, end } = normalizeRange(startDate, endDate)
  const dates = buildDateAxis(start, end)

  const created: number[] = []
  const closed: number[] = []

  dates.forEach((day) => {
    const seed = hashSeed(tenantId, day)
    const createdCount = 18 + (seed % 17)
    const closeBase = createdCount - 4 + (seed % 9)
    const closedCount = clamp(closeBase, 8, 42)
    created.push(createdCount)
    closed.push(closedCount)
  })

  const totalCreated = created.reduce((sum, value) => sum + value, 0)
  const totalClosed = closed.reduce((sum, value) => sum + value, 0)
  const doneRate = totalCreated > 0 ? Math.round((totalClosed / totalCreated) * 1000) / 10 : 0
  const avgHandleHours = Math.round((12 + (hashSeed(tenantId, 'avg') % 26) * 0.35) * 10) / 10
  const urgentRate = Math.round((8 + (hashSeed(tenantId, 'urgent') % 11) * 0.7) * 10) / 10

  const categories = [
    { name: '故障工单', value: 32 + (hashSeed(tenantId, 'fault') % 16) },
    { name: '需求工单', value: 24 + (hashSeed(tenantId, 'feature') % 14) },
    { name: '巡检工单', value: 16 + (hashSeed(tenantId, 'inspection') % 12) },
    { name: '安全工单', value: 10 + (hashSeed(tenantId, 'security') % 8) },
  ]

  const durations = [
    { name: '网络类', hours: Math.round((8 + (hashSeed(tenantId, 'net') % 10) * 0.8) * 10) / 10 },
    { name: '终端类', hours: Math.round((6 + (hashSeed(tenantId, 'client') % 9) * 0.9) * 10) / 10 },
    { name: '系统类', hours: Math.round((9 + (hashSeed(tenantId, 'system') % 10) * 0.9) * 10) / 10 },
    { name: '权限类', hours: Math.round((4 + (hashSeed(tenantId, 'auth') % 8) * 0.7) * 10) / 10 },
    { name: '资产类', hours: Math.round((5 + (hashSeed(tenantId, 'asset') % 9) * 0.8) * 10) / 10 },
  ]

  return {
    range: {
      startDate: start.toISOString(),
      endDate: end.toISOString(),
    },
    overview: {
      ticketTotal: totalCreated,
      doneRate,
      avgHandleHours,
      urgentRate,
    },
    trend: {
      dates,
      created,
      closed,
    },
    categories,
    durations,
  }
}
