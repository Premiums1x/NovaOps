export interface DashboardOverviewDto {
  ticketTotal: number
  doneRate: number
  avgHandleHours: number
  urgentRate: number
}

export interface DashboardTrendDto {
  dates: string[]
  created: number[]
  closed: number[]
}

export interface DashboardCategoryItemDto {
  name: string
  value: number
}

export interface DashboardDurationItemDto {
  name: string
  hours: number
}

export interface DashboardMetricsDto {
  range: {
    startDate: string
    endDate: string
  }
  overview: DashboardOverviewDto
  trend: DashboardTrendDto
  categories: DashboardCategoryItemDto[]
  durations: DashboardDurationItemDto[]
}

export interface DashboardMetricsQueryDto {
  startDate?: string
  endDate?: string
}
