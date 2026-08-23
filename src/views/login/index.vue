<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'
import { getRolesApi } from '@/api/auth'
import type { LoginRequestDto, RoleDto } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const roles = ref<RoleDto[]>([])
const formState = reactive<LoginRequestDto>({ username: '', password: '', tenantId: 'tenant-a', roleId: '' })

onMounted(async () => {
  try { roles.value = await getRolesApi() } catch { /* 角色加载失败不阻塞页面 */ }
})

const handleLogin = async () => {
  try {
    submitting.value = true
    await authStore.login(formState)
    message.success('登录成功')
    await router.replace(String(route.query.redirect || '/dashboard'))
  } catch { /* 请求层统一提示 */ } finally { submitting.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="NovaOps 产品介绍">
      <div class="brand-mask"></div>
      <div class="brand-content">
        <div class="brand-badge">NOVAOPS · ENTERPRISE AI</div>
        <h1>让企业知识与运维协作<br />真正连接起来</h1>
        <p>统一管理服务流程、企业知识和智能问答，让每一次决策都有依据可追溯。</p>
        <div class="brand-metrics">
          <span><strong>RAG</strong>可信知识检索</span><span><strong>24×7</strong>智能服务助手</span><span><strong>Tenant</strong>企业级数据隔离</span>
        </div>
      </div>
    </section>
    <section class="form-panel">
      <div class="mobile-brand">NovaOps</div>
      <a-card class="login-card" :bordered="false">
        <header class="login-heading"><span class="eyebrow">WELCOME BACK</span><h2>登录 NovaOps</h2><p>使用您的企业账号继续</p></header>
        <a-alert type="info" show-icon message="若账号不存在，将按所选身份自动创建（管理员除外）" class="login-alert" />
        <a-form layout="vertical" :model="formState" @finish="handleLogin">
          <a-form-item label="账号" name="username" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.username" placeholder="请输入账号" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="formState.password" placeholder="请输入密码" />
          </a-form-item>
          <a-form-item label="租户" name="tenantId" :rules="[{ required: true, message: '请选择租户' }]">
            <a-select v-model:value="formState.tenantId" :options="[{ value: 'tenant-a', label: 'Tenant A' }, { value: 'tenant-b', label: 'Tenant B' }]" />
          </a-form-item>
          <a-form-item label="身份" name="roleId" :rules="[{ required: true, message: '请选择身份' }]">
            <a-select v-model:value="formState.roleId" :options="roles.map(r => ({ value: r.id, label: r.name }))" placeholder="请选择身份" />
          </a-form-item>
          <a-form-item><a-button type="primary" html-type="submit" block :loading="submitting">登录</a-button></a-form-item>
        </a-form>
      </a-card>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; grid-template-columns: minmax(0, 2fr) minmax(360px, 1fr); min-height: 100vh; background: var(--nova-surface); }
.brand-panel { position: relative; min-height: 100vh; overflow: hidden; color: #fff; background: #101b36 url('/login-hero.svg') center/cover no-repeat; }
.brand-mask { position: absolute; inset: 0; background: linear-gradient(120deg, rgba(5,14,35,.92) 10%, rgba(10,39,79,.72) 52%, rgba(22,119,255,.28)); }
.brand-content { position: absolute; z-index: 1; left: clamp(48px,8vw,128px); right: clamp(36px,8vw,120px); bottom: clamp(72px,13vh,150px); max-width: 780px; }
.brand-badge, .eyebrow { color: #78b4ff; font-weight: 700; letter-spacing: .14em; font-size: 12px; }
.brand-content h1 { margin: 22px 0; color: #fff; font-size: clamp(38px,4.5vw,68px); line-height: 1.16; letter-spacing: -.035em; }
.brand-content p { max-width: 650px; color: rgba(255,255,255,.74); font-size: 18px; }
.brand-metrics { display: flex; flex-wrap: wrap; gap: 28px; margin-top: 38px; color: rgba(255,255,255,.72); }
.brand-metrics span { display: grid; gap: 3px; }.brand-metrics strong { color: #fff; font-size: 18px; }
.form-panel { display: grid; place-items: center; padding: 48px clamp(28px,5vw,72px); background: var(--nova-surface); }
.mobile-brand { display: none; }.login-card { width: min(430px,100%); background: transparent; box-shadow: none; }
.login-heading { margin-bottom: 28px; }.login-heading h2 { margin: 8px 0 5px; color: var(--nova-text); font-size: 30px; }.login-heading p { margin: 0; color: var(--nova-text-secondary); }.login-alert { margin-bottom: 20px; }
@media (max-width: 900px) { .login-page { grid-template-columns: 1fr; }.brand-panel { display: none; }.form-panel { min-height: 100vh; padding: 28px 20px; align-content: center; }.mobile-brand { display: block; margin-bottom: 16px; color: var(--nova-primary); font-size: 24px; font-weight: 800; } }
</style>
