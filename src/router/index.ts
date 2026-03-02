import { createRouter, createWebHistory } from 'vue-router'
import { setupRouterGuard } from './guard'
import { staticRoutes } from './staticRoutes'

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

setupRouterGuard(router)

export default router
