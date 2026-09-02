<script setup lang="ts">
//任务步骤时间线：工作台与任务中心共用（console.vue / tasks.vue）
export interface TaskStepView {
  seq: number
  tool: string
  title: string
  status: string
  observation: string
}

defineProps<{ steps: TaskStepView[] }>()

const statusColor = (status: string) =>
  status === 'DONE' ? 'green' : status === 'FAILED' ? 'red' : 'gray'
const statusTag = (status: string) =>
  status === 'DONE' ? 'success' : status === 'FAILED' ? 'error' : 'default'
</script>

<template>
  <a-timeline>
    <a-timeline-item
      v-for="step in steps"
      :key="`${step.seq}-${step.tool}`"
      :color="statusColor(step.status)"
    >
      <div class="step-line">
        <strong>{{ step.title }}</strong>
        <a-tag :color="statusTag(step.status)">{{ step.status }}</a-tag>
      </div>
      <a-collapse v-if="step.observation" ghost size="small" class="observation">
        <a-collapse-panel header="查看观察结果">
          <pre>{{ step.observation }}</pre>
        </a-collapse-panel>
      </a-collapse>
    </a-timeline-item>
  </a-timeline>
</template>

<style scoped>
.step-line{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.observation pre{max-height:200px;margin:4px 0 0;padding:8px;overflow:auto;border-radius:8px;background:var(--nova-surface);color:var(--nova-text-secondary);font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
