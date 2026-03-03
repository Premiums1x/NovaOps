<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

defineProps<{
  editorId: string
  modelValue: string
}>()
</script>

<template>
  <MdPreview
    :editor-id="editorId"
    :model-value="modelValue"
    no-mermaid
    no-katex
    no-echarts
  />
</template>
