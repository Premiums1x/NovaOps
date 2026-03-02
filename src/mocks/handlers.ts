import { delay, http, HttpResponse } from 'msw'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, UserProfile } from '@/types/auth'
import type { MenuDataDto } from '@/types/menu'
import {
  buildMenuData,
  buildSession,
  buildUserProfile,
  getSessionFromAccessToken,
  getUser,
  refreshSession,
  switchTenantSession,
} from './db'

const ok = <T>(data: T, message = 'ok') => {
  return HttpResponse.json({
    code: 0,
    message,
    data,
  })
}

const fail = (code: number, message: string) => {
  return HttpResponse.json({
    code,
    message,
    data: null,
  })
}

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    await delay(350)
    const payload = (await request.json()) as LoginRequestDto
    const user = getUser(payload.username)
    if (!user || payload.password !== user.password) {
      return fail(403, '用户名或密码错误')
    }

    const tenantId = payload.tenantId || 'tenant-a'
    const session = buildSession({
      username: user.username,
      tenantId,
    })
    const data: LoginResponseDto = {
      ...session,
      tenantId: session.tenantId,
    }
    return ok(data, '登录成功')
  }),

  http.post('/api/auth/refresh', async ({ request }) => {
    await delay(250)
    const payload = (await request.json()) as { refreshToken: string }
    const nextSession = refreshSession(payload.refreshToken)
    if (!nextSession) {
      return fail(401, 'refresh token 已失效')
    }
    return ok<AuthTokenDto>(nextSession, '刷新成功')
  }),

  http.post('/api/auth/switch-tenant', async ({ request }) => {
    await delay(220)
    const payload = (await request.json()) as { tenantId: string }
    const nextSession = switchTenantSession(request.headers.get('Authorization'), payload.tenantId)
    if (!nextSession) {
      return fail(401, 'token 无效，无法切换租户')
    }
    const data: LoginResponseDto = {
      ...nextSession,
      tenantId: nextSession.tenantId,
    }
    return ok(data, '租户切换成功')
  }),

  http.get('/api/auth/me', async ({ request }) => {
    await delay(200)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const profile: UserProfile = buildUserProfile(session.username, session.tenantId)
    return ok(profile)
  }),

  http.get('/api/auth/menu', async ({ request }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const menuData: MenuDataDto = buildMenuData(session.username, session.tenantId)
    return ok(menuData)
  }),
]
