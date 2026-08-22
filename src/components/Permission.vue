<script setup lang="ts">
import { computed } from 'vue'
import { usePermissionStore } from '@/store/permission'

const props = defineProps<{
  code: string
}>()

const permissionStore = usePermissionStore()
//根据权限码判断当前用户有没有权限，决定插槽内容是否渲染。
const visible = computed(() => permissionStore.hasPermission(props.code))
</script>

<template>
  <slot v-if="visible" />
</template>
