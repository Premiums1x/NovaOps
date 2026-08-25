import type {
  LoginRequestDto,
  RegisterRequestDto,
  RoleDto,
  UserListItemDto,
  UserProfile,
} from '@/types/auth'
import type { MenuDataDto, MenuItemDto } from '@/types/menu'
import type {
  CreateInvitationDto,
  CreatedInvitationDto,
  CreateTenantDto,
  InvitationDto,
  TenantDto,
} from '@/types/system'

type MenuTemplateKey = 'full' | 'staff' | 'guest'

interface MockMembership {
  tenantId: string
  roleCode: string
}

interface MockUser {
  id: string
  username: string
  password: string
  displayName: string
  enabled: boolean
  platformAdmin: boolean
  memberships: MockMembership[]
  createdAt: string
}

interface SessionPayload {
  username: string
  tenantId: string
}

interface MockInvitation extends InvitationDto {
  token: string
}

const initialTenants: TenantDto[] = [
  { code: 'tenant-a', name: 'Tenant A' },
  { code: 'tenant-b', name: 'Tenant B' },
]

const createInitialUsers = (): Record<string, MockUser> => ({
  admin: {
    id: 'u-admin',
    username: 'admin',
    password: '123456',
    displayName: 'System Admin',
    enabled: true,
    platformAdmin: true,
    memberships: [
      { tenantId: 'tenant-a', roleCode: 'admin' },
      { tenantId: 'tenant-b', roleCode: 'admin' },
    ],
    createdAt: '2026-04-01T08:00:00Z',
  },
  staff: {
    id: 'u-staff',
    username: 'staff',
    password: '123456',
    displayName: 'Support Staff',
    enabled: true,
    platformAdmin: false,
    memberships: [
      { tenantId: 'tenant-a', roleCode: 'staff' },
      { tenantId: 'tenant-b', roleCode: 'staff' },
    ],
    createdAt: '2026-04-02T08:00:00Z',
  },
  guest: {
    id: 'u-guest',
    username: 'guest',
    password: '123456',
    displayName: 'Read-only Guest',
    enabled: true,
    platformAdmin: false,
    memberships: [
      { tenantId: 'tenant-a', roleCode: 'guest' },
      { tenantId: 'tenant-b', roleCode: 'guest' },
    ],
    createdAt: '2026-04-03T08:00:00Z',
  },
})

const tenants: TenantDto[] = initialTenants.map((tenant) => ({ ...tenant }))
const users: Record<string, MockUser> = createInitialUsers()
const invitations: MockInvitation[] = []
let userCounter = 0
let invitationCounter = 0

export const roles: RoleDto[] = [
  { id: 'role-admin', code: 'admin', name: '管理员', description: '管理用户、身份、知识库以及全部业务数据', permissions: ['auth:user:manage', 'dashboard:view', 'ticket:view', 'asset:view', 'kb:view', 'kb:edit', 'agent:chat'] },
  { id: 'role-staff', code: 'staff', name: '运维人员', description: '处理工单、资产与使用智能问答', permissions: ['dashboard:view', 'ticket:view', 'ticket:create', 'asset:view', 'agent:chat'] },
  { id: 'role-guest', code: 'guest', name: '访客', description: '只读访问授权看板与智能问答', permissions: ['dashboard:view', 'agent:chat'] },
]

const permissionsByRole: Record<string, string[]> = {
  admin: ['dashboard:view', 'ticket:view', 'ticket:create', 'ticket:edit', 'ticket:assign', 'ticket:transfer', 'ticket:close', 'ticket:comment', 'ticket:advance', 'asset:view', 'asset:create', 'asset:edit', 'asset:claim', 'asset:scrap', 'kb:view', 'kb:edit', 'auth:user:manage', 'agent:chat'],
  staff: ['dashboard:view', 'ticket:view', 'ticket:create', 'ticket:assign', 'ticket:comment', 'ticket:advance', 'asset:view', 'asset:claim', 'agent:chat'],
  guest: ['dashboard:view', 'agent:chat'],
}

const menuTemplates: Record<MenuTemplateKey, MenuItemDto[]> = {
  full: [
    { id: 'dashboard', title: 'Dashboard', name: 'Dashboard', path: '/dashboard', component: 'DashboardView', icon: 'dashboard', permission: 'dashboard:view', keepAlive: true },
    { id: 'ticket', title: '工单', name: 'TicketRoot', path: '/ticket', component: 'RouteView', icon: 'ticket', children: [{ id: 'ticket-list', title: '工单列表', name: 'TicketList', path: '/ticket/list', component: 'TicketListView', permission: 'ticket:view', keepAlive: true }] },
    { id: 'asset', title: '资产', name: 'AssetRoot', path: '/asset', component: 'RouteView', icon: 'asset', children: [{ id: 'asset-list', title: '资产列表', name: 'AssetList', path: '/asset/list', component: 'AssetListView', permission: 'asset:view', keepAlive: true }] },
    { id: 'kb', title: '知识库', name: 'KbRoot', path: '/kb', component: 'RouteView', icon: 'kb', children: [{ id: 'kb-list', title: '文章列表', name: 'KbList', path: '/kb/list', component: 'KbListView', permission: 'kb:view', keepAlive: true }] },
    { id: 'users', title: '用户与身份', name: 'UserManagement', path: '/system/users', component: 'UserManagementView', icon: 'user', permission: 'auth:user:manage', keepAlive: true },
  ],
  staff: [
    { id: 'dashboard', title: 'Dashboard', name: 'Dashboard', path: '/dashboard', component: 'DashboardView', icon: 'dashboard', permission: 'dashboard:view', keepAlive: true },
    { id: 'ticket', title: '工单', name: 'TicketRoot', path: '/ticket', component: 'RouteView', icon: 'ticket', children: [{ id: 'ticket-list', title: '工单列表', name: 'TicketList', path: '/ticket/list', component: 'TicketListView', permission: 'ticket:view', keepAlive: true }] },
  ],
  guest: [{ id: 'dashboard', title: 'Dashboard', name: 'Dashboard', path: '/dashboard', component: 'DashboardView', icon: 'dashboard', permission: 'dashboard:view', keepAlive: true }],
}

const platformMenu: MenuItemDto = {
  id: 'tenant-invitations',
  title: '租户与邀请',
  name: 'TenantInvitationManagement',
  path: '/system/tenants',
  component: 'TenantInvitationManagementView',
  icon: 'user',
  keepAlive: true,
}

const accessTokenTable = new Map<string, SessionPayload>()
const refreshTokenTable = new Map<string, SessionPayload>()

const cloneMenu = (menus: MenuItemDto[]): MenuItemDto[] =>
  menus.map((item) => ({ ...item, children: item.children ? cloneMenu(item.children) : undefined }))

const findTenant = (tenantId: string) => tenants.find((tenant) => tenant.code === tenantId)
const findMembership = (user: MockUser, tenantId: string) =>
  user.memberships.find((membership) => membership.tenantId === tenantId)

export const getUser = (username: string) => users[username]
export const hasMembership = (username: string, tenantId: string) => {
  const user = getUser(username)
  return Boolean(user && findMembership(user, tenantId))
}

export const authenticateMockUser = (
  payload: LoginRequestDto
): { error: string } | { user: MockUser; tenantId: string } => {
  const tenantId = payload.tenantId?.trim() || ''
  if (!findTenant(tenantId)) return { error: '租户不存在' } as const
  const user = getUser(payload.username)
  if (!user || user.password !== payload.password) return { error: '账号或密码错误' } as const
  if (!user.enabled) return { error: '账号已被禁用' } as const
  const membership = findMembership(user, tenantId)
  if (!membership) return { error: '租户无权限访问' } as const
  const selectedRole = roles.find((role) => role.id === payload.roleId)
  if (!selectedRole || selectedRole.code !== membership.roleCode) return { error: '身份不匹配' } as const
  return { user, tenantId } as const
}

const createToken = (prefix: 'at' | 'rt', payload: SessionPayload) =>
  `${prefix}_${payload.username}_${payload.tenantId}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

export const buildSession = (payload: SessionPayload) => {
  const accessToken = createToken('at', payload)
  const refreshToken = createToken('rt', payload)
  accessTokenTable.set(accessToken, payload)
  refreshTokenTable.set(refreshToken, payload)
  return { accessToken, refreshToken, expiresIn: 1800, tenantId: payload.tenantId }
}

export const refreshSession = (refreshToken: string) => {
  const payload = refreshTokenTable.get(refreshToken)
  if (!payload || !hasMembership(payload.username, payload.tenantId)) return null
  const accessToken = createToken('at', payload)
  accessTokenTable.set(accessToken, payload)
  return { accessToken, refreshToken, expiresIn: 1800, tenantId: payload.tenantId }
}

const decodeBase64Url = (value: string) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  return atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))
}

const parseJwtSession = (token: string): SessionPayload | null => {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload = JSON.parse(decodeBase64Url(parts[1] || '')) as SessionPayload
    if (!payload.username || !payload.tenantId || !hasMembership(payload.username, payload.tenantId)) return null
    return payload
  } catch {
    return null
  }
}

export const getSessionFromAccessToken = (authorization: string | null) => {
  if (!authorization?.startsWith('Bearer ')) return null
  const token = authorization.replace('Bearer ', '').trim()
  return accessTokenTable.get(token) || parseJwtSession(token)
}

export const buildUserProfile = (username: string, tenantId: string): UserProfile => {
  const user = getUser(username)
  const membership = user && findMembership(user, tenantId)
  if (!user || !membership) throw new Error('User membership not found')
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    roles: [membership.roleCode],
    permissions: [...(permissionsByRole[membership.roleCode] || [])],
    tenantId,
    tenants: user.memberships.map((item) => {
      const tenant = findTenant(item.tenantId)
      return { id: item.tenantId, name: tenant?.name || item.tenantId }
    }),
    platformAdmin: user.platformAdmin,
  }
}

export const buildMenuData = (username: string, tenantId: string): MenuDataDto => {
  const user = getUser(username)
  const membership = user && findMembership(user, tenantId)
  if (!user || !membership) return { menus: [], permissions: [] }
  const templateKey: MenuTemplateKey = membership.roleCode === 'admin' ? 'full' : membership.roleCode === 'staff' ? 'staff' : 'guest'
  const menus = cloneMenu(menuTemplates[templateKey])
  if (user.platformAdmin) menus.push({ ...platformMenu })
  return { menus, permissions: [...(permissionsByRole[membership.roleCode] || [])] }
}

export const listMockUsers = (tenantId: string): UserListItemDto[] =>
  Object.values(users).flatMap((user) => {
    const membership = findMembership(user, tenantId)
    if (!membership) return []
    const role = roles.find((item) => item.code === membership.roleCode) || roles[2]!
    return [{ id: user.id, username: user.username, displayName: user.displayName, roleId: role.id, roleCode: role.code, roleName: role.name, enabled: user.enabled, createdAt: user.createdAt }]
  })

export const setMockUserStatus = (id: string, enabled: boolean) => {
  const user = Object.values(users).find((item) => item.id === id)
  if (user) user.enabled = enabled
  return Boolean(user)
}

export const setMockUserRole = (id: string, tenantId: string, roleId: string) => {
  const user = Object.values(users).find((item) => item.id === id)
  const membership = user && findMembership(user, tenantId)
  const role = roles.find((item) => item.id === roleId)
  if (membership && role) membership.roleCode = role.code
  return Boolean(membership && role)
}

export const setMockUserPassword = (id: string, password: string) => {
  const user = Object.values(users).find((item) => item.id === id)
  if (user) user.password = password
  return Boolean(user)
}

export const listMockTenants = () => tenants.map((tenant) => ({ ...tenant }))

export const createMockTenant = (username: string, payload: CreateTenantDto) => {
  const user = getUser(username)
  if (!user?.platformAdmin || findTenant(payload.code)) return null
  const tenant = { ...payload }
  tenants.push(tenant)
  user.memberships.push({ tenantId: tenant.code, roleCode: 'admin' })
  return tenant
}

const newInvitationToken = () =>
  `invite_${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`

export const listMockInvitations = (): InvitationDto[] =>
  invitations.map(({ id, tenantId, tenantName, roleCode, createdBy, expiresAt, usedAt, createdAt }) => ({ id, tenantId, tenantName, roleCode, createdBy, expiresAt, usedAt, createdAt }))

export const createMockInvitation = (username: string, payload: CreateInvitationDto): CreatedInvitationDto | null => {
  const user = getUser(username)
  const tenant = findTenant(payload.tenantId)
  if (!user?.platformAdmin || !tenant || !['staff', 'guest'].includes(payload.roleCode)) return null
  invitationCounter += 1
  const createdAt = new Date()
  const invitation: MockInvitation = {
    id: `inv-mock-${invitationCounter}`,
    tenantId: tenant.code,
    tenantName: tenant.name,
    roleCode: payload.roleCode,
    createdBy: user.id,
    expiresAt: new Date(createdAt.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    usedAt: null,
    createdAt: createdAt.toISOString(),
    token: newInvitationToken(),
  }
  invitations.unshift(invitation)
  return { ...invitation }
}

export const registerMockUser = (
  payload: RegisterRequestDto
): { error: string } | { user: MockUser; tenantId: string } => {
  const invitation = invitations.find((item) => item.token === payload.invitationToken)
  if (!invitation) return { error: '邀请无效' } as const
  if (invitation.usedAt) return { error: '邀请已使用' } as const
  if (new Date(invitation.expiresAt).getTime() <= Date.now()) return { error: '邀请已过期' } as const
  if (getUser(payload.username)) return { error: '用户名已存在' } as const
  userCounter += 1
  const user: MockUser = {
    id: `u-invited-${userCounter}`,
    username: payload.username,
    password: payload.password,
    displayName: payload.displayName,
    enabled: true,
    platformAdmin: false,
    memberships: [{ tenantId: invitation.tenantId, roleCode: invitation.roleCode }],
    createdAt: new Date().toISOString(),
  }
  users[user.username] = user
  invitation.usedAt = new Date().toISOString()
  return { user, tenantId: invitation.tenantId } as const
}
