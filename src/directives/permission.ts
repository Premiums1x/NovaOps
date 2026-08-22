// main.ts:29注册，此为具体实现

//只导入类型，不导入实际代码。
import type { Directive } from 'vue'
import { pinia } from '@/store'
import { usePermissionStore } from '@/store/permission'

export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
     // el — 指令绑定的 DOM 元素
    // binding.value — 指令传入的值（权限码） 
    const permissionStore = usePermissionStore(pinia)
    const requiredCode = binding.value
    if (!requiredCode) {
      return
    }
    const hasPermission = permissionStore.hasPermission(requiredCode)
    if (!hasPermission) {
      el.remove()
      //没权限，直接从 DOM 移除这个元素
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
