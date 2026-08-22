//定义后端菜单接口返回的数据结构。
//后端接口 /auth/menu 返回菜单数据，前端拿到后渲染成侧边栏。
export interface MenuItemDto {
  id: string
  title: string
  name: string
  path: string
  component: string
  icon?: string
  permission?: string
  keepAlive?: boolean
  children?: MenuItemDto[]
}

export interface MenuDataDto {
  menus: MenuItemDto[]
  permissions: string[]
}
