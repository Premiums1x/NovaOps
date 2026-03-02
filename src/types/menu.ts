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
