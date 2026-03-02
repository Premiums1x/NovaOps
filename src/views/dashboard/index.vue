<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/store/auth'

defineOptions({
  name: 'Dashboard',
})

const authStore = useAuthStore()

const greeting = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'User'
  return `Hi, ${name}`
})
</script>

<template>
  <main class="dashboard-page">
    <a-card title="Dashboard" :bordered="false">
      <a-typography-title :level="3">{{ greeting }}</a-typography-title>
      <a-typography-paragraph>
        NovaOps 阶段1已完成：登录鉴权、Token 刷新、路由守卫、MSW 模拟接口。
      </a-typography-paragraph>
    </a-card>
  </main>
</template>

<style scoped>
.dashboard-page {
  min-height: 420px;
  padding: 16px;
}
</style>
