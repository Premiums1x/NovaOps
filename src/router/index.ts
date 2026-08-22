import { createRouter, createWebHistory } from 'vue-router'
import { setupRouterGuard } from './guard'
import { staticRoutes } from './staticRoutes'

//创建路由器 
const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

//设置守卫 — 把 router 实例传给 setupRouterGuard，注册全局导航拦截
setupRouterGuard(router)

//导出 — 其他地方 import router 使用
export default router
