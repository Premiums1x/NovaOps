<script setup lang="ts">
import { onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ChatCore from '@/components/agent/ChatCore.vue'
import { useChatStore } from '@/store/chat'

// 路由 keep-alive 按组件名匹配缓存，必须与路由 name 保持一致
defineOptions({ name: 'AgentChat' })

const store = useChatStore()
onMounted(() => {
  store.loadConversations().catch(() => {})
})
</script>
<template>
  <a-card class="agent-page" :body-style="{padding:0,height:'calc(100vh - 176px)'}">
    <aside class="conversation-panel"><div class="panel-title"><strong>历史会话</strong><a-button type="primary" size="small" @click="store.newConversation()"><PlusOutlined/>新会话</a-button></div><a-list :data-source="store.conversations"><template #renderItem="{item}"><a-list-item class="conversation" :class="{active:item.id===store.conversationId}" @click="store.openConversation(item.id)"><a-list-item-meta :title="item.title" :description="new Date(item.updatedAt).toLocaleString()"/></a-list-item></template></a-list></aside>
    <main class="chat-panel"><ChatCore/></main>
  </a-card>
</template>
<style scoped>
.agent-page :deep(.ant-card-body){display:grid;grid-template-columns:260px minmax(0,1fr)}.conversation-panel{overflow:auto;border-right:1px solid var(--nova-border);background:var(--nova-surface-elevated)}.panel-title{display:flex;align-items:center;justify-content:space-between;padding:16px;border-bottom:1px solid var(--nova-border)}.conversation{cursor:pointer;padding-inline:16px!important}.conversation:hover,.conversation.active{background:color-mix(in srgb,var(--nova-primary) 10%,transparent)}.chat-panel{min-width:0}@media(max-width:760px){.agent-page :deep(.ant-card-body){grid-template-columns:1fr}.conversation-panel{display:none}}
</style>
