<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { verifyApi } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const success = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  const token = String(route.query.token || '')
  if (!token) {
    loading.value = false
    errorMsg.value = '激活链接无效'
    return
  }
  try {
    await verifyApi(token)
    success.value = true
  } catch (error) {
    errorMsg.value = (error as Error).message || '激活失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="verify-page">
    <a-card class="verify-card" :bordered="false">
      <div v-if="loading" class="verify-status">
        <a-spin size="large" />
        <p>正在激活账号…</p>
      </div>
      <a-result
        v-else-if="success"
        status="success"
        title="激活成功"
        sub-title="账号已激活，可正常登录"
      >
        <template #extra>
          <a-button type="primary" @click="router.replace('/login')">去登录</a-button>
        </template>
      </a-result>
      <a-result v-else status="error" title="激活失败" :sub-title="errorMsg">
        <template #extra>
          <a-button @click="router.replace('/login')">返回登录</a-button>
        </template>
      </a-result>
    </a-card>
  </main>
</template>

<style scoped>
.verify-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--nova-surface);
}
.verify-card {
  width: min(480px, 100%);
  background: transparent;
  box-shadow: none;
}
.verify-status {
  display: grid;
  place-items: center;
  gap: 16px;
  padding: 48px 0;
  color: var(--nova-text-secondary);
}
</style>
