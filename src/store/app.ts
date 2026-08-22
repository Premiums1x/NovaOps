import { defineStore } from 'pinia'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
//Vue Router 提供的当前路由对象的类型，就是 useRoute() 返回值的类型。

//Tab栏的每项
export interface TabItem {
  title: string
  path: string
  name: string
  closable: boolean
  keepAlive: boolean
}

//整个应用布局的状态：
interface AppState {
  collapsed: boolean
  tabs: TabItem[]
  activeTabPath: string
}

const DASHBOARD_TAB: TabItem = {
  title: 'Dashboard',
  path: '/dashboard',
  name: 'Dashboard',
  closable: false,
  keepAlive: true,
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    collapsed: false,
    tabs: [DASHBOARD_TAB],
    activeTabPath: '/dashboard',
  }),
  getters: {
    //把keep-alive缓存的找出来，再map加工
    cachedTabNames: (state) => state.tabs.filter((tab) => tab.keepAlive).map((tab) => tab.name),
  },

  actions: {
    setCollapsed(collapsed: boolean) {
      this.collapsed = collapsed
    },

    addTabByRoute(route: RouteLocationNormalizedLoaded) {
      const name = String(route.name || '')
      const path = route.path
      
      //只要包含一个就终止
      if (!name || !path || ['Login', 'Forbidden', 'NotFound', 'NotFoundPage'].includes(name)) {
        return
      }

      //tabs里找到对应当前路由对象的对象
      const existed = this.tabs.find((tab) => tab.path === path)

      if (!existed) {
        //没有就push一个当前路由对象
        this.tabs.push({
          title: String(route.meta.title || name),
          path,
          name,
          closable: path !== '/dashboard',
          keepAlive: Boolean(route.meta.keepAlive),
        })
      }

      //标记当前path高亮
      this.activeTabPath = path
    },

    removeTabByPath(path: string) {
      //只要是当前路径或者不可关闭的过滤掉
      this.tabs = this.tabs.filter((tab) => tab.path !== path || !tab.closable)

      //只要tabs里找到有当前激活路径的
      if (!this.tabs.find((tab) => tab.path === this.activeTabPath)) {
        //让当前激活路径为前一个或者dashboard
        this.activeTabPath = this.tabs[this.tabs.length - 1]?.path || '/dashboard'
      }
    },

    //重置tab栏为初始
    resetTabs() {
      this.tabs = [DASHBOARD_TAB]
      this.activeTabPath = '/dashboard'
    },
  },
})
