import type { TenantInfo, UserProfile } from '@/types/auth'
import type { MenuDataDto, MenuItemDto } from '@/types/menu'

type Username = 'admin' | 'staff' | 'guest'
type TenantId = 'tenant-a' | 'tenant-b'
type MenuTemplateKey = 'full' | 'staff' | 'guest'

interface MockUser {
  id: string
  username: Username
  password: string
  displayName: string
  roles: string[]
}

interface SessionPayload {
  username: Username
  tenantId: string
}

const tenantList: TenantInfo[] = [
  { id: 'tenant-a', name: 'Tenant A' },
  { id: 'tenant-b', name: 'Tenant B' },
]

const users: Record<Username, MockUser> = {
  admin: {
    id: 'u-admin',
    username: 'admin',
    password: '123456',
    displayName: 'System Admin',
    roles: ['admin'],
  },
  staff: {
    id: 'u-staff',
    username: 'staff',
    password: '123456',
    displayName: 'Support Staff',
    roles: ['staff'],
  },
  guest: {
    id: 'u-guest',
    username: 'guest',
    password: '123456',
    displayName: 'Read-only Guest',
    roles: ['guest'],
  },
}

const permissionMap: Record<Username, Record<TenantId, string[]>> = {
  admin: {
    'tenant-a': [
      'dashboard:view',
      'ticket:view',
      'ticket:create',
      'ticket:edit',
      'ticket:assign',
      'ticket:transfer',
      'ticket:close',
      'ticket:comment',
      'asset:view',
      'kb:view',
    ],
    'tenant-b': [
      'dashboard:view',
      'ticket:view',
      'ticket:create',
      'ticket:edit',
      'ticket:close',
      'ticket:comment',
      'kb:view',
    ],
  },
  staff: {
    'tenant-a': [
      'dashboard:view',
      'ticket:view',
      'ticket:create',
      'ticket:assign',
      'ticket:comment',
      'kb:view',
    ],
    'tenant-b': ['dashboard:view', 'ticket:view', 'ticket:create', 'ticket:comment'],
  },
  guest: {
    'tenant-a': ['dashboard:view'],
    'tenant-b': ['dashboard:view'],
  },
}

const menuTemplates: Record<MenuTemplateKey, MenuItemDto[]> = {
  full: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
    {
      id: 'ticket',
      title: '工单',
      name: 'TicketRoot',
      path: '/ticket',
      component: 'RouteView',
      icon: 'ticket',
      children: [
        {
          id: 'ticket-list',
          title: '工单列表',
          name: 'TicketList',
          path: '/ticket/list',
          component: 'TicketListView',
          permission: 'ticket:view',
          keepAlive: true,
        },
      ],
    },
    {
      id: 'asset',
      title: '资产',
      name: 'AssetRoot',
      path: '/asset',
      component: 'RouteView',
      icon: 'asset',
      children: [
        {
          id: 'asset-list',
          title: '资产列表',
          name: 'AssetList',
          path: '/asset/list',
          component: 'AssetListView',
          permission: 'asset:view',
          keepAlive: true,
        },
      ],
    },
    {
      id: 'kb',
      title: '知识库',
      name: 'KbRoot',
      path: '/kb',
      component: 'RouteView',
      icon: 'kb',
      children: [
        {
          id: 'kb-list',
          title: '文章列表',
          name: 'KbList',
          path: '/kb/list',
          component: 'KbListView',
          permission: 'kb:view',
          keepAlive: true,
        },
      ],
    },
  ],
  staff: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
    {
      id: 'ticket',
      title: '工单',
      name: 'TicketRoot',
      path: '/ticket',
      component: 'RouteView',
      icon: 'ticket',
      children: [
        {
          id: 'ticket-list',
          title: '工单列表',
          name: 'TicketList',
          path: '/ticket/list',
          component: 'TicketListView',
          permission: 'ticket:view',
          keepAlive: true,
        },
      ],
    },
    {
      id: 'kb',
      title: '知识库',
      name: 'KbRoot',
      path: '/kb',
      component: 'RouteView',
      icon: 'kb',
      children: [
        {
          id: 'kb-list',
          title: '文章列表',
          name: 'KbList',
          path: '/kb/list',
          component: 'KbListView',
          permission: 'kb:view',
          keepAlive: true,
        },
      ],
    },
  ],
  guest: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      name: 'Dashboard',
      path: '/dashboard',
      component: 'DashboardView',
      icon: 'dashboard',
      permission: 'dashboard:view',
      keepAlive: true,
    },
  ],
}

const accessTokenTable = new Map<string, SessionPayload>()
const refreshTokenTable = new Map<string, SessionPayload>()

const createToken = (prefix: 'at' | 'rt', payload: SessionPayload) => {
  return `${prefix}_${payload.username}_${payload.tenantId}_${Date.now()}_${Math.random()
    .toString(36)
    .slice(2, 8)}`
}

const normalizeTenantId = (tenantId: string) => {
  return tenantList.some((item) => item.id === tenantId) ? (tenantId as TenantId) : 'tenant-a'
}

const cloneMenu = (menus: MenuItemDto[]): MenuItemDto[] => {
  return menus.map((item) => ({
    ...item,
    children: item.children ? cloneMenu(item.children) : undefined,
  }))
}

const resolveMenusByUser = (username: Username, tenantId: string): MenuItemDto[] => {
  if (username === 'guest') {
    return cloneMenu(menuTemplates.guest)
  }
  if (username === 'staff' || tenantId === 'tenant-b') {
    return cloneMenu(menuTemplates.staff)
  }
  return cloneMenu(menuTemplates.full)
}

export const getUser = (username: string) => {
  return users[username as Username]
}

export const buildSession = (payload: SessionPayload) => {
  const normalizedPayload = { ...payload, tenantId: normalizeTenantId(payload.tenantId) }
  const accessToken = createToken('at', normalizedPayload)
  const refreshToken = createToken('rt', normalizedPayload)
  accessTokenTable.set(accessToken, normalizedPayload)
  refreshTokenTable.set(refreshToken, normalizedPayload)
  return {
    accessToken,
    refreshToken,
    expiresIn: 1800,
    tenantId: normalizedPayload.tenantId,
  }
}

export const refreshSession = (refreshToken: string) => {
  const payload = refreshTokenTable.get(refreshToken)
  if (!payload) {
    return null
  }
  const accessToken = createToken('at', payload)
  accessTokenTable.set(accessToken, payload)
  return {
    accessToken,
    refreshToken,
    expiresIn: 1800,
    tenantId: payload.tenantId,
  }
}

export const switchTenantSession = (authorization: string | null, tenantId: string) => {
  const session = getSessionFromAccessToken(authorization)
  if (!session) {
    return null
  }
  return buildSession({
    username: session.username,
    tenantId,
  })
}

export const getSessionFromAccessToken = (authorization: string | null) => {
  if (!authorization?.startsWith('Bearer ')) {
    return null
  }
  const token = authorization.replace('Bearer ', '').trim()
  return accessTokenTable.get(token) || null
}

export const buildUserProfile = (username: Username, tenantId: string): UserProfile => {
  const currentTenantId = normalizeTenantId(tenantId)
  const user = users[username]
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    roles: user.roles,
    permissions: permissionMap[username][currentTenantId],
    tenantId: currentTenantId,
    tenants: tenantList,
  }
}

export const buildMenuData = (username: Username, tenantId: string): MenuDataDto => {
  const currentTenantId = normalizeTenantId(tenantId)
  return {
    menus: resolveMenusByUser(username, currentTenantId),
    permissions: permissionMap[username][currentTenantId],
  }
}
