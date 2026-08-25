<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { registerApi } from '@/api/auth'
import type { RegisterRequestDto } from '@/types/auth'

const router = useRouter()
const submitting = ref(false)
const formState = reactive<RegisterRequestDto>({
  username: '',
  email: '',
  password: '',
})

const handleRegister = async () => {
  submitting.value = true
  try {
    await registerApi(formState)
    message.success('注册成功，请查收激活邮件')
    await router.replace('/login')
  } catch {
    // 请求层统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <a-card class="register-card" :bordered="false">
      <header class="register-heading">
        <span class="eyebrow">NOVAOPS · SIGN UP</span>
        <h2>注册账号</h2>
        <p>注册后需通过邮箱激活，默认授予普通成员身份</p>
      </header>
      <a-form layout="vertical" :model="formState" @finish="handleRegister">
        <a-form-item
          label="账号"
          name="username"
          :rules="[
            { required: true, message: '请输入账号' },
            { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '4~32 位字母、数字或下划线' },
          ]"
        >
          <a-input v-model:value="formState.username" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item
          label="邮箱"
          name="email"
          :rules="[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '邮箱格式不正确' },
          ]"
        >
          <a-input v-model:value="formState.email" placeholder="用于接收激活邮件" />
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.password" placeholder="至少 8 位" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="submitting">注册</a-button>
        </a-form-item>
      </a-form>
      <footer class="register-footer">
        已有账号？<a @click="router.replace('/login')">去登录</a>
      </footer>
    </a-card>
  </main>
</template>

<style scoped>
.register-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--nova-surface);
}
.register-card {
  width: min(420px, 100%);
  background: transparent;
  box-shadow: none;
}
.register-heading {
  margin-bottom: 24px;
}
.register-heading h2 {
  margin: 8px 0 5px;
  color: var(--nova-text);
  font-size: 28px;
}
.register-heading p {
  margin: 0;
  color: var(--nova-text-secondary);
}
.eyebrow {
  color: #78b4ff;
  font-weight: 700;
  letter-spacing: 0.14em;
  font-size: 12px;
}
.register-footer {
  text-align: center;
  color: var(--nova-text-secondary);
}
.register-footer a {
  color: #1677ff;
}
</style>
