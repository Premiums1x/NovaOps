<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { BubbleList, Welcome, XSender } from 'vue-element-plus-x'
import type { BubbleListItemProps } from 'vue-element-plus-x/types/BubbleList'
import type { CitationDto } from '@/types/agent'
import { useChat } from '@/composables/useChat'

type ChatBubble = BubbleListItemProps & {
  key: string
  role: 'user' | 'assistant'
  content: string
  citations?: CitationDto[]
  validationPassed?: boolean
}

const { store, send, stop } = useChat()
const senderRef = ref<InstanceType<typeof XSender>>()
const Preview = defineAsyncComponent(() => import('@/components/markdown/MdPreviewAsync.vue'))

const bubbleItems = computed<ChatBubble[]>(() =>
  store.messages.map((item, index) => ({
    ...item,
    key: item.id,
    placement: item.role === 'user' ? 'end' : 'start',
    shape: 'corner',
    variant: 'filled',
    maxWidth: item.role === 'user' ? '72%' : '86%',
    avatarSize: '34px',
    avatarGap: '12px',
    loading: item.role === 'assistant' && store.loading && index === store.messages.length - 1 && !item.content,
  })),
)

const submit = async () => {
  const value = senderRef.value?.getModelValue().text.trim() || ''
  if (!value || store.loading) return
  senderRef.value?.clear()
  await send(value)
  senderRef.value?.focus('end')
}

const askSuggestion = async (question: string) => {
  if (store.loading) return
  await send(question)
}
</script>

<template>
  <section class="chat-core">
    <div class="message-stage">
      <div v-if="!bubbleItems.length" class="welcome-wrap">
        <Welcome
          variant="borderless"
          title="你好，我是 Nova AI"
          description="检索企业知识库，并为企业问题提供可追溯依据。"
        />
        <div class="suggestions" aria-label="推荐问题">
          <button type="button" @click="askSuggestion('如何使用当前知识库？')">如何使用当前知识库？</button>
          <button type="button" @click="askSuggestion('如何处理服务器告警？')">如何处理服务器告警？</button>
          <button type="button" @click="askSuggestion('总结当前知识库的主要内容')">知识库有哪些内容？</button>
        </div>
      </div>

      <BubbleList
        v-else
        class="bubble-list"
        :list="bubbleItems"
        max-height="100%"
        show-back-button
        btn-color="var(--nova-primary)"
      >
        <template #avatar="{ item }">
          <span class="message-avatar" :class="item.role">
            <UserOutlined v-if="item.role === 'user'" />
            <RobotOutlined v-else />
          </span>
        </template>
        <template #content="{ item }">
          <div v-if="item.role === 'user'" class="user-message">{{ item.content }}</div>
          <Preview v-else-if="item.content" :editor-id="String(item.key)" :model-value="item.content" />
        </template>
        <template #footer="{ item }">
          <div v-if="item.role === 'assistant'" class="answer-footer">
            <div v-if="item.citations?.length" class="citations">
              <a-tag v-for="citation in item.citations" :key="citation.chunkId" color="blue">
                [{{ citation.index }}] {{ citation.documentName }}
              </a-tag>
            </div>
            <a-alert
              v-if="item.validationPassed === false"
              type="warning"
              show-icon
              message="该回答未通过完整依据校验，请谨慎参考"
            />
          </div>
        </template>
      </BubbleList>
    </div>

    <div class="sender-dock">
      <a-alert v-if="store.lastError" type="error" show-icon :message="store.lastError" closable />
      <XSender
        ref="senderRef"
        class="nova-sender"
        placeholder="向 Nova AI 提问，Enter 发送，Shift + Enter 换行"
        submit-type="enter"
        :max-length="4000"
        :loading="store.loading"
        clearable
        @submit="submit"
        @cancel="stop"
      />
      <p class="sender-note">Nova AI 可能会出错，企业结论请结合引用资料核对。</p>
    </div>
  </section>
</template>

<style scoped>
.chat-core{display:grid;grid-template-rows:minmax(0,1fr) auto;height:100%;min-height:0;background:var(--nova-surface)}
.message-stage{min-height:0;overflow:hidden;background:linear-gradient(180deg,color-mix(in srgb,var(--nova-primary) 4%,var(--nova-surface)) 0,var(--nova-surface) 180px)}
.bubble-list{height:100%;padding:24px clamp(16px,4vw,56px)}
.welcome-wrap{display:grid;place-content:center;justify-items:center;height:100%;padding:32px;text-align:center}
.welcome-wrap :deep(.el-welcome){max-width:620px;background:transparent}
.suggestions{display:flex;flex-wrap:wrap;justify-content:center;gap:10px;max-width:700px;margin-top:18px}
.suggestions button{padding:9px 14px;border:1px solid var(--nova-border);border-radius:999px;background:var(--nova-surface-elevated);color:var(--nova-text);cursor:pointer;transition:border-color .2s,transform .2s}
.suggestions button:hover{border-color:var(--nova-primary);transform:translateY(-1px)}
.message-avatar{display:grid;width:34px;height:34px;place-items:center;border:1px solid var(--nova-border);border-radius:10px;background:var(--nova-surface-elevated);color:var(--nova-primary);box-shadow:0 4px 12px rgba(15,23,42,.08)}
.message-avatar.user{border-color:transparent;background:var(--nova-primary);color:#fff}
.user-message{color:inherit;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word}
.bubble-list :deep(.elx-bubble--end){--elx-bubble-bg:var(--nova-primary);--elx-bubble-text-color:#fff}
.bubble-list :deep(.elx-bubble--start){--elx-bubble-bg:var(--nova-surface-elevated);--elx-bubble-text-color:var(--nova-text);--elx-bubble-border-color:var(--nova-border)}
.bubble-list :deep(.elx-bubble--start .elx-bubble__content){border:1px solid var(--nova-border);box-shadow:0 4px 14px rgba(15,23,42,.04)}
.bubble-list :deep(.markdown-preview),
.bubble-list :deep(.markdown-preview .md-editor),
.bubble-list :deep(.markdown-preview .md-editor-preview-wrapper),
.bubble-list :deep(.markdown-preview .md-editor-preview){background:transparent!important}
.bubble-list :deep(.markdown-preview .md-editor){--md-bk-color:transparent;--md-color:var(--nova-text);border:0!important;color:var(--nova-text)}
.bubble-list :deep(.markdown-preview .md-editor-preview){padding:0!important;color:var(--nova-text)}
.answer-footer{display:grid;gap:8px;margin-top:8px}
.citations{display:flex;flex-wrap:wrap;gap:6px}
.sender-dock{display:grid;gap:8px;padding:14px clamp(16px,4vw,56px) 12px;border-top:1px solid var(--nova-border);background:color-mix(in srgb,var(--nova-surface) 94%,transparent);backdrop-filter:blur(12px)}
.nova-sender{width:100%}
.nova-sender :deep(.el-sender-wrap){border-color:var(--nova-border);background:var(--nova-surface-elevated);box-shadow:0 10px 30px rgba(15,23,42,.08)}
.sender-note{margin:0;color:var(--nova-text-secondary);font-size:12px;text-align:center}
@media(max-width:760px){.bubble-list{padding:16px 10px}.sender-dock{padding:10px}.welcome-wrap{padding:20px}.suggestions{display:grid;width:100%}.suggestions button{text-align:left}}
</style>
