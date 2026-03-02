<script setup lang="ts">
defineOptions({
  name: 'TicketList',
})
</script>

<template>
  <a-card title="工单列表（阶段2占位）" :bordered="false">
    <a-space>
      <a-button v-permission="'ticket:create'" type="primary">新建工单</a-button>
      <Permission code="ticket:assign">
        <a-button>批量指派</a-button>
      </Permission>
    </a-space>
    <a-empty description="阶段3将实现完整工单模块（列表/详情/流转）" class="mt16" />
  </a-card>
</template>

<style scoped>
.mt16 {
  margin-top: 16px;
}
</style>
