import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  AssetActionDto,
  AssetDetailDto,
  AssetListItemDto,
  AssetListQueryDto,
  AssetSimpleDto,
  CreateAssetDto,
  UpdateAssetDto,
} from '@/types/asset'

export const getAssetListApi = (params: AssetListQueryDto) => {
  return request.get<PageResult<AssetListItemDto>>('/assets', { params })
}

export const getAssetDetailApi = (id: string) => {
  return request.get<AssetDetailDto>(`/assets/${id}`)
}

export const getAssetBatchApi = (ids: string[]) => {
  return request.get<AssetSimpleDto[]>('/assets/batch', {
    params: { ids: ids.join(',') },
  })
}

export const createAssetApi = (payload: CreateAssetDto) => {
  return request.post<AssetDetailDto, CreateAssetDto>('/assets', payload)
}

export const updateAssetApi = (id: string, payload: UpdateAssetDto) => {
  return request.put<AssetDetailDto, UpdateAssetDto>(`/assets/${id}`, payload)
}

export const assetActionApi = (id: string, payload: AssetActionDto) => {
  return request.post<AssetDetailDto, AssetActionDto>(`/assets/${id}/actions`, payload)
}
