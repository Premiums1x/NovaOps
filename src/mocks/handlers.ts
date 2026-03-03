import { delay, http, HttpResponse } from 'msw'
import type { AuthTokenDto, LoginRequestDto, LoginResponseDto, UserProfile } from '@/types/auth'
import type { MenuDataDto } from '@/types/menu'
import type {
  AssetActionDto,
  AssetDetailDto,
  AssetListQueryDto,
  CreateAssetDto,
  UpdateAssetDto,
} from '@/types/asset'
import type {
  CreateCommentDto,
  CreateTicketDto,
  TicketActionDto,
  TicketDetailDto,
  TicketListQueryDto,
  UpdateTicketDto,
  UploadAttachmentDto,
} from '@/types/ticket'
import type { DashboardMetricsQueryDto } from '@/types/dashboard'
import type { KbListQueryDto, SaveKbDto } from '@/types/kb'
import {
  buildMenuData,
  buildSession,
  buildUserProfile,
  getSessionFromAccessToken,
  getUser,
  refreshSession,
  switchTenantSession,
} from './db'
import {
  buildDashboardMetrics,
} from './dashboardDb'
import {
  getKbDetailByTenant,
  getKbVersionsByTenant,
  queryKbListByTenant,
  saveKbByTenant,
} from './kbDb'
import {
  actionAssetByTenant,
  createAssetByTenant,
  getAssetDetailByTenant,
  getAssetsByIdsByTenant,
  queryAssetsByTenant,
  updateAssetByTenant,
} from './assetDb'
import {
  actionTicketByTenant,
  createTicketByTenant,
  createTicketCommentByTenant,
  getTicketDetailByTenant,
  listRelatedTicketsByAsset,
  listTicketCommentsByTenant,
  queryTicketsByTenant,
  updateTicketByTenant,
  uploadTicketAttachmentByTenant,
} from './ticketDb'

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

  http.get('/api/tickets', async ({ request }) => {
    await delay(280)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: TicketListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      status: (url.searchParams.get('status') || undefined) as TicketListQueryDto['status'],
      priority: (url.searchParams.get('priority') || undefined) as TicketListQueryDto['priority'],
      keyword: url.searchParams.get('keyword') || undefined,
      startDate: url.searchParams.get('startDate') || undefined,
      endDate: url.searchParams.get('endDate') || undefined,
    }
    return ok(queryTicketsByTenant(session.tenantId, query))
  }),

  http.get('/api/tickets/:id', async ({ request, params }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const ticket = getTicketDetailByTenant(session.tenantId, String(params.id))
    if (!ticket) {
      return fail(404, '工单不存在')
    }
    return ok<TicketDetailDto>(ticket)
  }),

  http.post('/api/tickets', async ({ request }) => {
    await delay(260)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateTicketDto
    if (!payload.title || !payload.description) {
      return fail(400, '标题与描述为必填项')
    }
    const created = createTicketByTenant(session.tenantId, session.username, payload)
    return ok(created, '工单创建成功')
  }),

  http.put('/api/tickets/:id', async ({ request, params }) => {
    await delay(240)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as UpdateTicketDto
    const updated = updateTicketByTenant(session.tenantId, String(params.id), session.username, payload)
    if (!updated) {
      return fail(404, '工单不存在')
    }
    return ok(updated, '工单更新成功')
  }),

  http.post('/api/tickets/:id/actions', async ({ request, params }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as TicketActionDto
    const updated = actionTicketByTenant(session.tenantId, String(params.id), session.username, payload)
    if (!updated) {
      return fail(404, '工单不存在')
    }
    return ok(updated, '工单流转成功')
  }),

  http.get('/api/tickets/:id/comments', async ({ request, params }) => {
    await delay(160)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const comments = listTicketCommentsByTenant(session.tenantId, String(params.id))
    if (!comments) {
      return fail(404, '工单不存在')
    }
    return ok(comments)
  }),

  http.post('/api/tickets/:id/comments', async ({ request, params }) => {
    await delay(180)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateCommentDto
    if (!payload.content?.trim()) {
      return fail(400, '评论内容不能为空')
    }
    const created = createTicketCommentByTenant(
      session.tenantId,
      String(params.id),
      session.username,
      payload
    )
    if (!created) {
      return fail(404, '工单不存在')
    }
    return ok(created, '评论创建成功')
  }),

  http.post('/api/tickets/:id/attachments', async ({ request, params }) => {
    await delay(240)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as UploadAttachmentDto
    if (!payload.filename) {
      return fail(400, '附件名称不能为空')
    }
    const attachment = uploadTicketAttachmentByTenant(session.tenantId, String(params.id), payload)
    if (!attachment) {
      return fail(404, '工单不存在')
    }
    return ok(attachment, '附件上传成功')
  }),

  http.get('/api/assets', async ({ request }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: AssetListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      status: (url.searchParams.get('status') || undefined) as AssetListQueryDto['status'],
      type: (url.searchParams.get('type') || undefined) as AssetListQueryDto['type'],
      keyword: url.searchParams.get('keyword') || undefined,
    }
    return ok(queryAssetsByTenant(session.tenantId, query))
  }),

  http.get('/api/assets/batch', async ({ request }) => {
    await delay(180)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const ids = (url.searchParams.get('ids') || '')
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean)
    return ok(getAssetsByIdsByTenant(session.tenantId, ids))
  }),

  http.get('/api/assets/:id', async ({ request, params }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const asset = getAssetDetailByTenant(session.tenantId, String(params.id))
    if (!asset) {
      return fail(404, '资产不存在')
    }
    const detail: AssetDetailDto = {
      ...asset,
      relatedTickets: listRelatedTicketsByAsset(session.tenantId, asset.id),
    }
    return ok(detail)
  }),

  http.post('/api/assets', async ({ request }) => {
    await delay(240)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as CreateAssetDto
    if (!payload.name || !payload.spec) {
      return fail(400, '资产名称与规格为必填项')
    }
    const asset = createAssetByTenant(session.tenantId, payload)
    const detail: AssetDetailDto = {
      ...asset,
      relatedTickets: [],
    }
    return ok(detail, '资产入库成功')
  }),

  http.put('/api/assets/:id', async ({ request, params }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as UpdateAssetDto
    const updated = updateAssetByTenant(session.tenantId, String(params.id), payload)
    if (!updated) {
      return fail(404, '资产不存在')
    }
    const detail: AssetDetailDto = {
      ...updated,
      relatedTickets: listRelatedTicketsByAsset(session.tenantId, updated.id),
    }
    return ok(detail, '资产更新成功')
  }),

  http.post('/api/assets/:id/actions', async ({ request, params }) => {
    await delay(200)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as AssetActionDto
    const updated = actionAssetByTenant(session.tenantId, String(params.id), session.username, payload)
    if (!updated) {
      return fail(404, '资产不存在')
    }
    const detail: AssetDetailDto = {
      ...updated,
      relatedTickets: listRelatedTicketsByAsset(session.tenantId, updated.id),
    }
    return ok(detail, '资产状态更新成功')
  }),

  http.get('/api/dashboard/metrics', async ({ request }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: DashboardMetricsQueryDto = {
      startDate: url.searchParams.get('startDate') || undefined,
      endDate: url.searchParams.get('endDate') || undefined,
    }
    return ok(buildDashboardMetrics(session.tenantId, query.startDate, query.endDate))
  }),

  http.get('/api/kb', async ({ request }) => {
    await delay(220)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const url = new URL(request.url)
    const query: KbListQueryDto = {
      page: Number(url.searchParams.get('page') || 1),
      pageSize: Number(url.searchParams.get('pageSize') || 10),
      keyword: url.searchParams.get('keyword') || undefined,
      tag: url.searchParams.get('tag') || undefined,
    }
    return ok(queryKbListByTenant(session.tenantId, query))
  }),

  http.get('/api/kb/:id/versions', async ({ request, params }) => {
    await delay(200)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const versions = getKbVersionsByTenant(session.tenantId, String(params.id))
    if (!versions) {
      return fail(404, '文章不存在')
    }
    return ok(versions)
  }),

  http.get('/api/kb/:id', async ({ request, params }) => {
    await delay(180)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const detail = getKbDetailByTenant(session.tenantId, String(params.id))
    if (!detail) {
      return fail(404, '文章不存在')
    }
    return ok(detail)
  }),

  http.post('/api/kb/save', async ({ request }) => {
    await delay(240)
    const session = getSessionFromAccessToken(request.headers.get('Authorization'))
    if (!session) {
      return fail(401, 'token 无效')
    }
    const payload = (await request.json()) as SaveKbDto
    if (!payload.title || !payload.content) {
      return fail(400, '标题和正文不能为空')
    }
    return ok(saveKbByTenant(session.tenantId, session.username, payload), '保存成功')
  }),
]
