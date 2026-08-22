<script setup lang="ts">
import type { TableProps } from 'ant-design-vue'

//一个对象，key 都是 string，value 都是 unknown
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
  }>(),
  {
    loading: false,
    rowKey: 'id',
  }
)

//函数签名
const emit = defineEmits<{
  //e 是参数名，'update:page' 是它的类型——字面量类型，
  // 表示这个参数只能传这个固定字符串，不能传别的。
  (e: 'update:page', value: number): void
  (e: 'update:page-size', value: number): void
}>()

const handlePageChange = (page: number, pageSize: number) => {
  emit('update:page', page)
  //判断：用户这次操作有没有改变每页条数
  //变了才 emit，没变就不发事件，避免无意义的更新。
  if (pageSize !== props.pageSize) {
    emit('update:page-size', pageSize)
  }
}

//改变每页条数时触发
const handlePageSizeChange = (_current: number, pageSize: number) => {
  emit('update:page-size', pageSize)
  emit('update:page', 1)
}
</script>

<template>
  <!-- slot插槽：具体放什么筛选条件和按钮由父组件自己决定 -->
  <section class="pro-table">
    <div class="pro-table-toolbar">
      <div class="pro-table-filters">
        <slot name="filters" />
      </div>
      <div class="pro-table-actions">
        <slot name="actions" />
      </div>
    </div>

    <a-card :bordered="false" class="pro-table-card">
      <a-table
        :columns="props.columns"
        :data-source="props.dataSource"
        :loading="props.loading"
        :row-key="props.rowKey"
        :pagination="false"
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
  display: grid;
  gap: 12px;
}

.pro-table-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.pro-table-filters {
  flex: 1;
}

.pro-table-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pro-table-card {
  border-radius: 10px;
}

.pro-table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
