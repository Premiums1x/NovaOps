import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@/styles/index.css'
import App from './App.vue'
import router from '@/router'
import { pinia } from '@/store'
import { permissionDirective } from '@/directives/permission'
import Permission from '@/components/Permission.vue'

const enableMock = async () => {
  if (!import.meta.env.DEV) {
    return
  }
  const { worker } = await import('@/mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

const bootstrap = async () => {
  await enableMock()
  const app = createApp(App)
  app.use(Antd)
  app.use(pinia)
  app.use(router)
  app.directive('permission', permissionDirective)
  app.component('Permission', Permission)
  app.mount('#app')
}

void bootstrap()
