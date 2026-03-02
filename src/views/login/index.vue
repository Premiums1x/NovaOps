<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'
import type { LoginRequestDto } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)

const formState = reactive<LoginRequestDto>({
  username: 'admin',
  password: '123456',
  tenantId: 'tenant-a',
})

const handleLogin = async () => {
  try {
    submitting.value = true
    await authStore.login(formState)
    message.success('登录成功')
    const redirect = String(route.query.redirect || '/dashboard')
    await router.replace(redirect)
  } catch {
    // 错误消息由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <a-card class="login-card" title="NovaOps 登录">
      <a-alert
        type="info"
        show-icon
        message="演示账号：admin / staff / guest，密码统一为 123456"
        class="login-alert"
      />
      <a-form layout="vertical" :model="formState" @finish="handleLogin">
        <a-form-item
          label="账号"
          name="username"
          :rules="[{ required: true, message: '请输入账号' }]"
        >
          <a-select
            v-model:value="formState.username"
            :options="[
              { value: 'admin', label: 'admin' },
              { value: 'staff', label: 'staff' },
              { value: 'guest', label: 'guest' },
            ]"
          />
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password v-model:value="formState.password" />
        </a-form-item>
        <a-form-item
          label="租户"
          name="tenantId"
          :rules="[{ required: true, message: '请选择租户' }]"
        >
          <a-select
            v-model:value="formState.tenantId"
            :options="[
              { value: 'tenant-a', label: 'Tenant A' },
              { value: 'tenant-b', label: 'Tenant B' },
            ]"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="submitting"> 登录 </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
}

.login-card {
  width: min(420px, 95vw);
  border-radius: 12px;
}

.login-alert {
  margin-bottom: 20px;
}
</style>
