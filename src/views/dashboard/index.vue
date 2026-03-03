<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TitleComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { BarSeriesOption, LineSeriesOption, PieSeriesOption } from 'echarts/charts'
import { getDashboardMetricsApi } from '@/api/dashboard'
import { useAuthStore } from '@/store/auth'
import type { DashboardMetricsDto } from '@/types/dashboard'

defineOptions({
  name: 'Dashboard',
})

use([
  CanvasRenderer,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  LineChart,
  PieChart,
  BarChart,
])

type ECOption = ComposeOption<
  | TitleComponentOption
  | TooltipComponentOption
  | GridComponentOption
  | LegendComponentOption
  | LineSeriesOption
  | PieSeriesOption
  | BarSeriesOption
>

const authStore = useAuthStore()
const loading = ref(false)
const metrics = ref<DashboardMetricsDto | null>(null)
const rangeValue = ref<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day').startOf('day'), dayjs().endOf('day')])

const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()

const trendChart = ref<ECharts>()
const pieChart = ref<ECharts>()
const barChart = ref<ECharts>()

const greeting = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'User'
  return `Hi, ${name}`
})

const applyQuickRange = (days: number) => {
  rangeValue.value = [dayjs().subtract(days - 1, 'day').startOf('day'), dayjs().endOf('day')]
}

const createTrendOption = (data: DashboardMetricsDto): ECOption => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['新增工单', '完成工单'] },
  grid: { left: 40, right: 20, top: 30, bottom: 24 },
  xAxis: {
    type: 'category',
    data: data.trend.dates,
    boundaryGap: false,
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { type: 'dashed' } },
  },
  series: [
    {
      name: '新增工单',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.08 },
      data: data.trend.created,
      color: '#1677ff',
    },
    {
      name: '完成工单',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.08 },
      data: data.trend.closed,
      color: '#52c41a',
    },
  ],
})

const createPieOption = (data: DashboardMetricsDto): ECOption => ({
  tooltip: { trigger: 'item' },
  legend: {
    orient: 'vertical',
    left: 'left',
    top: 'middle',
  },
  series: [
    {
      type: 'pie',
      radius: ['36%', '65%'],
      center: ['64%', '50%'],
      label: { formatter: '{b}\n{d}%' },
      data: data.categories,
    },
  ],
})

const createBarOption = (data: DashboardMetricsDto): ECOption => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 24, bottom: 24 },
  xAxis: {
    type: 'value',
    splitLine: { lineStyle: { type: 'dashed' } },
  },
  yAxis: {
    type: 'category',
    data: data.durations.map((item) => item.name),
  },
  series: [
    {
      type: 'bar',
      data: data.durations.map((item) => item.hours),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: '#fa8c16',
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{c}h',
      },
    },
  ],
})

const resizeAllCharts = () => {
  trendChart.value?.resize()
  pieChart.value?.resize()
  barChart.value?.resize()
}

const renderCharts = () => {
  if (!metrics.value) {
    return
  }
  trendChart.value?.setOption(createTrendOption(metrics.value), true)
  pieChart.value?.setOption(createPieOption(metrics.value), true)
  barChart.value?.setOption(createBarOption(metrics.value), true)
  resizeAllCharts()
}

const initCharts = async () => {
  await nextTick()
  if (!trendChart.value && trendRef.value) {
    trendChart.value = init(trendRef.value)
  }
  if (!pieChart.value && pieRef.value) {
    pieChart.value = init(pieRef.value)
  }
  if (!barChart.value && barRef.value) {
    barChart.value = init(barRef.value)
  }
  renderCharts()
}

const fetchMetrics = async () => {
  const [start, end] = rangeValue.value
  loading.value = true
  try {
    metrics.value = await getDashboardMetricsApi({
      startDate: start.toISOString(),
      endDate: end.toISOString(),
    })
    await initCharts()
  } finally {
    loading.value = false
  }
}

watch(
  () => authStore.tenantId,
  () => {
    void fetchMetrics()
  }
)

watch(
  rangeValue,
  () => {
    void fetchMetrics()
  },
  { deep: true }
)

onMounted(() => {
  void fetchMetrics()
  window.addEventListener('resize', resizeAllCharts)
})

onActivated(() => {
  resizeAllCharts()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAllCharts)
  trendChart.value?.dispose()
  pieChart.value?.dispose()
  barChart.value?.dispose()
})
</script>

<template>
  <main class="dashboard-page">
    <a-card :bordered="false" class="welcome-card">
      <div class="welcome-head">
        <div>
          <a-typography-title :level="3">{{ greeting }}</a-typography-title>
          <a-typography-paragraph class="sub-text">
            当前租户：{{ authStore.user?.tenants.find((item) => item.id === authStore.tenantId)?.name || '-' }}
          </a-typography-paragraph>
        </div>
        <a-space>
          <a-range-picker v-model:value="rangeValue" />
          <a-button @click="applyQuickRange(7)">近7天</a-button>
          <a-button @click="applyQuickRange(30)">近30天</a-button>
        </a-space>
      </div>
    </a-card>

    <a-row :gutter="12" class="overview-row">
      <a-col :span="6">
        <a-card :loading="loading" :bordered="false">
          <a-statistic title="工单总量" :value="metrics?.overview.ticketTotal || 0" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :loading="loading" :bordered="false">
          <a-statistic
            title="完成率"
            :value="metrics?.overview.doneRate || 0"
            :precision="1"
            suffix="%"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :loading="loading" :bordered="false">
          <a-statistic
            title="平均处理时长"
            :value="metrics?.overview.avgHandleHours || 0"
            :precision="1"
            suffix="h"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :loading="loading" :bordered="false">
          <a-statistic title="SLA 达成率" :value="metrics?.overview.slaRate || 0" :precision="1" suffix="%" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="16">
        <a-card title="工单趋势" :loading="loading" :bordered="false">
          <div ref="trendRef" class="chart-box chart-line"></div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="工单分类占比" :loading="loading" :bordered="false">
          <div ref="pieRef" class="chart-box chart-pie"></div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="平均处理时长（小时）" :loading="loading" :bordered="false">
      <div ref="barRef" class="chart-box chart-bar"></div>
    </a-card>
  </main>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 12px;
}

.welcome-card {
  border-radius: 10px;
}

.welcome-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.sub-text {
  margin: 0;
  color: #64748b;
}

.overview-row {
  margin-bottom: 0;
}

.chart-box {
  width: 100%;
}

.chart-line {
  height: 340px;
}

.chart-pie {
  height: 340px;
}

.chart-bar {
  height: 320px;
}
</style>
