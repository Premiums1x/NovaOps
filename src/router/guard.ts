import type { Router } from 'vue-router'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'

export const setupRouterGuard = (router: Router) => {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    const permissionStore = usePermissionStore(pinia)
    const isPublicRoute = Boolean(to.meta.public)

    if (to.name === 'NotFound' && !authStore.isAuthenticated) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
      }
    }

    if (!isPublicRoute && !authStore.isAuthenticated) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
      }
    }

    if (to.path === '/login' && authStore.isAuthenticated) {
      return '/dashboard'
    }

    if (authStore.isAuthenticated && !authStore.user) {
      try {
        await authStore.me()
      } catch {
        authStore.logout()
        permissionStore.resetDynamicRoutes(router)
        return '/login'
      }
    }

    if (authStore.isAuthenticated && !permissionStore.isRouteReady) {
      await permissionStore.generateRoutes(router)
      return to.fullPath
    }

    const permissionCode = to.meta.permission as string | undefined
    if (permissionCode && !permissionStore.hasPermission(permissionCode)) {
      return '/403'
    }

    return true
  })
}
