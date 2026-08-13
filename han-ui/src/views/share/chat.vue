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
        <!-- 开场推荐问题（G1-10）：会话开场渲染可点击提问，点击即作为用户消息发送 -->
        <div
          v-if="suggestedQuestions.length > 0 && messages.length === 0"
          class="share-suggested"
          data-testid="share-suggested-questions"
        >
          <el-button
            v-for="question in suggestedQuestions"
            :key="question"
            round
            class="share-suggested-btn"
            data-testid="share-suggested-question"
            :disabled="sending"
            @click="handleSuggestedQuestion(question)"
          >
            {{ question }}
          </el-button>
        </div>
        <div
          v-for="msg in messages"
          :key="msg.key"
          class="share-message"
          :class="msg.role"
        >
          <!-- 与主对话页一致的 markdown 渲染：净化器已强制 rel="noopener noreferrer"，对外场景安全 -->
          <div v-if="msg.role === 'assistant'" class="bubble markdown-body" v-html="renderMarkdown(msg.content)"></div>
          <div v-else class="bubble">{{ msg.content }}</div>
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
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getShareProfile, shareChat, type ShareProfile, type ShareChatHistoryItem } from '@/api/ai'
import { sanitizeHtml } from '@/utils/sanitize-html'

const route = useRoute()
const shareKey = String(route.params.shareKey || '')

/** 分享页消息：`key` 是稳定标识，避免用数组下标做 key —— 失败回滚 pop 后会导致 DOM 复用错位。 */
interface ShareMessageItem extends ShareChatHistoryItem {
  key: string
}

const profile = ref<ShareProfile | null>(null)
const notFound = ref(false)
const messages = ref<ShareMessageItem[]>([])
const inputMessage = ref('')
const sending = ref(false)
const messageListRef = ref<HTMLElement>()

let messageSeq = 0
const nextMessageKey = () => `m-${++messageSeq}`

function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    return sanitizeHtml(marked.parse(content, { breaks: true, gfm: true }) as string)
  } catch {
    return sanitizeHtml(content)
  }
}

const scrollToBottom = () => {
  void nextTick(() => {
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight, behavior: 'smooth' })
  })
}

// 开场推荐问题（G1-10）：profile 下发 JSON 字符串数组，解析后渲染可点击提问
const suggestedQuestions = computed<string[]>(() => {
  const raw = profile.value?.suggestedQuestions
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is string => typeof item === 'string' && Boolean(item.trim()))
      .map((item) => item.trim())
  } catch {
    return []
  }
})

const handleSuggestedQuestion = (question: string) => {
  if (sending.value) return
  inputMessage.value = question
  void handleSend()
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
  messages.value.push({ key: nextMessageKey(), role: 'user', content: message })
  scrollToBottom()
  sending.value = true
  try {
    // 无状态接口：随请求携带最近 10 轮历史（不含刚发送的这条）
    const history: ShareChatHistoryItem[] = messages.value
      .slice(0, -1)
      .slice(-20)
      .map(({ role, content }) => ({ role, content }))
    const res = await shareChat(shareKey, message, history)
    const reply = (res as any).data?.reply || ''
    messages.value.push({ key: nextMessageKey(), role: 'assistant', content: reply || '(无回复内容)' })
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

// 开场推荐问题（G1-10）
.share-suggested {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;

  .share-suggested-btn {
    max-width: 76%;
    white-space: normal;
    height: auto;
    line-height: 1.6;
    padding-top: 8px;
    padding-bottom: 8px;
    // 覆盖 el-button+el-button 的默认左间距（纵向排列）
    margin-left: 0;
  }
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

  // markdown 渲染后由标签自己控制排版，不能再保留纯文本的 pre-wrap
  .bubble.markdown-body {
    white-space: normal;

    :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
    :deep(ul), :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
    :deep(pre) {
      margin: 8px 0;
      padding: 10px 12px;
      border-radius: 6px;
      background: #f5f7fa;
      overflow-x: auto;
    }
    :deep(code) { font-family: 'Fira Code', Consolas, monospace; font-size: 13px; }
    :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
    :deep(th), :deep(td) { border: 1px solid #e4e7ed; padding: 6px 8px; }
    :deep(img) { max-width: 100%; }
    :deep(a) { color: #409eff; word-break: break-all; }
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
