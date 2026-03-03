import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('echarts')) {
            return 'echarts'
          }
          if (id.includes('ant-design-vue') || id.includes('@ant-design')) {
            return 'antdv'
          }
          if (id.includes('axios') || id.includes('pinia') || id.includes('vue-router')) {
            return 'core-vendors'
          }
        },
      },
    },
  },
})
