import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setupAntd } from '@/plugins/antd'
import { assetActionApi, getAssetDetailApi, getAssetListApi } from '@/api/asset'
import { getUserOptionsApi } from '@/api/auth'
import type { AssetDetailDto, AssetListItemDto } from '@/types/asset'
import AssetDetail from './detail.vue'
import AssetList from './list.vue'

vi.mock('@/api/asset', () => ({
  getAssetListApi: vi.fn(),
  getAssetDetailApi: vi.fn(),
  createAssetApi: vi.fn(),
  updateAssetApi: vi.fn(),
  assetActionApi: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  getUserOptionsApi: vi.fn(),
}))

const asset: AssetListItemDto = {
  id: 'asset-uuid-1',
  assetNo: 'ASSET-A-0001',
  name: '核心交换机',
  type: 'network',
  status: 'stock',
  ownerId: null,
  ownerName: null,
  location: '上海机房-A区',
  purchaseDate: '2026-01-01',
  updatedAt: '2026-08-26T08:00:00',
}

const antdPlugin = { install: setupAntd }
const nativeGetComputedStyle = window.getComputedStyle.bind(window)
const globalOptions = {
  plugins: [antdPlugin],
  stubs: { Permission: { template: '<div><slot /></div>' } },
}

const mountList = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/asset/list', component: AssetList }],
  })
  await router.push('/asset/list')
  await router.isReady()
  return mount(AssetList, {
    global: { ...globalOptions, plugins: [antdPlugin, router] },
  })
}

const mountDetail = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/asset/detail/:id', component: AssetDetail }],
  })
  await router.push('/asset/detail/asset-uuid-1')
  await router.isReady()
  return mount(AssetDetail, {
    global: { ...globalOptions, plugins: [antdPlugin, router] },
  })
}

describe('asset pages backend integration', () => {
  beforeEach(() => {
    vi.spyOn(window, 'getComputedStyle').mockImplementation((element) =>
      nativeGetComputedStyle(element)
    )
    vi.clearAllMocks()
    vi.mocked(getAssetListApi).mockResolvedValue({ list: [asset], page: 1, pageSize: 10, total: 1 })
    vi.mocked(getAssetDetailApi).mockResolvedValue({
      ...asset,
      spec: '48 ports',
      remark: '生产网络',
      relatedTickets: [],
    })
    vi.mocked(getUserOptionsApi).mockResolvedValue([
      { id: 'u-staff', username: 'staff', displayName: 'Support Staff' },
    ])
    vi.mocked(assetActionApi).mockResolvedValue({
      ...asset,
      status: 'in_use',
      ownerId: 'u-staff',
      ownerName: 'Support Staff',
      spec: '48 ports',
      remark: '生产网络',
      relatedTickets: [],
    })
  })

  it('renders the business asset number instead of the opaque id', async () => {
    const wrapper = await mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('ASSET-A-0001')
    expect(wrapper.text()).not.toContain('asset-uuid-1')
  })

  it('loads detail before editing so spec and remark are preserved', async () => {
    const wrapper = await mountList()
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      openEdit: (record: AssetListItemDto) => Promise<void>
      editForm: { spec?: string; remark?: string }
    }

    await vm.openEdit(asset)

    expect(getAssetDetailApi).toHaveBeenCalledWith('asset-uuid-1')
    expect(vm.editForm).toMatchObject({ spec: '48 ports', remark: '生产网络' })
  })

  it('normalizes omitted nullable detail fields before editing', async () => {
    vi.mocked(getAssetDetailApi).mockResolvedValue({
      ...asset,
      location: null,
      purchaseDate: null,
      spec: null,
      remark: null,
      relatedTickets: [],
    } as unknown as AssetDetailDto)
    const wrapper = await mountList()
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      openEdit: (record: AssetListItemDto) => Promise<void>
      editForm: { location?: string; spec?: string; remark?: string }
    }

    await vm.openEdit(asset)

    expect(vm.editForm).toMatchObject({ location: '', spec: '', remark: '' })
  })

  it('loads user options and submits ownerId when claiming', async () => {
    const wrapper = await mountList()
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      openClaim: (record: AssetListItemDto) => Promise<void>
      submitClaim: () => Promise<void>
      claimForm: { ownerId: string; remark: string }
    }

    await vm.openClaim(asset)
    vm.claimForm.ownerId = 'u-staff'
    vm.claimForm.remark = '新员工设备'
    await vm.submitClaim()

    expect(getUserOptionsApi).toHaveBeenCalledOnce()
    expect(assetActionApi).toHaveBeenCalledWith('asset-uuid-1', {
      action: 'claim',
      ownerId: 'u-staff',
      remark: '新员工设备',
    })
  })

  it('renders assetNo and ownerName in details', async () => {
    vi.mocked(getAssetDetailApi).mockResolvedValue({
      ...asset,
      status: 'in_use',
      ownerId: 'u-staff',
      ownerName: 'Support Staff',
      spec: '48 ports',
      remark: '生产网络',
      relatedTickets: [],
    })
    const wrapper = await mountDetail()
    await flushPromises()

    expect(wrapper.text()).toContain('ASSET-A-0001')
    expect(wrapper.text()).toContain('Support Staff')
  })

  it('disables receive when the asset is not in use', async () => {
    const wrapper = await mountDetail()
    await flushPromises()
    const receive = wrapper.findAll('button').find((button) => button.text() === '回收入库')
    const scrap = wrapper.findAll('button').find((button) => button.text() === '报废')

    expect(receive?.attributes('disabled')).toBeDefined()
    expect(scrap?.attributes('disabled')).toBeUndefined()
  })
})
