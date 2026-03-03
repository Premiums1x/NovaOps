import dayjs from 'dayjs'
import type { PageResult } from '@/types/api'
import type { KbDetailDto, KbListItemDto, KbListQueryDto, KbVersionDto, SaveKbDto } from '@/types/kb'

type TenantId = 'tenant-a' | 'tenant-b'

interface KbRecord extends KbDetailDto {
  versions: KbVersionDto[]
}

const records: KbRecord[] = []

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const createVersion = (
  title: string,
  tags: string[],
  content: string,
  editor: string,
  createdAt?: string
): KbVersionDto => ({
  id: `v_${Math.random().toString(36).slice(2, 8)}_${Date.now()}`,
  title,
  tags: [...tags],
  content,
  editor,
  createdAt: createdAt || dayjs().toISOString(),
})

const seed = () => {
  if (records.length > 0) {
    return
  }
  ;(['tenant-a', 'tenant-b'] as TenantId[]).forEach((tenantId) => {
    for (let i = 1; i <= 12; i += 1) {
      const id = `${tenantId.toUpperCase()}-KB-${String(i).padStart(3, '0')}`
      const title = `${tenantId.toUpperCase()} 运维手册 #${i}`
      const tags = i % 2 === 0 ? ['网络', '流程'] : ['终端', '规范']
      const baseDate = dayjs().subtract(i * 3, 'day')
      const contentV1 = `# ${title}\n\n## 背景\n\n初版说明文档（V1）。\n\n## 操作\n\n- 步骤1\n- 步骤2`
      const contentV2 = `${contentV1}\n\n## 变更记录\n\n- 优化检查项`
      const contentV3 = `${contentV2}\n\n## 注意事项\n\n- 变更窗口请提前申请`
      records.push({
        id,
        tenantId,
        title,
        tags,
        author: tenantId === 'tenant-a' ? 'admin' : 'staff',
        updatedAt: baseDate.add(2, 'day').toISOString(),
        content: contentV3,
        versions: [
          createVersion(title, tags, contentV3, 'admin', baseDate.add(2, 'day').toISOString()),
          createVersion(title, tags, contentV2, 'admin', baseDate.add(1, 'day').toISOString()),
          createVersion(title, tags, contentV1, 'admin', baseDate.toISOString()),
        ],
      })
    }
  })
}

seed()

const toListItem = (record: KbRecord): KbListItemDto => ({
  id: record.id,
  title: record.title,
  tags: record.tags,
  author: record.author,
  updatedAt: record.updatedAt,
})

const toDetail = (record: KbRecord): KbDetailDto => ({
  id: record.id,
  tenantId: record.tenantId,
  title: record.title,
  tags: [...record.tags],
  author: record.author,
  updatedAt: record.updatedAt,
  content: record.content,
})

const findRecord = (tenantId: string, id: string) => {
  return records.find((record) => record.tenantId === tenantId && record.id === id) || null
}

export const queryKbListByTenant = (
  tenantId: string,
  query: KbListQueryDto
): PageResult<KbListItemDto> => {
  const page = Math.max(Number(query.page || 1), 1)
  const pageSize = Math.max(Number(query.pageSize || 10), 1)

  let filtered = records.filter((record) => record.tenantId === tenantId)
  if (query.keyword) {
    const keyword = query.keyword.toLowerCase()
    filtered = filtered.filter((record) => {
      return (
        record.title.toLowerCase().includes(keyword) ||
        record.content.toLowerCase().includes(keyword) ||
        record.tags.some((tag) => tag.toLowerCase().includes(keyword))
      )
    })
  }
  if (query.tag) {
    filtered = filtered.filter((record) => record.tags.includes(query.tag as string))
  }

  const sorted = filtered.sort((a, b) => dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf())
  const total = sorted.length
  const startIndex = (page - 1) * pageSize
  const list = sorted.slice(startIndex, startIndex + pageSize).map(toListItem)
  return clone({
    list,
    page,
    pageSize,
    total,
  })
}

export const getKbDetailByTenant = (tenantId: string, id: string): KbDetailDto | null => {
  const record = findRecord(tenantId, id)
  if (!record) {
    return null
  }
  return clone(toDetail(record))
}

export const getKbVersionsByTenant = (tenantId: string, id: string): KbVersionDto[] | null => {
  const record = findRecord(tenantId, id)
  if (!record) {
    return null
  }
  const sorted = [...record.versions].sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf())
  return clone(sorted)
}

export const saveKbByTenant = (tenantId: string, username: string, payload: SaveKbDto): KbDetailDto => {
  const now = dayjs().toISOString()
  if (payload.id) {
    const existing = findRecord(tenantId, payload.id)
    if (existing) {
      existing.versions.unshift(
        createVersion(existing.title, existing.tags, existing.content, username, now)
      )
      existing.versions = existing.versions.slice(0, 20)
      existing.title = payload.title
      existing.tags = payload.tags
      existing.content = payload.content
      existing.author = username
      existing.updatedAt = now
      return clone(toDetail(existing))
    }
  }

  const id = `${tenantId.toUpperCase()}-KB-${String(records.length + 101).padStart(3, '0')}`
  const created: KbRecord = {
    id,
    tenantId,
    title: payload.title,
    tags: payload.tags,
    author: username,
    updatedAt: now,
    content: payload.content,
    versions: [
      createVersion(payload.title, payload.tags, payload.content, username, now),
      createVersion(payload.title, payload.tags, '# 初稿\n\n待完善', username, dayjs(now).subtract(1, 'hour').toISOString()),
      createVersion(payload.title, payload.tags, '# 初始化模板', username, dayjs(now).subtract(2, 'hour').toISOString()),
    ],
  }
  records.unshift(created)
  return clone(toDetail(created))
}
