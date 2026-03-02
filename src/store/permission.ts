import type { Router } from 'vue-router'
import { defineStore } from 'pinia'
import { menuApi } from '@/api/auth'
import { transformMenuToRoutes } from '@/router/dynamicRoutes'
import type { MenuItemDto } from '@/types/menu'

interface PermissionState {
  codes: string[]
  menus: MenuItemDto[]
  isRouteReady: boolean
  dynamicRouteNames: string[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    codes: [],
    menus: [],
    isRouteReady: false,
    dynamicRouteNames: [],
  }),
  actions: {
    hasPermission(code: string) {
      return this.codes.includes(code)
    },
    resetDynamicRoutes(router: Router) {
      this.dynamicRouteNames.forEach((routeName) => {
        if (router.hasRoute(routeName)) {
          router.removeRoute(routeName)
        }
      })
      this.dynamicRouteNames = []
      this.codes = []
      this.menus = []
      this.isRouteReady = false
    },
    async generateRoutes(router: Router) {
      const data = await menuApi()
      const routes = transformMenuToRoutes(data.menus)
      this.resetDynamicRoutes(router)

      routes.forEach((route) => {
        router.addRoute('Root', route)
        this.dynamicRouteNames.push(String(route.name))
      })

      this.codes = data.permissions
      this.menus = data.menus
      this.isRouteReady = true
    },
  },
  persist: {
    key: 'novaops_permission',
    pick: ['codes', 'menus'],
  },
})
