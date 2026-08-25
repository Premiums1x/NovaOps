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
  switchTenantApi: vi.fn(),
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

  it('uses an editable tenant code input on login', async () => {
    const router = await makeRouter([{ path: '/login', component: LoginView }], '/login')
    const wrapper = mount(LoginView, { global: { plugins: [antdPlugin, router] } })
    await flushPromises()
    const tenantInput = wrapper.get('input[placeholder="例如 tenant-a"]')
    expect(tenantInput.element.tagName).toBe('INPUT')
    await tenantInput.setValue('tenant-acme')
    expect((tenantInput.element as HTMLInputElement).value).toBe('tenant-acme')
    expect(wrapper.text()).toContain('若账号不存在')
  })

  it('submits the email registration contract and returns to login', async () => {
    const routes = [
      { path: '/register', component: RegisterView },
      { path: '/dashboard', component: { template: '<div>Dashboard</div>' } },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ]
    vi.mocked(registerApi).mockResolvedValue(undefined)
    const router = await makeRouter(routes, '/register')
    const wrapper = mount(RegisterView, { global: { plugins: [antdPlugin, router] } })
    await wrapper.get('input[placeholder="请输入账号"]').setValue('new-staff')
    await wrapper.get('input[placeholder="用于接收激活邮件"]').setValue('staff@example.com')
    await wrapper.get('input[type="password"]').setValue('strong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(registerApi).toHaveBeenCalledWith({
      username: 'new-staff',
      email: 'staff@example.com',
      password: 'strong-password',
    })
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
