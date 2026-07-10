<template>
  <div class="share-chat-page">
    <template v-if="notFound">
      <div class="share-empty">
        <el-empty description="分享链接无效或应用未发布" />
      </div>
    </template>
    <template v-else>
      <header class="share-header" data-testid="share-chat-header">
        <el-avatar :size="36" :src="profile?.avatar || undefined">
          {{ (profile?.agentName || 'AI').slice(0, 1) }}
        </el-avatar>
        <div class="share-header-text">
          <strong>{{ profile?.agentName || 'AI 应用' }}</strong>
          <span v-if="profile?.description" class="share-desc">{{ profile.description }}</span>
        </div>
      </header>

      <main ref="messageListRef" class="share-messages" data-testid="share-chat-messages">
        <div v-if="profile?.prologue" class="share-message assistant">
          <div class="bubble">{{ profile.prologue }}</div>
        </div>
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="share-message"
          :class="msg.role"
        >
          <div class="bubble">{{ msg.content }}</div>
        </div>
        <div v-if="sending" class="share-message assistant">
          <div class="bubble bubble-loading">
            <span class="dot" /><span class="dot" /><span class="dot" />
          </div>
        </div>
      </main>

      <footer class="share-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入消息，按 Enter 发送"
          resize="none"
          :disabled="sending"
          data-testid="share-chat-input"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button
          type="primary"
          circle
          :icon="Promotion"
          :loading="sending"
          :disabled="!inputMessage.trim()"
          data-testid="share-chat-send"
          @click="handleSend"
        />
      </footer>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { getShareProfile, shareChat, type ShareProfile, type ShareChatHistoryItem } from '@/api/ai'

const route = useRoute()
const shareKey = String(route.params.shareKey || '')

const profile = ref<ShareProfile | null>(null)
const notFound = ref(false)
const messages = ref<ShareChatHistoryItem[]>([])
const inputMessage = ref('')
const sending = ref(false)
const messageListRef = ref<HTMLElement>()

const scrollToBottom = () => {
  void nextTick(() => {
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight, behavior: 'smooth' })
  })
}

const loadProfile = async () => {
  if (!shareKey) {
    notFound.value = true
    return
  }
  try {
    const res = await getShareProfile(shareKey)
    profile.value = (res as any).data || null
    document.title = profile.value?.agentName || 'AI 对话'
  } catch {
    notFound.value = true
  }
}

const handleSend = async () => {
  const message = inputMessage.value.trim()
  if (!message || sending.value) return
  inputMessage.value = ''
  messages.value.push({ role: 'user', content: message })
  scrollToBottom()
  sending.value = true
  try {
    // 无状态接口：随请求携带最近 10 轮历史（不含刚发送的这条）
    const history = messages.value.slice(0, -1).slice(-20)
    const res = await shareChat(shareKey, message, history)
    const reply = (res as any).data?.reply || ''
    messages.value.push({ role: 'assistant', content: reply || '(无回复内容)' })
  } catch (e: any) {
    messages.value.pop()
    inputMessage.value = message
    ElMessage.error(e?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  void loadProfile()
})
</script>

<style lang="scss" scoped>
.share-chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.share-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.share-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;

  .share-header-text {
    display: flex;
    flex-direction: column;
    min-width: 0;

    strong { font-size: 15px; color: #303133; }

    .share-desc {
      font-size: 12px;
      color: #909399;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.share-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.share-message {
  display: flex;

  &.user { justify-content: flex-end; }

  .bubble {
    max-width: 76%;
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
    background: #fff;
    color: #303133;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }

  &.user .bubble {
    background: #409eff;
    color: #fff;
  }
}

.bubble-loading {
  display: flex;
  gap: 4px;
  align-items: center;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #c0c4cc;
    animation: share-dot 1.2s infinite ease-in-out;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes share-dot {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.share-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #e4e7ed;

  .el-input { flex: 1; }
}
</style>
