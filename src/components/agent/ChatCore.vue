<script setup lang="ts">
import { defineAsyncComponent,nextTick,ref,watch } from 'vue'
import { SendOutlined,StopOutlined } from '@ant-design/icons-vue'
import { useChat } from '@/composables/useChat'
const props=withDefaults(defineProps<{compact?:boolean}>(),{compact:false});const {store,send,stop}=useChat();const input=ref(''),scrollRef=ref<HTMLElement>()
const submit=async()=>{const value=input.value;input.value='';await send(value)}
watch(()=>store.messages.map(item=>item.content.length).join(','),async()=>{await nextTick();if(scrollRef.value)scrollRef.value.scrollTop=scrollRef.value.scrollHeight})
const Preview=defineAsyncComponent(()=>import('@/components/markdown/MdPreviewAsync.vue'))
</script>
<template>
  <div class="chat-core" :class="{compact:props.compact}">
    <div ref="scrollRef" class="messages">
      <a-empty v-if="!store.messages.length" description="向企业知识助手提问，回答将附带可回溯引用" />
      <div v-for="item in store.messages" :key="item.id" class="message" :class="item.role">
        <div class="bubble"><strong>{{item.role==='user'?'你':'Nova AI'}}</strong><Preview :editor-id="item.id" :model-value="item.content||'正在检索并核对知识库…'" /><div v-if="item.citations?.length" class="citations"><a-tag v-for="citation in item.citations" :key="citation.chunkId" color="blue">[{{citation.index}}] {{citation.documentName}}</a-tag></div><a-alert v-if="item.validationPassed===false" type="warning" show-icon message="该回答未通过完整依据校验，请谨慎参考" /></div>
      </div>
    </div>
    <div class="composer"><a-textarea v-model:value="input" :auto-size="{minRows:2,maxRows:5}" :maxlength="4000" placeholder="请输入问题，Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="submit"/><a-button v-if="store.loading" danger @click="stop"><StopOutlined/>停止</a-button><a-button v-else type="primary" :disabled="!input.trim()" @click="submit"><SendOutlined/>发送</a-button></div>
  </div>
</template>
<style scoped>
.chat-core{display:grid;grid-template-rows:minmax(0,1fr) auto;height:100%;min-height:480px}.messages{overflow:auto;padding:18px}.message{display:flex;margin-bottom:16px}.message.user{justify-content:flex-end}.bubble{max-width:82%;padding:12px 16px;border-radius:14px;background:var(--nova-surface-elevated);box-shadow:0 1px 0 var(--nova-border)}.user .bubble{color:#fff;background:var(--nova-primary)}.bubble :deep(.md-editor-preview-wrapper){padding:6px 0}.user .bubble :deep(.md-editor-preview){color:#fff}.citations{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.composer{display:flex;gap:10px;align-items:flex-end;padding:14px;border-top:1px solid var(--nova-border);background:var(--nova-surface)}.compact{min-height:420px}.compact .messages{padding:12px}.compact .bubble{max-width:92%}
</style>
