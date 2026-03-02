import { defineStore } from 'pinia'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface TabItem {
  title: string
  path: string
  name: string
  closable: boolean
  keepAlive: boolean
}

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
    cachedTabNames: (state) => state.tabs.filter((tab) => tab.keepAlive).map((tab) => tab.name),
  },
  actions: {
    setCollapsed(collapsed: boolean) {
      this.collapsed = collapsed
    },
    addTabByRoute(route: RouteLocationNormalizedLoaded) {
      const name = String(route.name || '')
      const path = route.path
      if (!name || !path || ['Login', 'Forbidden', 'NotFound', 'NotFoundPage'].includes(name)) {
        return
      }

      const existed = this.tabs.find((tab) => tab.path === path)
      if (!existed) {
        this.tabs.push({
          title: String(route.meta.title || name),
          path,
          name,
          closable: path !== '/dashboard',
          keepAlive: Boolean(route.meta.keepAlive),
        })
      }
      this.activeTabPath = path
    },
    removeTabByPath(path: string) {
      this.tabs = this.tabs.filter((tab) => tab.path !== path || !tab.closable)
      if (!this.tabs.find((tab) => tab.path === this.activeTabPath)) {
        this.activeTabPath = this.tabs[this.tabs.length - 1]?.path || '/dashboard'
      }
    },
    resetTabs() {
      this.tabs = [DASHBOARD_TAB]
      this.activeTabPath = '/dashboard'
    },
  },
})
