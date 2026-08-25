<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message, type TableProps } from 'ant-design-vue'
import {
  createInvitationApi,
  createTenantApi,
  getInvitationsApi,
  getTenantsApi,
} from '@/api/system'
import type { InvitationDto, InvitationRoleCode, TenantDto } from '@/types/system'
import { useAuthStore } from '@/store/auth'

defineOptions({ name: 'TenantInvitationManagement' })

const authStore = useAuthStore()
const loading = ref(false)
const tenantSubmitting = ref(false)
const invitationSubmitting = ref(false)
const tenants = ref<TenantDto[]>([])
const invitations = ref<InvitationDto[]>([])
const invitationLink = ref('')
const tenantForm = reactive({ code: '', name: '' })
const invitationForm = reactive<{ tenantId: string; roleCode: InvitationRoleCode }>({
  tenantId: '',
  roleCode: 'staff',
})

const tenantColumns: TableProps['columns'] = [
  { title: '租户代码', dataIndex: 'code', key: 'code' },
  { title: '租户名称', dataIndex: 'name', key: 'name' },
]
const invitationColumns: TableProps['columns'] = [
  { title: '租户', key: 'tenant' },
  { title: '身份', dataIndex: 'roleCode', key: 'roleCode', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '到期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 180 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 160 },
]
const tenantOptions = computed(() =>
  tenants.value.map((tenant) => ({ label: `${tenant.name} (${tenant.code})`, value: tenant.code }))
)

const statusOf = (invitation: InvitationDto) => {
  if (invitation.usedAt) return { label: '已使用', color: 'default' }
  if (!dayjs(invitation.expiresAt).isAfter(dayjs())) return { label: '已过期', color: 'error' }
  return { label: '待使用', color: 'processing' }
}

const loadData = async () => {
  loading.value = true
  try {
    ;[tenants.value, invitations.value] = await Promise.all([getTenantsApi(), getInvitationsApi()])
    if (!invitationForm.tenantId && tenants.value[0]) invitationForm.tenantId = tenants.value[0].code
  } finally {
    loading.value = false
  }
}

const createTenant = async () => {
  const code = tenantForm.code.trim()
  const name = tenantForm.name.trim()
  if (!code || !name) return message.warning('请填写租户代码和名称')
  tenantSubmitting.value = true
  try {
    const created = await createTenantApi({ code, name })
    tenants.value.push(created)
    await authStore.me()
    invitationForm.tenantId = created.code
    tenantForm.code = ''
    tenantForm.name = ''
    message.success('租户创建成功')
  } finally {
    tenantSubmitting.value = false
  }
}

const createInvitation = async () => {
  if (!invitationForm.tenantId) return message.warning('请选择租户')
  invitationSubmitting.value = true
  try {
    const created = await createInvitationApi({ ...invitationForm })
    const registerPath = `/register?token=${encodeURIComponent(created.token)}`
    invitationLink.value = new URL(registerPath, window.location.origin).toString()
    invitations.value.unshift(created)
    message.success('邀请已创建；令牌只在本次响应中显示')
  } finally {
    invitationSubmitting.value = false
  }
}

const copyLink = async () => {
  if (!invitationLink.value) return
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(invitationLink.value)
  } else {
    const textarea = document.createElement('textarea')
    textarea.value = invitationLink.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }
  message.success('注册链接已复制')
}

onMounted(loadData)
</script>

<template>
  <div class="management-page">
    <a-alert
      type="info"
      show-icon
      message="平台级管理"
      description="此页面仅对平台管理员开放；邀请令牌只在创建时返回，请立即复制完整注册链接。"
    />
    <a-row :gutter="16">
      <a-col :xs="24" :xl="10">
        <a-card title="创建租户" class="action-card">
          <a-form layout="vertical" :model="tenantForm" @finish="createTenant">
            <a-form-item label="租户代码" name="code" :rules="[{ required: true, pattern: /^[a-z][a-z0-9-]*$/, message: '以小写字母开头，仅含小写字母、数字和连字符' }]">
              <a-input v-model:value="tenantForm.code" placeholder="例如 tenant-acme" />
            </a-form-item>
            <a-form-item label="租户名称" name="name" :rules="[{ required: true, message: '请输入租户名称' }]">
              <a-input v-model:value="tenantForm.name" placeholder="例如 Acme 运维中心" />
            </a-form-item>
            <a-button type="primary" html-type="submit" :loading="tenantSubmitting">创建租户</a-button>
          </a-form>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="14">
        <a-card title="创建邀请" class="action-card">
          <a-form layout="vertical" :model="invitationForm" @finish="createInvitation">
            <a-form-item label="租户" name="tenantId" :rules="[{ required: true, message: '请选择租户' }]">
              <a-select v-model:value="invitationForm.tenantId" :options="tenantOptions" placeholder="请选择租户" />
            </a-form-item>
            <a-form-item label="邀请身份" name="roleCode">
              <a-select v-model:value="invitationForm.roleCode" :options="[{ label: '运维人员', value: 'staff' }, { label: '访客', value: 'guest' }]" />
            </a-form-item>
            <a-button type="primary" html-type="submit" :loading="invitationSubmitting">生成邀请</a-button>
          </a-form>
          <a-input-group v-if="invitationLink" compact class="invitation-link">
            <a-input :value="invitationLink" readonly style="width: calc(100% - 96px)" />
            <a-button type="primary" style="width: 96px" @click="copyLink">复制链接</a-button>
          </a-input-group>
        </a-card>
      </a-col>
    </a-row>
    <a-card title="租户列表">
      <a-table :columns="tenantColumns" :data-source="tenants" :loading="loading" row-key="code" :pagination="false" />
    </a-card>
    <a-card title="邀请记录">
      <a-table :columns="invitationColumns" :data-source="invitations" :loading="loading" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'tenant'">{{ record.tenantName }} <span class="secondary">{{ record.tenantId }}</span></template>
          <template v-else-if="column.key === 'status'"><a-tag :color="statusOf(record as InvitationDto).color">{{ statusOf(record as InvitationDto).label }}</a-tag></template>
          <template v-else-if="column.key === 'expiresAt' || column.key === 'createdAt'">{{ dayjs(record[column.dataIndex as 'expiresAt' | 'createdAt']).format('YYYY-MM-DD HH:mm') }}</template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.management-page { display: grid; gap: 16px; }.action-card { height: 100%; }.invitation-link { display: flex; margin-top: 18px; }.secondary { margin-left: 6px; color: var(--nova-text-secondary); font-size: 12px; }
</style>
