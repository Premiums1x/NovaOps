import { defineStore } from 'pinia'

interface PermissionState {
  codes: string[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    codes: [],
  }),
  actions: {
    setCodes(codes: string[]) {
      this.codes = codes
    },
    clearCodes() {
      this.codes = []
    },
  },
})
