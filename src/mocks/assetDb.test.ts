import { describe, expect, it } from 'vitest'
import {
  actionAsset,
  createAsset,
  getAssetsByIds,
  queryAssets,
} from './assetDb'
import { listRelatedTicketsByAsset } from './ticketDb'

describe('asset mock backend contract', () => {
  it('returns assetNo and user relation fields instead of owner text', () => {
    const result = queryAssets({ page: 1, pageSize: 180 })
    const claimed = result.list.find((asset) => asset.status === 'in_use')

    expect(result.list[0]).toHaveProperty('assetNo')
    expect(claimed).toMatchObject({
      ownerId: expect.any(String),
      ownerName: expect.any(String),
    })
    expect(claimed).not.toHaveProperty('owner')
  })

  it('creates stock assets without an owner and claims them by ownerId', () => {
    const created = createAsset({
      name: '契约测试服务器',
      type: 'server',
      location: '上海机房-A区',
      spec: '4C / 16G',
    })

    expect(created).toMatchObject({
      status: 'stock',
      ownerId: null,
      ownerName: null,
    })

    const claimed = actionAsset(created.id, 'admin', {
      action: 'claim',
      ownerId: 'u-staff',
    })
    expect(claimed).toMatchObject({
      status: 'in_use',
      ownerId: 'u-staff',
      ownerName: 'Support Staff',
    })

    const received = actionAsset(created.id, 'admin', {
      action: 'receive',
    })
    expect(received).toMatchObject({ status: 'stock', ownerId: null, ownerName: null })
  })

  it('rejects a missing or disabled claim target like the backend', () => {
    const created = createAsset({
      name: '非法领用测试',
      type: 'laptop',
      location: '深圳办公区',
      spec: '16G / 512G',
    })

    expect(() =>
      actionAsset(created.id, 'admin', { action: 'claim' })
    ).toThrow('领用需指定领用人')
    expect(() =>
      actionAsset(created.id, 'admin', {
        action: 'claim',
        ownerId: 'missing-user',
      })
    ).toThrow('领用人不存在或已禁用')
  })

  it('preserves seeded ticket and asset relations in both directions', () => {
    const linkedAssets = getAssetsByIds(['ASSET-1'])
    const linkedTickets = listRelatedTicketsByAsset('ASSET-1')

    expect(linkedAssets).toEqual([
      expect.objectContaining({ id: 'ASSET-1' }),
    ])
    expect(linkedTickets.length).toBeGreaterThan(0)
  })
})
