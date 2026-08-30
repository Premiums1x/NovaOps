<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { cancelTaskApi, confirmTaskApi, createTaskApi, getTaskApi, listTasksApi, streamTaskEvents } from '@/api/agentTask'
import type { AgentTaskDto } from '@/api/agentTask'

const Preview = defineAsyncComponent(() => import('@/components/markdown/MdPreviewAsync.vue'))

interface PlanStepView { seq: number; tool: string; title: string; why: string }
interface ExecutedStepView { seq: number; tool: string; title: string; status: string; observation: string }
interface ConfirmInfo { confirmationId: string; tool: string; title: string; preview: Record<string, unknown> }

const suggestions = [
  '把 VPN 掉线的工单转派给 Jerry，并附上处理备注',
  '查一下目前所有待处理的工单，给我一份摘要',
  '知识库里有哪些文档？检索一下 VPN 相关的处置手册',
]

const goal = ref('')
const running = ref(false)
const taskId = ref('')
const planSteps = ref<PlanStepView[]>([])
const executed = ref<ExecutedStepView[]>([])
const confirmInfo = ref<ConfirmInfo | null>(null)
const confirmVisible = ref(false)
const result = ref('')
const errorText = ref('')
const recentTasks = ref<AgentTaskDto[]>([])
const viewingHistory = ref(false)

let controller: AbortController | undefined

const canSubmit = computed(() => goal.value.trim().length > 0 && !running.value)

const upsert = (step: ExecutedStepView) => {
  const index = executed.value.findIndex((item) => item.seq === step.seq)
  if (index >= 0) {
    executed.value.splice(index, 1, step)
  } else {
    executed.value.push(step)
  }
}

const handleEvent = (event: string, data: Record<string, unknown>) => {
  if (event === 'plan') {
    const steps = Array.isArray(data.steps) ? data.steps as Array<Record<string, unknown>> : []
    planSteps.value = steps.map((step, index) => ({
      seq: Number(step.seq || index + 1),
      tool: String(step.tool || ''),
      title: String(step.title || step.tool || ''),
      why: String(step.why || ''),
    }))
  }
  if (event === 'step') {
    upsert({
      seq: Number(data.seq || 0),
      tool: String(data.tool || ''),
      title: String(data.title || data.tool || ''),
      status: String(data.status || ''),
      observation: String(data.observation || ''),
    })
  }
  if (event === 'confirm_required') {
    confirmInfo.value = {
      confirmationId: String(data.confirmationId || ''),
      tool: String(data.tool || ''),
      title: String(data.title || data.tool || ''),
      preview: (data.preview || {}) as Record<string, unknown>,
    }
    confirmVisible.value = true
  }
  if (event === 'result') result.value = String(data.summary || '')
  if (event === 'error') errorText.value = String(data.message || '任务失败')
}

const run = async () => {
  const question = goal.value.trim()
  if (!question || running.value) return
  running.value = true
  viewingHistory.value = false
  result.value = ''
  errorText.value = ''
  planSteps.value = []
  executed.value = []
  confirmInfo.value = null
  controller = new AbortController()
  try {
    const { taskId: id } = await createTaskApi(question)
    taskId.value = id
    await streamTaskEvents(id, handleEvent, controller.signal)
  } catch (caught) {
    if ((caught as Error).name !== 'AbortError') {
      errorText.value = (caught as Error).message || '任务执行失败'
      message.error(errorText.value)
    }
  } finally {
    running.value = false
    refreshRecent()
  }
}

const onConfirm = async (approved: boolean) => {
  if (!confirmInfo.value) return
  const { confirmationId } = confirmInfo.value
  try {
    await confirmTaskApi(taskId.value, confirmationId, approved)
    confirmInfo.value = null
    confirmVisible.value = false
  } catch (caught) {
    message.error((caught as Error).message || '确认失败')
  }
}

const onCancelTask = async () => {
  controller?.abort()
  if (taskId.value) {
    try {
      await cancelTaskApi(taskId.value)
    } catch { /* 会话可能已结束，忽略 */ }
  }
  running.value = false
  confirmInfo.value = null
}

const refreshRecent = async () => {
  try {
    recentTasks.value = await listTasksApi()
  } catch { /* 列表失败不打断主流程 */ }
}

const openHistory = async (id: string) => {
  try {
    const detail = await getTaskApi(id)
    viewingHistory.value = true
    taskId.value = id
    goal.value = detail.task.goal
    result.value = detail.task.resultText || ''
    errorText.value = detail.task.errorText || ''
    planSteps.value = []
    executed.value = detail.steps
      .filter((step) => step.kind === 'tool' || step.kind === 'summary')
      .map((step, index) => ({
        seq: index + 1,
        tool: step.toolName || '',
        title: step.toolName || step.kind,
        status: step.status,
        observation: step.observationJson || '',
      }))
    confirmInfo.value = null
  } catch (caught) {
    message.error((caught as Error).message || '任务详情加载失败')
  }
}

const startNew = () => {
  viewingHistory.value = false
  result.value = ''
  errorText.value = ''
  planSteps.value = []
  executed.value = []
}

onMounted(refreshRecent)
</script>

<template>
  <section class="agent-console">
    <div class="console-grid">
      <div class="console-main">
        <a-card class="console-card" :bordered="false">
          <template #title>任务目标</template>
          <a-textarea
            v-model:value="goal"
            :rows="3"
            :maxlength="2000"
            placeholder="描述一个运维目标，例如：把 VPN 掉线的工单转派给 Jerry，并附上处理备注"
          />
          <div class="suggestions">
            <button
              v-for="item in suggestions"
              :key="item"
              type="button"
              :disabled="running"
              @click="goal = item"
            >
              {{ item }}
            </button>
          </div>
          <div class="actions">
            <a-button type="primary" :loading="running" :disabled="!canSubmit" @click="run">
              {{ running ? '执行中…' : '启动任务' }}
            </a-button>
            <a-button v-if="running" danger @click="onCancelTask">停止</a-button>
            <a-button v-if="viewingHistory" @click="startNew">新建任务</a-button>
          </div>
        </a-card>

        <a-alert v-if="errorText" type="error" show-icon :message="errorText" class="console-block" />

        <a-card v-if="planSteps.length" class="console-card" :bordered="false" title="执行计划">
          <a-steps direction="vertical" size="small" :current="planSteps.length">
            <a-step v-for="step in planSteps" :key="step.seq" :title="step.title" :description="step.why" />
          </a-steps>
        </a-card>

        <a-card v-if="executed.length" class="console-card" :bordered="false" title="执行过程">
          <a-timeline>
            <a-timeline-item
              v-for="step in executed"
              :key="`${step.seq}-${step.tool}`"
              :color="step.status === 'DONE' ? 'green' : step.status === 'FAILED' ? 'red' : 'gray'"
            >
              <div class="step-line">
                <strong>{{ step.title }}</strong>
                <a-tag :color="step.status === 'DONE' ? 'success' : step.status === 'FAILED' ? 'error' : 'default'">
                  {{ step.status }}
                </a-tag>
              </div>
              <a-collapse v-if="step.observation" ghost size="small" class="observation">
                <a-collapse-panel header="查看观察结果">
                  <pre>{{ step.observation }}</pre>
                </a-collapse-panel>
              </a-collapse>
            </a-timeline-item>
          </a-timeline>
        </a-card>

        <a-card v-if="result" class="console-card" :bordered="false" title="任务报告">
          <Preview editor-id="agent-task-result" :model-value="result" />
        </a-card>
      </div>

      <div class="console-side">
        <a-card class="console-card" :bordered="false" title="最近任务">
          <a-empty v-if="!recentTasks.length" description="还没有任务" />
          <a-list v-else :data-source="recentTasks" size="small">
            <template #renderItem="{ item }">
              <a-list-item class="recent-item" @click="openHistory(item.id)">
                <div class="recent-goal">{{ item.goal }}</div>
                <a-tag :color="item.status === 'DONE' ? 'success' : item.status === 'FAILED' ? 'error' : 'processing'">
                  {{ item.status }}
                </a-tag>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </div>
    </div>

    <a-modal
      v-model:open="confirmVisible"
      title="需要人工确认"
      ok-text="确认执行"
      cancel-text="拒绝"
      :closable="true"
      @ok="onConfirm(true)"
      @cancel="onConfirm(false)"
    >
      <p v-if="confirmInfo">智能体请求执行：<strong>{{ confirmInfo.title }}</strong>（{{ confirmInfo.tool }}）</p>
      <pre v-if="confirmInfo" class="preview-json">{{ JSON.stringify(confirmInfo.preview, null, 2) }}</pre>
      <p class="confirm-note">确认后才会真正执行；拒绝后智能体会调整后续动作。</p>
    </a-modal>
  </section>
</template>
<style scoped>
.agent-console{display:grid;gap:14px;height:100%;min-height:0;overflow:auto;padding:16px clamp(14px,3vw,40px);background:var(--nova-surface)}
.console-grid{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:14px;align-items:start}
@media(max-width:1100px){.console-grid{grid-template-columns:minmax(0,1fr)}}
.console-card{border:1px solid var(--nova-border);border-radius:12px;box-shadow:0 6px 18px rgba(15,23,42,.04)}
.console-block{border-radius:10px}
.console-main,.console-side{display:grid;gap:14px;min-width:0}
.suggestions{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
.suggestions button{padding:6px 12px;border:1px solid var(--nova-border);border-radius:999px;background:var(--nova-surface-elevated);color:var(--nova-text);font-size:12px;cursor:pointer;transition:border-color .2s}
.suggestions button:hover:not(:disabled){border-color:var(--nova-primary)}
.suggestions button:disabled{opacity:.5;cursor:not-allowed}
.actions{display:flex;gap:10px;margin-top:12px}
.step-line{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.observation pre{max-height:200px;margin:4px 0 0;padding:8px;overflow:auto;border-radius:8px;background:var(--nova-surface);color:var(--nova-text-secondary);font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.preview-json{max-height:260px;overflow:auto;border-radius:8px;background:var(--nova-surface);padding:10px;font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.confirm-note{margin:10px 0 0;color:var(--nova-text-secondary);font-size:12px}
.recent-item{cursor:pointer;gap:8px}
.recent-item:hover{background:var(--nova-surface-elevated)}
.recent-goal{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;font-size:13px;word-break:break-all}
</style>
