<script setup lang="ts">
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

// 等价于下面这两行：
// const props = defineProps<{ modelValue: string }>()
// const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const modelValue = defineModel<string>({
  required: true,
})

withDefaults(
  defineProps<{
    preview?: boolean
    readOnly?: boolean
    minHeight?: string
  }>(),
  //指定默认值
  {
    preview: false,
    readOnly: false,
    minHeight: '420px',
  }
)
</script>

<template>
  <!-- 声明好的变量传给 MdEditor 组件 -->
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
