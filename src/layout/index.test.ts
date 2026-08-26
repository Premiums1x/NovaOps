/* eslint-disable vue/one-component-per-file */
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import Layout from './index.vue'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'

const passthrough = defineComponent({ template: '<div><slot /></div>' })
const clickableButton = defineComponent({ emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' })
const dropdown = defineComponent({ template: '<div><slot /><slot name="overlay" /></div>' })
const menu = defineComponent({ emits: ['click'], template: '<ul><slot /></ul>' })
const select = defineComponent({
  props: {
    value: { type: String, required: true },
    options: { type: Array, default: () => [] },
  },
  emits: ['change'],
  template: '<select :value="value"><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>',
})
const stubs = {
  'a-layout': passthrough,
  'a-layout-sider': passthrough,
  'a-layout-header': passthrough,
  'a-layout-content': passthrough,
  'a-menu': menu,
  'a-menu-item': defineComponent({ template: '<li><slot /></li>' }),
  'a-button': clickableButton,
  'a-breadcrumb': true,
  'a-tabs': passthrough,
  'a-tab-pane': passthrough,
  'a-space': defineComponent({ template: '<div class="space"><slot /></div>' }),
  'a-tooltip': passthrough,
  'a-dropdown': dropdown,
  'a-select': select,
  ThemeSettings: defineComponent({ template: '<button class="theme-settings">theme</button>' }),
  RouterView: defineComponent({ template: '<div />' }),
}

const mountLayout = async (permissions: string[]) => {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/agent/chat', component: { template: '<div />' } },
    ],
  })
  await router.push('/dashboard')
  await router.isReady()
  const authStore = useAuthStore(pinia)
  authStore.user = { id: 'u-1', username: 'nova', displayName: 'Nova', roles: [], permissions: [], tenantId: 't-1', tenants: [] }
  const permissionStore = usePermissionStore(pinia)
  permissionStore.codes = permissions
  return { router, wrapper: mount(Layout, { global: { plugins: [pinia, router], stubs } }) }
}

describe('layout agent entry', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the agent button beside theme settings and navigates when permitted', async () => {
    const { router, wrapper } = await mountLayout(['agent:chat'])
    const headerSpace = wrapper.get('.header-right .space')
    const children = headerSpace.findAll(':scope > *')
    const themeIndex = children.findIndex((node) => node.classes().includes('theme-settings'))
    const agentButton = wrapper.get('[aria-label="智能问答"]')

    expect(themeIndex).toBeGreaterThanOrEqual(0)
    expect(children[themeIndex + 1]?.find('[aria-label="智能问答"]').exists()).toBe(true)
    await agentButton.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/agent/chat')
  })

  it('hides the agent button without permission and leaves it out of the user menu', async () => {
    const { wrapper } = await mountLayout([])

    expect(wrapper.find('[aria-label="智能问答"]').exists()).toBe(false)
    expect(wrapper.get('.user-link').text()).toBe('Nova')
    expect(wrapper.findAll('li').map((item) => item.text())).not.toContain('智能问答')
    expect(wrapper.find('.ant-float-btn').exists()).toBe(false)
  })

  it('keeps the tenant switch control supported by the backend', async () => {
    const { wrapper } = await mountLayout([])
    const authStore = useAuthStore()
    authStore.user!.tenants = [
      { id: 'tenant-a', name: 'Tenant A' },
      { id: 'tenant-b', name: 'Tenant B' },
    ]
    await wrapper.vm.$nextTick()
    // 单租户化后布局不再渲染租户选择器
    expect(wrapper.find('select').exists()).toBe(false)
  })
})
