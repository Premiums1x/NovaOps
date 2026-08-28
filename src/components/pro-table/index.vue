<script setup lang="ts">
import type { TableProps } from 'ant-design-vue'

type RowData = Record<string, unknown>

const props = withDefaults(
  defineProps<{
    columns: TableProps['columns']
    dataSource: RowData[]
    loading?: boolean
    page: number
    pageSize: number
    total: number
    rowKey?: string | ((record: RowData) => string)
    scroll?: TableProps['scroll']
    headerTitle?: string
  }>(),
  {
    loading: false,
    rowKey: 'id',
    scroll: () => ({ x: 'max-content' }),
    headerTitle: '',
  }
)

const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'update:page-size', value: number): void
}>()

const handlePageChange = (page: number, pageSize: number) => {
  emit('update:page', page)
  if (pageSize !== props.pageSize) {
    emit('update:page-size', pageSize)
  }
}

const handlePageSizeChange = (_current: number, pageSize: number) => {
  emit('update:page-size', pageSize)
  emit('update:page', 1)
}
</script>

<template>
  <section class="pro-table">
    <!-- 上层：独立筛选卡片（仅当传入 filters 插槽时渲染） -->
    <a-card v-if="$slots.filters" :bordered="false" class="pro-table-search-card">
      <slot name="filters" />
    </a-card>

    <!-- 下层：表格数据卡片 -->
    <a-card :bordered="false" class="pro-table-card">
      <!-- 表格上方工具栏：左侧标题/统计，右侧操作按钮组 -->
      <div v-if="props.headerTitle || $slots.headerTitle || $slots.actions" class="pro-table-header">
        <div class="pro-table-title">
          <slot name="headerTitle">
            <span v-if="props.headerTitle" class="title-text">{{ props.headerTitle }}</span>
          </slot>
        </div>
        <div v-if="$slots.actions" class="pro-table-actions">
          <slot name="actions" />
        </div>
      </div>

      <!-- 数据表格 -->
      <a-table
        :columns="props.columns"
        :data-source="props.dataSource"
        :loading="props.loading"
        :row-key="props.rowKey"
        :pagination="false"
        :scroll="props.scroll"
        size="middle"
      >
        <template #bodyCell="slotProps">
          <slot name="bodyCell" v-bind="slotProps" />
        </template>
        <template #emptyText>
          <slot name="empty">
            <a-empty description="暂无数据" />
          </slot>
        </template>
      </a-table>

      <!-- 底部统计与分页 -->
      <div class="pro-table-pagination">
        <a-pagination
          :current="props.page"
          :page-size="props.pageSize"
          :total="props.total"
          :show-size-changer="true"
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handlePageChange"
          @show-size-change="handlePageSizeChange"
        />
      </div>
    </a-card>
  </section>
</template>

<style scoped>
.pro-table {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pro-table-search-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.pro-table-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.pro-table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.pro-table-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.title-text {
  line-height: 1.5;
}

.pro-table-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.pro-table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
