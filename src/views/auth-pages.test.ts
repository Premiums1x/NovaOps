import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setupAntd } from '@/plugins/antd'
import { useAuthStore } from '@/store/auth'
import LoginView from '@/views/login/index.vue'
import RegisterView from '@/views/register/index.vue'

vi.mock('@/api/auth', () => ({
  getRolesApi: vi.fn().mockResolvedValue([
    { id: 'role-staff', code: 'staff', name: '运维人员', description: '', permissions: [] },
  ]),
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  meApi: vi.fn(),
  refreshTokenApi: vi.fn(),
}))

const antdPlugin = { install: setupAntd }
const makeRouter = async (routes: RouteRecordRaw[], path: string) => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return router
}

describe('authentication pages', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('uses an editable tenant code input on login', async () => {
    const router = await makeRouter([{ path: '/login', component: LoginView }], '/login')
    const wrapper = mount(LoginView, { global: { plugins: [antdPlugin, router] } })
    await flushPromises()
    const tenantInput = wrapper.get('input[placeholder="例如 tenant-a"]')
    expect(tenantInput.element.tagName).toBe('INPUT')
    await tenantInput.setValue('tenant-acme')
    expect((tenantInput.element as HTMLInputElement).value).toBe('tenant-acme')
    expect(wrapper.text()).not.toContain('账号不存在')
  })

  it('rejects a missing invitation token and submits the query token when present', async () => {
    const routes = [
      { path: '/register', component: RegisterView },
      { path: '/dashboard', component: { template: '<div>Dashboard</div>' } },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ]
    const missingRouter = await makeRouter(routes, '/register')
    const missing = mount(RegisterView, { global: { plugins: [antdPlugin, missingRouter] } })
    expect(missing.text()).toContain('无效邀请')

    const router = await makeRouter(routes, '/register?token=invite-once')
    const authStore = useAuthStore()
    const register = vi.fn().mockResolvedValue(undefined)
    authStore.register = register
    const wrapper = mount(RegisterView, { global: { plugins: [antdPlugin, router] } })
    await wrapper.get('input[placeholder="请输入账号"]').setValue('new-staff')
    await wrapper.get('input[placeholder="例如 张三"]').setValue('New Staff')
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0]!.setValue('strong-password')
    await passwordInputs[1]!.setValue('strong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).toHaveBeenCalledWith({
      invitationToken: 'invite-once',
      username: 'new-staff',
      displayName: 'New Staff',
      password: 'strong-password',
    })
  })
})
