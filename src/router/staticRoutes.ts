import type { RouteRecordRaw } from 'vue-router'

export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Root',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: {
      requiresAuth: true,
    },
    children: [
      {
        path: '/ticket/detail/:id',
        name: 'TicketDetail',
        component: () => import('@/views/ticket/detail.vue'),
        meta: {
          title: '工单详情',
          permission: 'ticket:view',
          keepAlive: false,
        },
      },
      {
        path: '/asset/detail/:id',
        name: 'AssetDetail',
        component: () => import('@/views/asset/detail.vue'),
        meta: {
          title: '资产详情',
          permission: 'asset:view',
          keepAlive: false,
        },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      public: true,
    },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/403/index.vue'),
    meta: {
      title: '403',
      public: true,
    },
  },
  {
    path: '/404',
    name: 'NotFoundPage',
    component: () => import('@/views/404/index.vue'),
    meta: {
      title: '404',
      public: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/404/index.vue'),
    meta: {
      title: '404',
      public: true,
    },
  },
]
