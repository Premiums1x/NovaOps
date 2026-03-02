import type { Directive } from 'vue'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'

export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const authStore = useAuthStore(pinia)
    const requiredCode = binding.value
    if (!requiredCode) {
      return
    }
    const hasPermission = authStore.permissions.includes(requiredCode)
    if (!hasPermission) {
      el.remove()
    }
  },
}
