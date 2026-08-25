import { describe, expect, it } from 'vitest'
import {
  authenticateMockUser,
  buildMenuData,
  buildUserProfile,
  createMockInvitation,
  registerMockUser,
} from './db'

describe('full mock tenant authentication', () => {
  it('rejects unknown users, unknown tenants, and mismatched membership roles', () => {
    expect(authenticateMockUser({ username: 'missing', password: '123456', tenantId: 'tenant-a', roleId: 'role-staff' })).toEqual({ error: '账号或密码错误' })
    expect(authenticateMockUser({ username: 'staff', password: '123456', tenantId: 'unknown-tenant', roleId: 'role-staff' })).toEqual({ error: '租户不存在' })
    expect(authenticateMockUser({ username: 'staff', password: '123456', tenantId: 'tenant-a', roleId: 'role-guest' })).toEqual({ error: '身份不匹配' })
  })

  it('registers from an invitation and reuses the submitted password and membership', () => {
    const created = createMockInvitation('admin', { tenantId: 'tenant-b', roleCode: 'guest' })
    expect(created?.token).toBeTruthy()
    const username = `invited-${Date.now()}`
    const result = registerMockUser({
      invitationToken: created!.token,
      username,
      displayName: 'Invited Guest',
      password: 'my-secure-password',
    })
    expect('error' in result).toBe(false)
    expect(authenticateMockUser({ username, password: '123456', tenantId: 'tenant-b', roleId: 'role-guest' })).toEqual({ error: '账号或密码错误' })
    expect(authenticateMockUser({ username, password: 'my-secure-password', tenantId: 'tenant-b', roleId: 'role-guest' })).not.toHaveProperty('error')
    expect(buildUserProfile(username, 'tenant-b').tenants).toEqual([{ id: 'tenant-b', name: 'Tenant B' }])
    expect(authenticateMockUser({ username, password: 'my-secure-password', tenantId: 'tenant-a', roleId: 'role-guest' })).toEqual({ error: '租户无权限访问' })
  })

  it('shows platform management only to platform administrators', () => {
    expect(buildMenuData('admin', 'tenant-a').menus.some((item) => item.path === '/system/tenants')).toBe(true)
    expect(buildMenuData('staff', 'tenant-a').menus.some((item) => item.path === '/system/tenants')).toBe(false)
  })
})
