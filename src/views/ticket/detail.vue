<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAssetBatchApi } from '@/api/asset'
import {
  createTicketCommentApi,
  getTicketCommentsApi,
  getTicketDetailApi,
  ticketActionApi,
  uploadTicketAttachmentApi,
} from '@/api/ticket'
import type { AssetSimpleDto } from '@/types/asset'
import type { TicketActionType, TicketCommentDto, TicketDetailDto, TicketStatus } from '@/types/ticket'

defineOptions({
  name: 'TicketDetail',
})

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const commentLoading = ref(false)
const detail = ref<TicketDetailDto | null>(null)
const comments = ref<TicketCommentDto[]>([])
const relatedAssets = ref<AssetSimpleDto[]>([])
const newComment = ref('')

const actionForm = reactive({
  assignee: '',
  targetUser: '',
  remark: '',
})

const statusTextMap: Record<TicketStatus, string> = {
  pending: '待处理',
  processing: '处理中',
  review: '待复核',
  done: '已完成',
}

const flowSteps: TicketStatus[] = ['pending', 'processing', 'review', 'done']

const currentStep = computed(() => {
  if (!detail.value) {
    return 0
  }
  const index = flowSteps.indexOf(detail.value.status)
  return index >= 0 ? index : 0
})

const ticketId = computed(() => String(route.params.id || ''))

const loadTicket = async () => {
  if (!ticketId.value) {
    return
  }
  loading.value = true
  try {
    const data = await getTicketDetailApi(ticketId.value)
    detail.value = data
    if (data.assetIds.length) {
      relatedAssets.value = await getAssetBatchApi(data.assetIds)
    } else {
      relatedAssets.value = []
    }
  } finally {
    loading.value = false
  }
}

const loadComments = async () => {
  if (!ticketId.value) {
    return
  }
  const data = await getTicketCommentsApi(ticketId.value)
  comments.value = data
}

const refreshDetail = async () => {
  await Promise.all([loadTicket(), loadComments()])
}

const doAction = async (action: TicketActionType) => {
  if (!detail.value) {
    return
  }
  if (action === 'assign' && !actionForm.assignee) {
    message.warning('请选择指派对象')
    return
  }
  if (action === 'transfer' && !actionForm.targetUser) {
    message.warning('请填写转派目标人')
    return
  }
  actionLoading.value = true
  try {
    await ticketActionApi(detail.value.id, {
      action,
      assignee: actionForm.assignee || undefined,
      targetUser: actionForm.targetUser || undefined,
      remark: actionForm.remark || undefined,
    })
    message.success('状态流转成功')
    actionForm.remark = ''
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

const submitComment = async () => {
  if (!newComment.value.trim() || !ticketId.value) {
    return
  }
  commentLoading.value = true
  try {
    await createTicketCommentApi(ticketId.value, {
      content: newComment.value.trim(),
    })
    newComment.value = ''
    await loadComments()
    message.success('评论已发布')
  } finally {
    commentLoading.value = false
  }
}

const handleUpload = async (options: any) => {
  try {
    const file = options.file as File
    await uploadTicketAttachmentApi(ticketId.value, {
      filename: file.name,
      size: file.size,
    })
    options.onSuccess?.({}, file)
    message.success('附件上传成功')
    await loadTicket()
  } catch (error) {
    options.onError?.(error)
  }
}

watch(
  () => route.params.id,
  () => {
    void refreshDetail()
  }
)

onMounted(() => {
  void refreshDetail()
})
</script>

<template>
  <section class="ticket-detail-page">
    <a-card :loading="loading" :bordered="false">
      <template #title>
        <a-space>
          <a-button size="small" @click="router.back()">返回</a-button>
          <span>{{ detail?.title || '工单详情' }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <Permission code="ticket:assign">
            <a-button
              size="small"
              :loading="actionLoading"
              :disabled="detail?.status === 'done'"
              @click="doAction('assign')"
            >
              指派
            </a-button>
          </Permission>
          <Permission code="ticket:transfer">
            <a-button
              size="small"
              :loading="actionLoading"
              :disabled="detail?.status === 'done'"
              @click="doAction('transfer')"
            >
              转派
            </a-button>
          </Permission>
          <Permission code="ticket:close">
            <a-button
              size="small"
              danger
              :loading="actionLoading"
              :disabled="detail?.status === 'done'"
              @click="doAction('close')"
            >
              关闭
            </a-button>
          </Permission>
          <a-button size="small" :loading="actionLoading" @click="doAction('advance')">推进状态</a-button>
          <a-button size="small" :loading="actionLoading" @click="doAction('reject')">驳回</a-button>
        </a-space>
      </template>

      <a-steps :current="currentStep" size="small" class="mb16">
        <a-step title="待处理" />
        <a-step title="处理中" />
        <a-step title="待复核" />
        <a-step title="已完成" />
      </a-steps>

      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="工单号">{{ detail?.id }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag color="geekblue">{{ detail ? statusTextMap[detail.status] : '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="优先级">{{ detail?.priority }}</a-descriptions-item>
        <a-descriptions-item label="负责人">{{ detail?.assignee || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ detail?.creator }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ detail?.updatedAt ? dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="关联资产">
          <a-space wrap>
            <a-tag
              v-for="asset in relatedAssets"
              :key="asset.id"
              color="blue"
              class="asset-tag"
              @click="router.push(`/asset/detail/${asset.id}`)"
            >
              {{ asset.id }} / {{ asset.name }}
            </a-tag>
            <span v-if="!relatedAssets.length">-</span>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ detail?.description }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-row :gutter="12">
      <a-col :span="14">
        <a-card title="流转记录" :bordered="false">
          <a-timeline>
            <a-timeline-item v-for="item in detail?.timeline || []" :key="item.id">
              <div class="timeline-title">
                {{ item.action }}
                <span class="timeline-time">{{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
              </div>
              <div>执行人：{{ item.operator }}</div>
              <div v-if="item.fromStatus && item.toStatus">
                状态：{{ statusTextMap[item.fromStatus] }} -> {{ statusTextMap[item.toStatus] }}
              </div>
              <div v-if="item.remark">备注：{{ item.remark }}</div>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card title="状态操作" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="指派给">
              <a-select
                v-model:value="actionForm.assignee"
                allow-clear
                :options="[
                  { value: 'Tom', label: 'Tom' },
                  { value: 'Jerry', label: 'Jerry' },
                  { value: 'Alice', label: 'Alice' },
                  { value: 'Nova Team', label: 'Nova Team' },
                ]"
              />
            </a-form-item>
            <a-form-item label="转派目标人">
              <a-input v-model:value="actionForm.targetUser" placeholder="例如：oncall.bob" />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model:value="actionForm.remark" :rows="3" placeholder="填写 reject/transfer 说明" />
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="12">
        <a-card title="评论" :bordered="false">
          <a-list :data-source="comments" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-comment
                  :author="item.author"
                  :content="item.content"
                  :datetime="dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss')"
                />
              </a-list-item>
            </template>
          </a-list>
          <a-space direction="vertical" style="width: 100%; margin-top: 12px">
            <a-textarea v-model:value="newComment" :rows="3" placeholder="输入评论..." />
            <a-button type="primary" :loading="commentLoading" @click="submitComment">发表评论</a-button>
          </a-space>
        </a-card>
      </a-col>

      <a-col :span="12">
        <a-card title="附件" :bordered="false">
          <a-upload :show-upload-list="false" :custom-request="handleUpload">
            <a-button type="dashed">上传附件（Mock）</a-button>
          </a-upload>
          <a-list :data-source="detail?.attachments || []" size="small" class="mt12">
            <template #renderItem="{ item }">
              <a-list-item>
                <a :href="item.url" target="_blank">{{ item.name }}</a>
                <span>{{ (item.size / 1024).toFixed(1) }} KB</span>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
    </a-row>
  </section>
</template>

<style scoped>
.ticket-detail-page {
  display: grid;
  gap: 12px;
}

.mb16 {
  margin-bottom: 16px;
}

.mt12 {
  margin-top: 12px;
}

.timeline-title {
  font-weight: 600;
  display: flex;
  gap: 8px;
  align-items: center;
}

.timeline-time {
  color: #64748b;
  font-size: 12px;
}

.asset-tag {
  cursor: pointer;
}
</style>
