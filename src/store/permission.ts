import type { Router } from 'vue-router'
import { defineStore } from 'pinia'
import { menuApi } from '@/api/auth'
import { transformMenuToRoutes } from '@/router/dynamicRoutes'
import type { MenuItemDto } from '@/types/menu'


//权限状态
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
    
    //包含某个权限码
    hasPermission(code: string) {
      return this.codes.includes(code)
    },

    resetDynamicRoutes(router: Router) {
      this.dynamicRouteNames.forEach((routeName) => {
        if (router.hasRoute(routeName)) {
          //动态路由中表有就移除
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
      //菜单->路由表
      const routes = transformMenuToRoutes(data.menus)

      //重置动态路由
      this.resetDynamicRoutes(router)

      //重写动态路由
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
