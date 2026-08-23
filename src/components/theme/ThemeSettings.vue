<script setup lang="ts">
import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons-vue'
import { useThemeStore, type ThemeMode } from '@/store/theme'

const themeStore = useThemeStore()
const colors = ['#1677ff', '#13a8a8', '#52c41a', '#722ed1', '#eb2f96', '#fa8c16']
const modes: Array<{ label: string; value: ThemeMode }> = [
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' },
  { label: '跟随系统', value: 'system' },
]
</script>

<template>
  <a-popover placement="bottomRight" trigger="click">
    <template #content>
      <div class="theme-panel">
        <div class="theme-label">显示模式</div>
        <a-segmented :value="themeStore.mode" :options="modes" @change="themeStore.setMode($event as ThemeMode)" />
        <div class="theme-label color-label">主题色</div>
        <div class="color-grid">
          <button v-for="color in colors" :key="color" type="button" class="color-swatch" :style="{ backgroundColor: color }" :aria-label="`使用主题色 ${color}`" @click="themeStore.setPrimaryColor(color)">
            <CheckOutlined v-if="themeStore.primaryColor === color" />
          </button>
          <label class="custom-color" title="自定义主题色">
            <input type="color" :value="themeStore.primaryColor" @input="themeStore.setPrimaryColor(($event.target as HTMLInputElement).value)" />+
          </label>
        </div>
      </div>
    </template>
    <a-button type="text" aria-label="主题设置"><BgColorsOutlined /></a-button>
  </a-popover>
</template>

<style scoped>
.theme-panel { width: 280px; }
.theme-label { margin-bottom: 8px; color: var(--nova-text-secondary); font-size: 13px; }
.color-label { margin-top: 18px; }
.color-grid { display: flex; gap: 10px; align-items: center; }
.color-swatch, .custom-color { width: 28px; height: 28px; border: 2px solid rgba(255,255,255,.85); border-radius: 50%; box-shadow: 0 0 0 1px rgba(15,23,42,.16); color: #fff; display: grid; place-items: center; cursor: pointer; }
.custom-color { position: relative; overflow: hidden; color: var(--nova-text-secondary); background: var(--nova-surface); }
.custom-color input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
</style>
