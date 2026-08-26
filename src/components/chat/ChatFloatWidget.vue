<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  CloseOutlined,
  CustomerServiceOutlined,
  ExpandOutlined,
  HistoryOutlined,
  LeftOutlined,
  MessageOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { useChatStore } from '@/store/chat'
import { usePermissionStore } from '@/store/permission'

const ChatCore = defineAsyncComponent(() => import('@/components/agent/ChatCore.vue'))

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const permissionStore = usePermissionStore()

const visible = ref(false)
const showHistory = ref(false)
const historyLoading = ref(false)

// 当处于独立的 /agent/chat 完整页面时，隐藏浮窗和悬浮按钮
const isAgentChatPage = computed(() => route.path.startsWith('/agent/chat'))
const hasPermission = computed(() => permissionStore.hasPermission('agent:chat'))

// 格式化历史会话时间
const formatTime = (timeStr?: string) => {
  if (!timeStr) return ''
  const date = dayjs(timeStr)
  const now = dayjs()
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm')
  }
  if (date.isSame(now, 'year')) {
    return date.format('MM-DD HH:mm')
  }
  return date.format('YYYY-MM-DD')
}

// 切换浮窗显示状态
const toggleWidget = () => {
  visible.value = !visible.value
  if (visible.value) {
    void loadHistory()
  } else {
    showHistory.value = false
  }
}

// 加载历史会话
const loadHistory = async () => {
  historyLoading.value = true
  try {
    await chatStore.loadConversations()
  } catch {
    // 忽略加载异常
  } finally {
    historyLoading.value = false
  }
}

// 切换抽屉历史面板
const toggleHistory = () => {
  showHistory.value = !showHistory.value
  if (showHistory.value) {
    void loadHistory()
  }
}

// 选择某条历史对话
const selectConversation = async (id: string) => {
  if (id === chatStore.conversationId) {
    showHistory.value = false
    return
  }
  try {
    await chatStore.openConversation(id)
  } finally {
    showHistory.value = false
  }
}

// 新建对话
const handleNewChat = () => {
  chatStore.newConversation()
  showHistory.value = false
}

// 展开至全屏完整问答页
const handleExpandFull = () => {
  visible.value = false
  showHistory.value = false
  void router.push('/agent/chat')
}

// 路由切换时如进入独立问答页则自动收起
watch(
  () => route.path,
  (newPath) => {
    if (newPath.startsWith('/agent/chat')) {
      visible.value = false
      showHistory.value = false
    }
  },
)
</script>

<template>
  <div v-if="hasPermission && !isAgentChatPage" class="chat-float-container">
    <!-- 对话小浮窗面板 -->
    <Transition name="widget-fade-scale">
      <section v-if="visible" class="chat-widget-panel" aria-label="NovaOps AI 悬浮助手">
        <!-- 浮窗头部 -->
        <header class="widget-header">
          <div class="header-info">
            <span class="widget-avatar">
              <CustomerServiceOutlined />
            </span>
            <div class="widget-title-wrap">
              <div class="widget-title">Nova AI 助手</div>
              <div class="widget-status"><i class="status-dot"></i> 在线服务中</div>
            </div>
          </div>
          <div class="header-actions">
            <a-tooltip :title="showHistory ? '返回对话' : '历史对话记录'">
              <button
                type="button"
                class="action-btn"
                :class="{ 'is-active': showHistory }"
                aria-label="历史对话"
                @click="toggleHistory"
              >
                <HistoryOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="新建对话">
              <button type="button" class="action-btn" aria-label="新建对话" @click="handleNewChat">
                <PlusOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="全屏打开">
              <button type="button" class="action-btn" aria-label="全屏打开" @click="handleExpandFull">
                <ExpandOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="收起">
              <button type="button" class="action-btn close-btn" aria-label="收起" @click="toggleWidget">
                <CloseOutlined />
              </button>
            </a-tooltip>
          </div>
        </header>

        <!-- 浮窗主体：对话区域 + 抽屉式历史记录 -->
        <main class="widget-body">
          <ChatCore />

          <!-- 抽屉式历史会话面板 -->
          <Transition name="drawer-slide">
            <div v-if="showHistory" class="history-drawer-panel">
              <div class="drawer-head">
                <div class="drawer-title">
                  <HistoryOutlined class="drawer-icon" />
                  <span>历史会话</span>
                  <span class="drawer-count">({{ chatStore.conversations.length }})</span>
                </div>
                <button type="button" class="drawer-new-btn" @click="handleNewChat">
                  <PlusOutlined />
                  <span>新对话</span>
                </button>
              </div>

              <!-- 历史列表 -->
              <div class="drawer-list-wrap">
                <a-spin :spinning="historyLoading">
                  <ul v-if="chatStore.conversations.length" class="history-list">
                    <li
                      v-for="item in chatStore.conversations"
                      :key="item.id"
                      class="history-item"
                      :class="{ 'is-current': item.id === chatStore.conversationId }"
                      @click="selectConversation(item.id)"
                    >
                      <div class="item-left">
                        <MessageOutlined class="item-icon" />
                        <div class="item-meta">
                          <span class="item-title" :title="item.title">{{ item.title || '未命名对话' }}</span>
                          <span class="item-time">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
                        </div>
                      </div>
                      <span v-if="item.id === chatStore.conversationId" class="current-badge">当前</span>
                    </li>
                  </ul>
                  <div v-else class="history-empty">
                    <div class="empty-icon">
                      <MessageOutlined />
                    </div>
                    <p>暂无历史对话</p>
                    <button type="button" class="empty-action-btn" @click="handleNewChat">
                      开启新对话
                    </button>
                  </div>
                </a-spin>
              </div>

              <!-- 抽屉底部快捷返回 -->
              <div class="drawer-footer">
                <button type="button" class="drawer-back-btn" @click="showHistory = false">
                  <LeftOutlined />
                  <span>返回当前对话</span>
                </button>
              </div>
            </div>
          </Transition>
        </main>
      </section>
    </Transition>

    <!-- 右下角悬浮触发按钮 -->
    <button
      type="button"
      class="chat-float-btn"
      :class="{ 'is-active': visible }"
      aria-label="打开智能问答"
      @click="toggleWidget"
    >
      <span class="btn-glow"></span>
      <span class="btn-icon">
        <CloseOutlined v-if="visible" />
        <CustomerServiceOutlined v-else />
      </span>
      <span v-if="!visible" class="btn-label">AI 助手</span>
    </button>
  </div>
</template>

<style scoped>
.chat-float-container {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1050;
  pointer-events: none;
}

.chat-float-container * {
  pointer-events: auto;
}

/* 悬浮按钮 */
.chat-float-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 18px 0 14px;
  border: 1px solid color-mix(in srgb, var(--nova-primary) 40%, transparent);
  border-radius: 999px;
  background: linear-gradient(135deg, var(--nova-primary), color-mix(in srgb, var(--nova-primary) 80%, #000));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 8px 24px color-mix(in srgb, var(--nova-primary) 42%, transparent),
              0 2px 6px rgba(0, 0, 0, 0.12);
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  user-select: none;
}

.chat-float-btn.is-active {
  width: 48px;
  height: 48px;
  padding: 0;
  justify-content: center;
  background: var(--nova-surface-elevated);
  border-color: var(--nova-border);
  color: var(--nova-text);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
}

.chat-float-btn:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--nova-primary) 55%, transparent),
              0 4px 10px rgba(0, 0, 0, 0.15);
}

.chat-float-btn.is-active:hover {
  background: var(--nova-surface);
  color: var(--nova-primary);
  border-color: var(--nova-primary);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.16);
}

.btn-icon {
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.25s ease;
}

.chat-float-btn:hover .btn-icon {
  transform: scale(1.1);
}

.btn-label {
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.btn-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.25) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.chat-float-btn:hover .btn-glow {
  opacity: 1;
}

/* 浮窗面板 */
.chat-widget-panel {
  position: absolute;
  right: 0;
  bottom: 60px;
  width: 400px;
  height: 590px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 100px);
  display: grid;
  grid-template-rows: auto 1fr;
  border-radius: 16px;
  border: 1px solid var(--nova-border);
  background: var(--nova-surface);
  box-shadow: 0 16px 44px rgba(15, 23, 42, 0.18),
              0 0 1px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  backdrop-filter: blur(16px);
}

/* 浮窗头部 */
.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--nova-primary) 6%, var(--nova-surface-elevated));
  border-bottom: 1px solid var(--nova-border);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.widget-avatar {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--nova-primary), color-mix(in srgb, var(--nova-primary) 70%, #000));
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 16px;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--nova-primary) 35%, transparent);
}

.widget-title-wrap {
  display: grid;
  gap: 2px;
}

.widget-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--nova-text);
  line-height: 1.2;
}

.widget-status {
  font-size: 11px;
  color: var(--nova-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--nova-text-secondary);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--nova-primary) 12%, transparent);
  color: var(--nova-primary);
}

.action-btn.is-active {
  background: color-mix(in srgb, var(--nova-primary) 18%, transparent);
  color: var(--nova-primary);
  font-weight: bold;
}

.action-btn.close-btn:hover {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

/* 浮窗主体 */
.widget-body {
  position: relative;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.widget-body :deep(.bubble-list) {
  padding: 16px 12px;
}

.widget-body :deep(.sender-dock) {
  padding: 10px 12px;
}

.widget-body :deep(.welcome-wrap) {
  padding: 16px 12px;
}

.widget-body :deep(.suggestions) {
  gap: 6px;
  margin-top: 12px;
}

.widget-body :deep(.suggestions button) {
  padding: 6px 10px;
  font-size: 12px;
}

/* 历史记录抽屉面板 */
.history-drawer-panel {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: var(--nova-surface);
  backdrop-filter: blur(14px);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--nova-border);
  background: var(--nova-surface-elevated);
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--nova-text);
}

.drawer-icon {
  color: var(--nova-primary);
  font-size: 15px;
}

.drawer-count {
  font-size: 12px;
  font-weight: normal;
  color: var(--nova-text-secondary);
}

.drawer-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--nova-primary) 35%, transparent);
  background: color-mix(in srgb, var(--nova-primary) 12%, transparent);
  color: var(--nova-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.drawer-new-btn:hover {
  background: var(--nova-primary);
  color: #fff;
}

.drawer-list-wrap {
  min-height: 0;
  overflow-y: auto;
  padding: 8px 10px;
}

.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: var(--nova-surface-elevated);
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-item:hover {
  border-color: color-mix(in srgb, var(--nova-primary) 30%, transparent);
  background: color-mix(in srgb, var(--nova-primary) 6%, var(--nova-surface-elevated));
  transform: translateX(2px);
}

.history-item.is-current {
  border-color: var(--nova-primary);
  background: color-mix(in srgb, var(--nova-primary) 12%, var(--nova-surface-elevated));
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.item-icon {
  font-size: 15px;
  color: var(--nova-text-secondary);
  flex-shrink: 0;
}

.history-item.is-current .item-icon {
  color: var(--nova-primary);
}

.item-meta {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--nova-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-item.is-current .item-title {
  color: var(--nova-primary);
  font-weight: 600;
}

.item-time {
  font-size: 11px;
  color: var(--nova-text-secondary);
}

.current-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--nova-primary);
  color: #fff;
  flex-shrink: 0;
  font-weight: 500;
}

.history-empty {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  padding: 48px 16px;
  color: var(--nova-text-secondary);
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  color: color-mix(in srgb, var(--nova-text-secondary) 50%, transparent);
}

.empty-action-btn {
  margin-top: 4px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--nova-border);
  background: var(--nova-surface-elevated);
  color: var(--nova-text);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.empty-action-btn:hover {
  border-color: var(--nova-primary);
  color: var(--nova-primary);
}

.drawer-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--nova-border);
  background: var(--nova-surface-elevated);
}

.drawer-back-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--nova-border);
  background: var(--nova-surface);
  color: var(--nova-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.drawer-back-btn:hover {
  border-color: var(--nova-primary);
  color: var(--nova-primary);
}

/* 抽屉过渡动画 */
.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1),
              opacity 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

/* 展开过渡动画 */
.widget-fade-scale-enter-active,
.widget-fade-scale-leave-active {
  transition: opacity 0.24s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.24s cubic-bezier(0.16, 1, 0.3, 1);
  transform-origin: right bottom;
}

.widget-fade-scale-enter-from,
.widget-fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.88) translateY(16px);
}
</style>
