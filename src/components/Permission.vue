<script setup lang="ts">
import { computed } from 'vue'
import { usePermissionStore } from '@/store/permission'

const props = defineProps<{
  code: string
}>()

const permissionStore = usePermissionStore()
const visible = computed(() => permissionStore.hasPermission(props.code))
</script>

<template>
  <slot v-if="visible" />
</template>
