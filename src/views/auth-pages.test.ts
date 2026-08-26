import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setupAntd } from '@/plugins/antd'
import { registerApi } from '@/api/auth'
import LoginView from '@/views/login/index.vue'
import RegisterView from '@/views/register/index.vue'

vi.mock('@/api/auth', () => ({
  getRolesApi: vi.fn().mockResolvedValue([
    { id: 'role-staff', code: 'staff', name: '运维人员', description: '', permissions: [] },
  ]),
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  verifyApi: vi.fn(),
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
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the login form without tenant/role selectors (single-tenant)', async () => {
    const router = await makeRouter([{ path: '/login', component: LoginView }], '/login')
    const wrapper = mount(LoginView, { global: { plugins: [antdPlugin, router] } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('租户')
    expect(wrapper.text()).not.toContain('身份')
    expect(wrapper.find('input[placeholder="请输入账号"]').exists()).toBe(true)
  })

  it('submits the email registration contract and jumps to the activation page with the returned token', async () => {
    const routes = [
      { path: '/register', component: RegisterView },
      { path: '/verify', component: { template: '<div>Verify</div>' } },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ]
    vi.mocked(registerApi).mockResolvedValue({ activationToken: 'tok-abc' })
    const router = await makeRouter(routes, '/register')
    const wrapper = mount(RegisterView, { global: { plugins: [antdPlugin, router] } })
    await wrapper.get('input[placeholder="请输入账号"]').setValue('new_staff')
    await wrapper.get('input[placeholder="用于接收激活邮件"]').setValue('staff@example.com')
    await wrapper.get('input[type="password"]').setValue('strong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(registerApi).toHaveBeenCalledWith({
      username: 'new_staff',
      email: 'staff@example.com',
      password: 'strong-password',
    })
    expect(router.currentRoute.value.path).toBe('/verify')
    expect(router.currentRoute.value.query.token).toBe('tok-abc')
  })

  it('returns to login with a prompt when no activation token is returned (smtp mode)', async () => {
    const routes = [
      { path: '/register', component: RegisterView },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ]
    vi.mocked(registerApi).mockResolvedValue({ activationToken: null })
    const router = await makeRouter(routes, '/register')
    const wrapper = mount(RegisterView, { global: { plugins: [antdPlugin, router] } })
    await wrapper.get('input[placeholder="请输入账号"]').setValue('new_staff')
    await wrapper.get('input[placeholder="用于接收激活邮件"]').setValue('staff@example.com')
    await wrapper.get('input[type="password"]').setValue('strong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
