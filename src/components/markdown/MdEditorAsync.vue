<script setup lang="ts">
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const modelValue = defineModel<string>({
  required: true,
})

withDefaults(
  defineProps<{
    preview?: boolean
    readOnly?: boolean
    minHeight?: string
  }>(),
  {
    preview: false,
    readOnly: false,
    minHeight: '420px',
  }
)
</script>

<template>
  <MdEditor
    v-model="modelValue"
    :preview="preview"
    :read-only="readOnly"
    :style="{ minHeight }"
    no-mermaid
    no-katex
    no-echarts
    no-prettier
    no-upload-img
  />
</template>
