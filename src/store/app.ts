import { defineStore } from 'pinia'

interface AppState {
  collapsed: boolean
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    collapsed: false,
  }),
  actions: {
    setCollapsed(collapsed: boolean) {
      this.collapsed = collapsed
    },
  },
})
