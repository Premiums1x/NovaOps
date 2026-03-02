import type { Directive } from 'vue'
import { pinia } from '@/store'
import { usePermissionStore } from '@/store/permission'

export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const permissionStore = usePermissionStore(pinia)
    const requiredCode = binding.value
    if (!requiredCode) {
      return
    }
    const hasPermission = permissionStore.hasPermission(requiredCode)
    if (!hasPermission) {
      el.remove()
    }
  },
  updated(el, binding) {
    const permissionStore = usePermissionStore(pinia)
    const requiredCode = binding.value
    if (!requiredCode) {
      return
    }
    const hasPermission = permissionStore.hasPermission(requiredCode)
    if (!hasPermission) {
      el.remove()
    }
  },
}
