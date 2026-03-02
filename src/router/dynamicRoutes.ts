import type { RouteRecordRaw } from 'vue-router'
import type { MenuItemDto } from '@/types/menu'

type RouteComponentLoader = () => Promise<unknown>

const routeComponentMap: Record<string, RouteComponentLoader> = {
  DashboardView: () => import('@/views/dashboard/index.vue'),
  TicketListView: () => import('@/views/ticket/list.vue'),
  TicketDetailView: () => import('@/views/ticket/detail.vue'),
  AssetListView: () => import('@/views/asset/list.vue'),
  KbListView: () => import('@/views/kb/list.vue'),
}

const isRouteLeaf = (menu: MenuItemDto) => menu.component !== 'RouteView'

export const transformMenuToRoutes = (menus: MenuItemDto[]): RouteRecordRaw[] => {
  const routes: RouteRecordRaw[] = []

  const travel = (nodes: MenuItemDto[]) => {
    nodes.forEach((node) => {
      if (isRouteLeaf(node)) {
        const component = routeComponentMap[node.component]
        if (component) {
          routes.push({
            path: node.path,
            name: node.name,
            component,
            meta: {
              title: node.title,
              permission: node.permission,
              keepAlive: node.keepAlive ?? true,
            },
          })
        }
      }

      if (node.children?.length) {
        travel(node.children)
      }
    })
  }

  travel(menus)
  return routes
}
