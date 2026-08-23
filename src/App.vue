<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { ConfigProvider, theme } from 'ant-design-vue'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()
const themeConfig = computed(() => ({
  algorithm: themeStore.resolvedMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
  token: { colorPrimary: themeStore.primaryColor, borderRadius: 8 },
}))

onMounted(themeStore.initialize)
onUnmounted(themeStore.dispose)
</script>

<template>
  <ConfigProvider :theme="themeConfig">
    <RouterView />
  </ConfigProvider>
</template>
