import type { TenantInfo, UserProfile } from '@/types/auth'

interface MockUser {
  id: string
  username: 'admin' | 'staff' | 'guest'
  password: string
  displayName: string
  roles: string[]
  permissions: string[]
}

interface SessionPayload {
  username: MockUser['username']
  tenantId: string
}

const tenantList: TenantInfo[] = [
  { id: 'tenant-a', name: 'Tenant A' },
  { id: 'tenant-b', name: 'Tenant B' },
]

const users: Record<MockUser['username'], MockUser> = {
  admin: {
    id: 'u-admin',
    username: 'admin',
    password: '123456',
    displayName: 'System Admin',
    roles: ['admin'],
    permissions: ['dashboard:view', 'ticket:view', 'ticket:create', 'ticket:edit'],
  },
  staff: {
    id: 'u-staff',
    username: 'staff',
    password: '123456',
    displayName: 'Support Staff',
    roles: ['staff'],
    permissions: ['dashboard:view', 'ticket:view', 'ticket:create'],
  },
  guest: {
    id: 'u-guest',
    username: 'guest',
    password: '123456',
    displayName: 'Read-only Guest',
    roles: ['guest'],
    permissions: ['dashboard:view'],
  },
}

const accessTokenTable = new Map<string, SessionPayload>()
const refreshTokenTable = new Map<string, SessionPayload>()

const createToken = (prefix: 'at' | 'rt', payload: SessionPayload) => {
  return `${prefix}_${payload.username}_${payload.tenantId}_${Date.now()}_${Math.random()
    .toString(36)
    .slice(2, 8)}`
}

export const getUser = (username: string) => {
  return users[username as MockUser['username']]
}

export const buildSession = (payload: SessionPayload) => {
  const accessToken = createToken('at', payload)
  const refreshToken = createToken('rt', payload)
  accessTokenTable.set(accessToken, payload)
  refreshTokenTable.set(refreshToken, payload)
  return {
    accessToken,
    refreshToken,
    expiresIn: 1800,
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
  }
}

export const getSessionFromAccessToken = (authorization: string | null) => {
  if (!authorization?.startsWith('Bearer ')) {
    return null
  }
  const token = authorization.replace('Bearer ', '').trim()
  return accessTokenTable.get(token) || null
}

export const buildUserProfile = (username: MockUser['username'], tenantId: string): UserProfile => {
  const user = users[username]
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    roles: user.roles,
    permissions: user.permissions,
    tenantId,
    tenants: tenantList,
  }
}
