<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()

defineProps<{
  editorId: string
  modelValue: string
}>()
</script>

<template>
  <div class="markdown-preview">
    <MdPreview
      :editor-id="editorId"
      :model-value="modelValue"
      :theme="themeStore.resolvedMode"
      no-mermaid
      no-katex
      no-echarts
    />
  </div>
</template>

<style scoped>
.markdown-preview :deep(.md-editor-preview-wrapper),
.markdown-preview :deep(.md-editor-preview) {
  background: transparent;
}
</style>
