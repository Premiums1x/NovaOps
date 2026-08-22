import type { RoleDto, TenantInfo, UserProfile } from '@/types/auth'
import type { MenuDataDto, MenuItemDto } from '@/types/menu'

type TenantId = 'tenant-a' | 'tenant-b'
type MenuTemplateKey = 'full' | 'staff' | 'guest'

interface MockUser {
  id: string
  username: string
  password: string
  displayName: string
  roles: string[]
}

interface SessionPayload {
  username: string
  tenantId: string
}

const tenantList: TenantInfo[] = [
  { id: 'tenant-a', name: 'Tenant A' },
  { id: 'tenant-b', name: 'Tenant B' },
]

const users: Record<string, MockUser> = {
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

// 动态注册的用户
const dynamicUsers = new Map<string, MockUser>()

let userCounter = 0

export const roles: RoleDto[] = [
  { id: 'role-admin', code: 'admin', name: '管理员' },
  { id: 'role-staff', code: 'staff', name: '运维人员' },
  { id: 'role-guest', code: 'guest', name: '访客' },
]

export const createDynamicUser = (username: string, roleId: string): MockUser => {
  if (users[username]) {
    return users[username]
  }
  const role = roles.find(r => r.id === roleId)
  if (!role) {
    throw new Error(`Role not found: ${roleId}`)
  }
  const roleCode = role.code
  userCounter++
  const user: MockUser = {
    id: `u-dyn-${userCounter}`,
    username,
    password: '123456',
    displayName: username,
    roles: [roleCode],
  }
  dynamicUsers.set(username, user)
  return user
}

const permissionMap: Record<string, Record<TenantId, string[]>> = {
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
      'asset:create',
      'asset:edit',
      'asset:claim',
      'asset:scrap',
      'kb:view',
      'kb:edit',
    ],
    'tenant-b': [
      'dashboard:view',
      'ticket:view',
      'ticket:create',
      'ticket:edit',
      'ticket:close',
      'ticket:comment',
      'kb:view',
      'asset:view',
      'asset:create',
      'asset:edit',
      'asset:claim',
      'asset:scrap',
      'kb:edit',
    ],
  },
  staff: {
    'tenant-a': [
      'dashboard:view',
      'ticket:view',
      'ticket:create',
      'ticket:assign',
      'ticket:comment',
      'asset:view',
      'asset:claim',
      'kb:view',
      'kb:edit',
    ],
    'tenant-b': ['dashboard:view', 'ticket:view', 'ticket:create', 'ticket:comment', 'asset:view', 'kb:view', 'kb:edit'],
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

const decodeBase64Url = (value: string) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  return atob(padded)
}

const parseJwtSession = (token: string): SessionPayload | null => {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payload = JSON.parse(decodeBase64Url(parts[1] || '')) as {
      username?: string
      tenantId?: string
    }
    const username = payload.username
    if (!username) {
      return null
    }
    return {
      username,
      tenantId: normalizeTenantId(payload.tenantId || 'tenant-a'),
    }
  } catch {
    return null
  }
}

const cloneMenu = (menus: MenuItemDto[]): MenuItemDto[] => {
  return menus.map((item) => ({
    ...item,
    children: item.children ? cloneMenu(item.children) : undefined,
  }))
}

const resolveMenusByUser = (username: string, tenantId: string): MenuItemDto[] => {
  const user = users[username] || dynamicUsers.get(username)
  const roleCode = user?.roles?.[0]
  if (roleCode === 'guest') {
    return cloneMenu(menuTemplates.guest)
  }
  if (roleCode === 'staff' || tenantId === 'tenant-b') {
    return cloneMenu(menuTemplates.staff)
  }
  return cloneMenu(menuTemplates.full)
}

export const getUser = (username: string) => {
  return users[username] || dynamicUsers.get(username)
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
  const mockSession = accessTokenTable.get(token)
  if (mockSession) {
    return mockSession
  }
  return parseJwtSession(token)
}

// 按「用户名 → 角色码」的优先级取权限表,兜底用 admin 权限,保证一定有返回值
const resolvePermissions = (username: string, roleCode: string, tenantId: TenantId): string[] =>
  permissionMap[username]?.[tenantId] || permissionMap[roleCode]?.[tenantId] || permissionMap.admin?.[tenantId] || []

export const buildUserProfile = (username: string, tenantId: string): UserProfile => {
  const currentTenantId = normalizeTenantId(tenantId)
  const user = users[username] || dynamicUsers.get(username)
  if (!user) {
    throw new Error(`User not found: ${username}`)
  }
  const roleCode = user.roles[0] || 'admin'
  const perms = resolvePermissions(username, roleCode, currentTenantId)
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    roles: user.roles,
    permissions: perms,
    tenantId: currentTenantId,
    tenants: tenantList,
  }
}

export const buildMenuData = (username: string, tenantId: string): MenuDataDto => {
  const currentTenantId = normalizeTenantId(tenantId)
  const user = users[username] || dynamicUsers.get(username)
  const roleCode = user?.roles?.[0] || 'admin'
  const perms = resolvePermissions(username, roleCode, currentTenantId)
  return {
    menus: resolveMenusByUser(username, currentTenantId),
    permissions: perms,
  }
}
