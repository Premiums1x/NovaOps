import dayjs from 'dayjs'
import type { PageResult } from '@/types/api'
import type {
  AssetActionDto,
  AssetDetailDto,
  AssetListItemDto,
  AssetListQueryDto,
  AssetSimpleDto,
  AssetStatus,
  AssetType,
  CreateAssetDto,
  UpdateAssetDto,
} from '@/types/asset'
import { listMockUsers } from './db'

type TenantId = 'tenant-a' | 'tenant-b'

interface InternalAsset extends Omit<AssetDetailDto, 'relatedTickets'> {
  tenantId: string
  history: string[]
}

const tenants: TenantId[] = ['tenant-a', 'tenant-b']
const assetTypes: AssetType[] = ['server', 'laptop', 'network', 'license']
const assetStatuses: AssetStatus[] = ['stock', 'in_use', 'scrapped']
const locations = ['上海机房-A区', '北京机房-B区', '深圳办公区', '杭州容灾机房']

const assets: InternalAsset[] = []

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const enabledUsers = () => listMockUsers().filter((user) => user.enabled)
const findEnabledUser = (userId: string) =>
  enabledUsers().find((user) => user.id === userId) || null

const ensureSeed = () => {
  if (assets.length) {
    return
  }
  let index = 1
  tenants.forEach((tenantId) => {
    for (let i = 1; i <= 180; i += 1) {
      const type = assetTypes[i % assetTypes.length] || 'server'
      const status = assetStatuses[i % assetStatuses.length] || 'stock'
      const createdAt = dayjs().subtract(i % 60, 'day')
      const ownerCandidates = enabledUsers()
      const owner = status === 'in_use' ? ownerCandidates[i % ownerCandidates.length] || null : null
      assets.push({
        id: `ASSET-${i}`,
        tenantId,
        assetNo: `ASSET-${tenantId === 'tenant-a' ? 'A' : 'B'}-${String(i).padStart(4, '0')}`,
        name: `${tenantId === 'tenant-a' ? 'A' : 'B'}-${type.toUpperCase()}-${String(index).padStart(3, '0')}`,
        type,
        status,
        ownerId: owner?.id || null,
        ownerName: owner?.displayName || null,
        location: locations[i % locations.length] || '上海机房-A区',
        purchaseDate: createdAt.subtract(120, 'day').format('YYYY-MM-DD'),
        updatedAt: createdAt.toISOString(),
        spec: `${type} spec ${i} / CPU ${(i % 8) + 1}C / RAM ${(i % 16) + 8}G`,
        remark: '自动生成的 Mock 资产',
        history: [`${dayjs(createdAt).format('YYYY-MM-DD')} 入库`],
      })
      index += 1
    }
  })
}

ensureSeed()

const toListItem = (asset: InternalAsset): AssetListItemDto => {
  return {
    id: asset.id,
    assetNo: asset.assetNo,
    name: asset.name,
    type: asset.type,
    status: asset.status,
    ownerId: asset.ownerId,
    ownerName: asset.ownerName,
    location: asset.location,
    purchaseDate: asset.purchaseDate,
    updatedAt: asset.updatedAt,
  }
}

const toDetailBase = (asset: InternalAsset): Omit<AssetDetailDto, 'relatedTickets'> => {
  return {
    id: asset.id,
    assetNo: asset.assetNo,
    name: asset.name,
    type: asset.type,
    status: asset.status,
    ownerId: asset.ownerId,
    ownerName: asset.ownerName,
    location: asset.location,
    purchaseDate: asset.purchaseDate,
    updatedAt: asset.updatedAt,
    spec: asset.spec,
    remark: asset.remark,
  }
}

const findAsset = (tenantId: string, id: string) => {
  return assets.find((asset) => asset.tenantId === tenantId && asset.id === id) || null
}

export const queryAssetsByTenant = (
  tenantId: string,
  query: AssetListQueryDto
): PageResult<AssetListItemDto> => {
  const page = Math.max(Number(query.page || 1), 1)
  const pageSize = Math.max(Number(query.pageSize || 10), 1)

  let filtered = assets.filter((item) => item.tenantId === tenantId)

  if (query.status) {
    filtered = filtered.filter((item) => item.status === query.status)
  }
  if (query.type) {
    filtered = filtered.filter((item) => item.type === query.type)
  }
  if (query.keyword) {
    const keyword = query.keyword.toLowerCase()
    filtered = filtered.filter((item) => {
      return (
        item.id.toLowerCase().includes(keyword) ||
        item.assetNo.toLowerCase().includes(keyword) ||
        item.name.toLowerCase().includes(keyword) ||
        (item.location || '').toLowerCase().includes(keyword) ||
        (item.ownerName || '').toLowerCase().includes(keyword)
      )
    })
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

export const getAssetDetailByTenant = (tenantId: string, id: string): Omit<AssetDetailDto, 'relatedTickets'> | null => {
  const asset = findAsset(tenantId, id)
  if (!asset) {
    return null
  }
  return clone(toDetailBase(asset))
}

export const getAssetsByIdsByTenant = (tenantId: string, ids: string[]): AssetSimpleDto[] => {
  const list = assets
    .filter((item) => item.tenantId === tenantId && ids.includes(item.id))
    .map((item) => ({
      id: item.id,
      name: item.name,
      status: item.status,
    }))
  return clone(list)
}

export const createAssetByTenant = (tenantId: string, payload: CreateAssetDto) => {
  const id = `mock-asset-${assets.length + 1000}`
  const assetNo = `ASSET-MOCK-${String(assets.length + 1000).padStart(4, '0')}`
  const now = dayjs().toISOString()
  const asset: InternalAsset = {
    id,
    tenantId,
    assetNo,
    name: payload.name,
    type: payload.type,
    status: 'stock',
    ownerId: null,
    ownerName: null,
    location: payload.location,
    purchaseDate: dayjs().format('YYYY-MM-DD'),
    updatedAt: now,
    spec: payload.spec,
    remark: payload.remark || '',
    history: [`${dayjs().format('YYYY-MM-DD HH:mm:ss')} 入库`],
  }
  assets.unshift(asset)
  return clone(toDetailBase(asset))
}

export const updateAssetByTenant = (tenantId: string, id: string, payload: UpdateAssetDto) => {
  const asset = findAsset(tenantId, id)
  if (!asset) {
    return null
  }
  asset.name = payload.name || asset.name
  asset.type = payload.type || asset.type
  asset.location = payload.location || asset.location
  asset.spec = payload.spec || asset.spec
  asset.remark = payload.remark ?? asset.remark
  asset.updatedAt = dayjs().toISOString()
  asset.history.unshift(`${dayjs().format('YYYY-MM-DD HH:mm:ss')} 更新资产信息`)
  return clone(toDetailBase(asset))
}

export const actionAssetByTenant = (tenantId: string, id: string, username: string, payload: AssetActionDto) => {
  const asset = findAsset(tenantId, id)
  if (!asset) {
    return null
  }
  const now = dayjs().toISOString()

  // 状态机前置校验（与后端 AssetService.validateTransition 保持一致）
  const invalid = (() => {
    switch (payload.action) {
      case 'claim':
        return asset.status !== 'stock' ? '仅库存中的资产可领用' : null
      case 'receive':
        return asset.status !== 'in_use' ? '仅已领用的资产可回收' : null
      case 'scrap':
        return asset.status === 'scrapped' ? '资产已报废，不可重复操作' : null
      default:
        return '不支持的资产动作'
    }
  })()
  if (invalid) {
    throw new Error(invalid)
  }

  switch (payload.action) {
    case 'receive':
      asset.status = 'stock'
      asset.ownerId = null
      asset.ownerName = null
      asset.history.unshift(`${dayjs().format('YYYY-MM-DD HH:mm:ss')} 回收入库 by ${username}`)
      break
    case 'claim': {
      if (!payload.ownerId) {
        throw new Error('领用需指定领用人')
      }
      const owner = findEnabledUser(payload.ownerId)
      if (!owner) {
        throw new Error('领用人不存在或已禁用')
      }
      asset.status = 'in_use'
      asset.ownerId = owner.id
      asset.ownerName = owner.displayName
      asset.history.unshift(
        `${dayjs().format('YYYY-MM-DD HH:mm:ss')} 领用 by ${asset.ownerName}${payload.remark ? ` / ${payload.remark}` : ''}`
      )
      break
    }
    case 'scrap':
      asset.status = 'scrapped'
      asset.ownerId = null
      asset.ownerName = null
      asset.history.unshift(`${dayjs().format('YYYY-MM-DD HH:mm:ss')} 报废 by ${username}`)
      break
    default:
      break
  }
  asset.updatedAt = now
  return clone(toDetailBase(asset))
}
