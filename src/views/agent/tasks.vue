<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  createTaskApi,
  getTaskApi,
  getTaskAuditsApi,
  listTasksApi,
  streamTaskEvents,
} from '@/api/agentTask'
import type { AgentTaskAuditDto, AgentTaskDto, AgentTaskStepDto } from '@/api/agentTask'
import type { TaskStepView } from '@/components/agent/TaskTimeline.vue'
import TaskTimeline from '@/components/agent/TaskTimeline.vue'
import { parseStepObservation, stepSpanText } from '@/utils/taskStep'

const Preview = defineAsyncComponent(() => import('@/components/markdown/MdPreviewAsync.vue'))

const STATUS_FILTERS = ['ALL', 'RUNNING', 'AWAITING_CONFIRM', 'DONE', 'FAILED', 'CANCELLED'] as const

const tasks = ref<AgentTaskDto[]>([])
const statusFilter = ref<string>('ALL')
const selectedId = ref('')
const detailTask = ref<AgentTaskDto | null>(null)
const executed = ref<TaskStepView[]>([])
const audits = ref<AgentTaskAuditDto[]>([])
const result = ref('')
const errorText = ref('')
const rerunning = ref(false)
const planAt = ref<number | undefined>()
const resultAt = ref<number | undefined>()

let controller: AbortController | undefined

//任务总耗时：实时流用 plan→result 事件差；历史回放退化为首尾步骤差
const totalDuration = computed(() =>
  stepSpanText(executed.value.map((step) => step.at), planAt.value, resultAt.value),
)

const filteredTasks = computed(() =>
  statusFilter.value === 'ALL'
    ? tasks.value
    : tasks.value.filter((task) => task.status === statusFilter.value),
)

const statusTag = (status: string) =>
  status === 'DONE' ? 'success' : status === 'FAILED' ? 'error' : 'processing'

const formatTime = (value: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '—'

const toStepView = (step: AgentTaskStepDto, index: number): TaskStepView => {
  const parsed = parseStepObservation(step.observationJson)
  return {
    seq: step.seq ?? index + 1,
    tool: step.toolName || '',
    title: step.toolName || step.kind,
    status: step.status,
    observation: parsed.observation,
    at: parsed.at,
  }
}

const loadTasks = async () => {
  try {
    tasks.value = await listTasksApi()
  } catch { /* 列表失败不打断主流程 */ }
}

const loadDetail = async (id: string) => {
  const [detail, taskAudits] = await Promise.all([
    getTaskApi(id),
    getTaskAuditsApi(id).catch(() => [] as AgentTaskAuditDto[]),
  ])
  selectedId.value = id
  detailTask.value = detail.task
  result.value = detail.task.resultText || ''
  errorText.value = detail.task.errorText || ''
  executed.value = detail.steps
    .filter((step) => step.kind === 'tool' || step.kind === 'summary')
    .map(toStepView)
  audits.value = taskAudits
}

const selectTask = async (id: string) => {
  controller?.abort()
  try {
    await loadDetail(id)
  } catch (caught) {
    message.error((caught as Error).message || '任务详情加载失败')
  }
}

const onStreamEvent = (event: string, data: Record<string, unknown>) => {
  const at = typeof data.at === 'number' ? data.at : undefined
  if (event === 'plan') {
    planAt.value = at
    const steps = Array.isArray(data.steps) ? data.steps as Array<Record<string, unknown>> : []
    executed.value = steps.map((step, index) => ({
      seq: Number(step.seq || index + 1),
      tool: String(step.tool || ''),
      title: String(step.title || step.tool || ''),
      status: 'PENDING',
      observation: '',
    }))
  }
  if (event === 'step') {
    const step: TaskStepView = {
      seq: Number(data.seq || 0),
      tool: String(data.tool || ''),
      title: String(data.title || data.tool || ''),
      status: String(data.status || ''),
      observation: String(data.observation || ''),
      at,
    }
    const index = executed.value.findIndex((item) => item.seq === step.seq)
    if (index >= 0) {
      executed.value.splice(index, 1, step)
    } else {
      executed.value.push(step)
    }
  }
  if (event === 'result') {
    result.value = String(data.summary || '')
    resultAt.value = at
  }
  if (event === 'error') errorText.value = String(data.message || '任务失败')
  if (event === 'confirm_required') {
    // 重跑如果撞上写操作确认：工作台才有确认上下文，这里提示后停止流
    message.warning('重跑的任务包含写操作，请在工作台确认后继续')
    controller?.abort()
  }
}

const rerun = async (task: AgentTaskDto) => {
  if (rerunning.value) return
  rerunning.value = true
  controller = new AbortController()
  try {
    const { taskId: id } = await createTaskApi(task.goal)
    result.value = ''
    errorText.value = ''
    executed.value = []
    audits.value = []
    detailTask.value = { ...task, id, status: 'RUNNING' }
    selectedId.value = id
    await streamTaskEvents(id, onStreamEvent, controller.signal)
  } catch (caught) {
    if ((caught as Error).name !== 'AbortError') {
      errorText.value = (caught as Error).message || '任务执行失败'
      message.error(errorText.value)
    }
  } finally {
    rerunning.value = false
    loadTasks()
  }
}

onMounted(loadTasks)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="agent-tasks">
    <div class="tasks-grid">
      <div class="tasks-side">
        <a-card class="tasks-card" :bordered="false" title="任务列表">
          <div class="filter-chips">
            <button
              v-for="item in STATUS_FILTERS"
              :key="item"
              type="button"
              :class="{ active: statusFilter === item }"
              @click="statusFilter = item"
            >
              {{ item === 'ALL' ? '全部' : item }}
            </button>
          </div>
          <a-empty v-if="!filteredTasks.length" description="没有符合条件的任务" />
          <a-list v-else :data-source="filteredTasks" size="small">
            <template #renderItem="{ item }">
              <a-list-item
                class="task-item"
                :class="{ selected: item.id === selectedId }"
                @click="selectTask(item.id)"
              >
                <div class="task-goal">{{ item.goal }}</div>
                <div class="task-meta">
                  <a-tag :color="statusTag(item.status)">{{ item.status }}</a-tag>
                  <span class="task-time">{{ formatTime(item.createdAt) }}</span>
                </div>
                <div v-if="item.status === 'FAILED'" class="task-rerun">
                  <a-button size="small" :loading="rerunning" @click.stop="rerun(item)">重跑</a-button>
                </div>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </div>

      <div class="tasks-main">
        <a-empty v-if="!detailTask" description="从左侧选择一个任务查看详情" />
        <template v-else>
          <a-alert v-if="errorText" type="error" show-icon :message="errorText" class="tasks-block" />
          <a-card v-if="executed.length" class="tasks-card" :bordered="false" title="执行过程">
            <TaskTimeline :steps="executed" />
          </a-card>
          <a-card v-if="result" class="tasks-card" :bordered="false" title="任务报告">
            <div v-if="totalDuration" class="report-meta">总耗时 {{ totalDuration }}</div>
            <Preview editor-id="agent-tasks-result" :model-value="result" />
          </a-card>
          <a-card v-if="audits.length" class="tasks-card" :bordered="false" title="审计明细">
            <a-collapse ghost>
              <a-collapse-panel v-for="audit in audits" :key="audit.id" :header="audit.toolName || '未知工具'">
                <div class="audit-tags">
                  <a-tag v-if="audit.writeOperation" color="orange">写操作</a-tag>
                  <a-tag v-if="audit.confirmed === true" color="green">已确认</a-tag>
                  <a-tag v-if="audit.allowed === false" color="red">已拦截</a-tag>
                  <a-tag v-else-if="audit.allowed" color="blue">放行</a-tag>
                  <span class="audit-time">{{ formatTime(audit.createdAt) }}</span>
                </div>
                <pre v-if="audit.argsDigest" class="audit-json">args: {{ audit.argsDigest }}</pre>
                <pre v-if="audit.resultDigest" class="audit-json">result: {{ audit.resultDigest }}</pre>
              </a-collapse-panel>
            </a-collapse>
          </a-card>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.agent-tasks{height:100%;min-height:0;overflow:auto;padding:16px clamp(14px,3vw,40px);background:var(--nova-surface)}
.tasks-grid{display:grid;grid-template-columns:340px minmax(0,1fr);gap:14px;align-items:start}
@media(max-width:1100px){.tasks-grid{grid-template-columns:minmax(0,1fr)}}
.tasks-card{border:1px solid var(--nova-border);border-radius:12px;box-shadow:0 6px 18px rgba(15,23,42,.04)}
.tasks-block{border-radius:10px}
.tasks-side,.tasks-main{display:grid;gap:14px;min-width:0}
.report-meta{margin-bottom:8px;font-size:12px;color:var(--nova-text-secondary)}
.filter-chips{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:10px}
.filter-chips button{padding:4px 10px;border:1px solid var(--nova-border);border-radius:999px;background:var(--nova-surface-elevated);color:var(--nova-text);font-size:12px;cursor:pointer}
.filter-chips button.active{border-color:var(--nova-primary);color:var(--nova-primary)}
.task-item{cursor:pointer;gap:8px;flex-direction:column;align-items:stretch}
.task-item:hover,.task-item.selected{background:var(--nova-surface-elevated)}
.task-goal{font-size:13px;word-break:break-all;display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.task-meta{display:flex;align-items:center;gap:8px}
.task-time{font-size:12px;color:var(--nova-text-secondary)}
.audit-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.audit-time{font-size:12px;color:var(--nova-text-secondary)}
.audit-json{margin:6px 0 0;padding:8px;border-radius:8px;background:var(--nova-surface);color:var(--nova-text-secondary);font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
