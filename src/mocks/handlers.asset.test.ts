import { setupServer, type SetupServerApi } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

let server: SetupServerApi
let buildSession: typeof import('./db').buildSession

beforeAll(async () => {
  vi.stubEnv('VITE_ENABLE_MOCK', 'full')
  vi.resetModules()
  const db = await import('./db')
  const { handlers } = await import('./handlers')
  buildSession = db.buildSession
  server = setupServer(...handlers)
  server.listen({ onUnhandledRequest: 'error' })
})
afterEach(() => server.resetHandlers())
afterAll(() => {
  server.close()
  vi.unstubAllEnvs()
})

const getUserOptions = async (username: string) => {
  const session = buildSession({ username })
  return fetch('http://localhost:3000/api/auth/user-options', {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
}

describe('asset-related mock handlers', () => {
  it('allows staff with asset:claim to list enabled user options', async () => {
    const response = await getUserOptions('staff')
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.code).toBe(0)
    expect(body.data).toEqual(
      expect.arrayContaining([
        { id: 'u-staff', username: 'staff', displayName: 'Support Staff' },
      ])
    )
    expect(body.data.every((user: { enabled?: boolean }) => user.enabled === undefined)).toBe(true)
  })

  it('denies users without asset:claim', async () => {
    const response = await getUserOptions('guest')
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.code).toBe(403)
    expect(body.message).toBe('无权限执行该操作')
  })
})
