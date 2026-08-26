import { describe, expect, it, vi } from 'vitest'
import { getUser, registerMockUser, verifyMockUser } from './db'

describe('full mock email registration', () => {
  it('creates a disabled member and activates it with the generated token', () => {
    const stamp = Date.now()
    const username = `member-${stamp}`
    const email = `member-${stamp}@example.com`
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})

    registerMockUser(username, email, 'strong-password')

    expect(getUser(username)).toMatchObject({
      username,
      password: 'strong-password',
      roles: ['member'],
      enabled: false,
    })
    const activationUrl = String(log.mock.calls[0]?.[0] || '').match(/https?:\/\/\S+/)?.[0]
    const token = activationUrl ? new URL(activationUrl).searchParams.get('token') : null
    expect(token).toBeTruthy()
    expect(verifyMockUser(token!)).toBe(true)
    expect(getUser(username)?.enabled).toBe(true)
    expect(verifyMockUser(token!)).toBe(false)

    log.mockRestore()
  })

  it('rejects duplicate accounts and email addresses', () => {
    const stamp = Date.now()
    const username = `duplicate-${stamp}`
    const email = `duplicate-${stamp}@example.com`
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})

    registerMockUser(username, email, 'strong-password')
    expect(() => registerMockUser(username, `${stamp}-other@example.com`, 'strong-password')).toThrow('账号已存在')
    expect(() => registerMockUser(`${username}-other`, email, 'strong-password')).toThrow('邮箱已被注册')

    log.mockRestore()
  })
})
