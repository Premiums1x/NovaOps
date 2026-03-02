import type { Router } from 'vue-router'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'

export const setupRouterGuard = (router: Router) => {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    const isPublicRoute = Boolean(to.meta.public)

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
        return '/login'
      }
    }

    const permissionCode = to.meta.permission as string | undefined
    if (permissionCode && !authStore.permissions.includes(permissionCode)) {
      return '/403'
    }

    return true
  })
}
