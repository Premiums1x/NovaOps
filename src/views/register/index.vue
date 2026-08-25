<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'
import type { RegisterRequestDto } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const invitationToken = computed(() => {
  const token = route.query.token
  return typeof token === 'string' ? token.trim() : ''
})
const formState = reactive<Omit<RegisterRequestDto, 'invitationToken'> & { confirmPassword: string }>({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const handleRegister = async () => {
  if (!invitationToken.value) return
  if (formState.password !== formState.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await authStore.register({
      invitationToken: invitationToken.value,
      username: formState.username,
      displayName: formState.displayName,
      password: formState.password,
    })
    message.success('注册成功')
    await router.replace('/dashboard')
  } catch {
    // 请求层展示后端返回的邀请状态或校验错误。
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <a-card class="register-card" :bordered="false">
      <div class="wordmark">NovaOps</div>
      <a-result
        v-if="!invitationToken"
        status="error"
        title="无效邀请"
        sub-title="注册链接缺少邀请令牌，请向平台管理员获取新的完整链接。"
      >
        <template #extra><a-button type="primary" @click="router.replace('/login')">返回登录</a-button></template>
      </a-result>
      <template v-else>
        <header class="register-heading">
          <span>INVITATION ONLY</span>
          <h1>创建 NovaOps 账号</h1>
          <p>您的租户与身份由此邀请确定，注册成功后即可进入工作台。</p>
        </header>
        <a-form layout="vertical" :model="formState" @finish="handleRegister">
          <a-form-item label="账号" name="username" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.username" autocomplete="username" placeholder="请输入账号" />
          </a-form-item>
          <a-form-item label="显示名称" name="displayName" :rules="[{ required: true, message: '请输入显示名称' }]">
            <a-input v-model:value="formState.displayName" placeholder="例如 张三" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, min: 6, message: '密码至少 6 位' }]">
            <a-input-password v-model:value="formState.password" autocomplete="new-password" placeholder="6–72 位" />
          </a-form-item>
          <a-form-item label="确认密码" name="confirmPassword" :rules="[{ required: true, message: '请再次输入密码' }]">
            <a-input-password v-model:value="formState.confirmPassword" autocomplete="new-password" placeholder="再次输入密码" />
          </a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="submitting">接受邀请并注册</a-button>
        </a-form>
        <button class="login-link" type="button" @click="router.replace('/login')">已有账号？返回登录</button>
      </template>
    </a-card>
  </main>
</template>

<style scoped>
.register-page { display: grid; min-height: 100vh; place-items: center; padding: 32px 20px; background: radial-gradient(circle at 10% 10%, rgba(22,119,255,.16), transparent 34%), var(--nova-bg); }
.register-card { width: min(500px, 100%); padding: 12px 14px; border: 1px solid var(--nova-border); border-radius: 18px; background: var(--nova-surface); box-shadow: var(--nova-shadow); }
.wordmark { margin-bottom: 26px; color: var(--nova-primary); font-size: 24px; font-weight: 800; letter-spacing: -.03em; }
.register-heading { margin-bottom: 26px; }.register-heading span { color: var(--nova-primary); font-size: 12px; font-weight: 700; letter-spacing: .15em; }.register-heading h1 { margin: 8px 0; color: var(--nova-text); font-size: 29px; }.register-heading p { margin: 0; color: var(--nova-text-secondary); }
.login-link { display: block; margin: 20px auto 0; border: 0; color: var(--nova-primary); background: transparent; cursor: pointer; }
</style>
