import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { setupRouterGuard } from './guard'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'
import { staticRoutes } from './staticRoutes'
import { transformMenuToRoutes } from './dynamicRoutes'

describe('platform administrator route guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const authStore = useAuthStore(pinia)
    const permissionStore = usePermissionStore(pinia)
    authStore.$reset()
    permissionStore.$reset()
  })

  it('blocks a tenant admin from the platform management route', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'Root', component: { template: '<router-view />' }, children: [
          { path: '/system/tenants', component: { template: '<div>Tenants</div>' }, meta: { platformAdmin: true } },
        ] },
        { path: '/403', component: { template: '<div>Forbidden</div>' } },
        { path: '/login', component: { template: '<div>Login</div>' }, meta: { public: true } },
      ],
    })
    const authStore = useAuthStore(pinia)
    const permissionStore = usePermissionStore(pinia)
    authStore.accessToken = 'test-token'
    authStore.user = {
      id: 'u-admin', username: 'tenant-admin', displayName: 'Tenant Admin', roles: ['admin'],
      permissions: [], tenantId: 'tenant-a', tenants: [{ id: 'tenant-a', name: 'Tenant A' }], platformAdmin: false,
    }
    permissionStore.isRouteReady = true
    setupRouterGuard(router)

    await router.push('/system/tenants')
    expect(router.currentRoute.value.path).toBe('/403')

    authStore.user.platformAdmin = true
    await router.push('/system/tenants')
    expect(router.currentRoute.value.path).toBe('/system/tenants')
  })

  it('registers the tenant invitation page statically and never duplicates it dynamically', () => {
    const root = staticRoutes.find((route) => route.name === 'Root')
    expect(root?.children?.some((route) => route.path === '/system/tenants')).toBe(true)
    expect(transformMenuToRoutes([{
      id: 'platform-tenants', title: '租户与邀请', name: 'TenantInvitationManagement',
      path: '/system/tenants', component: 'TenantInvitationManagementView',
    }])).toEqual([])
  })
})
