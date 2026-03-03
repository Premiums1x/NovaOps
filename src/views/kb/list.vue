<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import type { TableProps } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import ProTable from '@/components/pro-table/index.vue'
import { getKbListApi } from '@/api/kb'
import type { KbListItemDto, KbListQueryDto } from '@/types/kb'

defineOptions({
  name: 'KbList',
})

const router = useRouter()
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref<KbListItemDto[]>([])

const filters = reactive<{
  keyword: string
  tag?: string
}>({
  keyword: '',
  tag: undefined,
})

const columns: TableProps['columns'] = [
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '标签', dataIndex: 'tags', key: 'tags', width: 220 },
  { title: '作者', dataIndex: 'author', key: 'author', width: 120 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 140 },
]

const buildQuery = (): KbListQueryDto => ({
  page: page.value,
  pageSize: pageSize.value,
  keyword: filters.keyword.trim() || undefined,
  tag: filters.tag || undefined,
})

const fetchList = async () => {
  loading.value = true
  try {
    const data = await getKbListApi(buildQuery())
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const search = async () => {
  page.value = 1
  await fetchList()
}

const reset = async () => {
  filters.keyword = ''
  filters.tag = undefined
  page.value = 1
  await fetchList()
}

const handlePageChange = async (value: number) => {
  page.value = value
  await fetchList()
}

const handlePageSizeChange = async (value: number) => {
  pageSize.value = value
  page.value = 1
  await fetchList()
}

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <section class="kb-list-page">
    <ProTable
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    >
      <template #filters>
        <a-form layout="inline">
          <a-form-item>
            <a-input
              v-model:value="filters.keyword"
              allow-clear
              style="width: 260px"
              placeholder="搜索标题/正文关键字"
            />
          </a-form-item>
          <a-form-item label="标签">
            <a-select
              v-model:value="filters.tag"
              allow-clear
              style="width: 180px"
              :options="[
                { label: '网络', value: '网络' },
                { label: '流程', value: '流程' },
                { label: '终端', value: '终端' },
                { label: '规范', value: '规范' },
              ]"
            />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="search">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </template>

      <template #actions>
        <Permission code="kb:edit">
          <a-button type="primary" @click="router.push('/kb/edit')">新建文章</a-button>
        </Permission>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <a @click="router.push(`/kb/edit/${(record as KbListItemDto).id}`)">
            {{ (record as KbListItemDto).title }}
          </a>
        </template>
        <template v-else-if="column.key === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in (record as KbListItemDto).tags" :key="tag">{{ tag }}</a-tag>
          </a-space>
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          {{ dayjs((record as KbListItemDto).updatedAt).format('YYYY-MM-DD HH:mm') }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button type="link" size="small" @click="router.push(`/kb/edit/${(record as KbListItemDto).id}`)">
              查看
            </a-button>
            <Permission code="kb:edit">
              <a-button type="link" size="small" @click="router.push(`/kb/edit/${(record as KbListItemDto).id}`)">
                编辑
              </a-button>
            </Permission>
          </a-space>
        </template>
      </template>
    </ProTable>
  </section>
</template>

<style scoped>
.kb-list-page {
  display: grid;
  gap: 12px;
}
</style>
