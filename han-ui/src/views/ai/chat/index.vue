<template>
  <div class="ai-chat-container" data-testid="ai-chat-page">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button
          type="primary"
          :icon="Plus"
          class="new-chat-btn"
          data-testid="ai-chat-new-button"
          @click="handleNewChat"
        >
          新建对话
        </el-button>
      </div>
      <div class="conversation-list" data-testid="ai-chat-conversation-list">
        <div
          v-for="conv in conversationList"
          :key="conv.conversationId"
          :class="['conversation-item', { active: currentConversationId === conv.conversationId }]"
          data-testid="ai-chat-conversation-item"
          @click="selectConversation(conv)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span class="conv-title">{{ conv.title }}</span>
          <el-icon class="conv-delete" @click.stop="handleDeleteConversation(conv.conversationId)"><Delete /></el-icon>
        </div>
        <div v-if="conversationList.length === 0" class="empty-tip">暂无对话记录</div>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-main">
      <!-- 顶部栏 -->
      <div class="chat-header">
        <div class="chat-title">
          <template v-if="currentConversationId">
            <span v-if="!editingTitle" @dblclick="startEditTitle">{{ currentConversation?.title || 'AI对话' }}</span>
            <el-input
              v-else
              v-model="editTitleValue"
              size="small"
              style="width: 200px"
              @blur="saveTitle"
              @keyup.enter="saveTitle"
              autofocus
            />
          </template>
          <span v-else>AI 智能助手</span>
        </div>
        <div class="chat-header-actions">
          <el-select v-model="selectedModelId" placeholder="选择模型" size="small" style="width: 180px">
            <el-option v-for="m in modelList" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesRef" data-testid="ai-chat-message-list">
        <div v-if="messages.length === 0 && !currentConversationId" class="welcome-screen">
          <el-icon :size="64" color="#409eff"><ChatDotRound /></el-icon>
          <h2>欢迎使用 HAN AI 助手</h2>
          <p>选择一个模型，开始对话吧</p>
        </div>
        <div
          v-for="(msg, idx) in messages"
          :key="msg.messageId || msg.sortOrder"
          :class="['message-item', msg.role]"
          data-testid="ai-chat-message"
          :data-role="msg.role"
          :data-message-id="String(msg.messageId ?? '')"
        >
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'user'" :size="36" style="background: #409eff">
              <el-icon><User /></el-icon>
            </el-avatar>
            <el-avatar v-else :size="36" style="background: #67c23a">
              <el-icon><Monitor /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI 助手' }}</div>
            <!-- 编辑模式 -->
            <template v-if="editingMessageId === msg.messageId && msg.role === 'user'">
              <el-input
                v-model="editMessageContent"
                data-testid="ai-chat-edit-input"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 8 }"
              />
              <div class="edit-actions">
                <el-button size="small" type="primary" data-testid="ai-chat-edit-submit-button" @click="submitEditMessage(msg)">发送</el-button>
                <el-button size="small" @click="cancelEditMessage">取消</el-button>
              </div>
            </template>
            <template v-else>
              <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-actions" v-if="!streaming">
                <el-button v-if="msg.role === 'user'" type="info" link size="small" data-testid="ai-chat-edit-button" @click="startEditMessage(msg)">
                  <el-icon><Edit /></el-icon>编辑
                </el-button>
                <el-button v-if="msg.role === 'assistant' && idx === messages.length - 1" type="info" link size="small" data-testid="ai-chat-regenerate-button" @click="handleRegenerate">
                  <el-icon><RefreshRight /></el-icon>重新生成
                </el-button>
              </div>
            </template>
          </div>
        </div>
        <!-- 流式输出中 -->
        <div v-if="streaming" class="message-item assistant" data-testid="ai-chat-streaming">
          <div class="message-avatar">
            <el-avatar :size="36" style="background: #67c23a">
              <el-icon><Monitor /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-role">AI 助手</div>
            <div class="message-text">
              <span v-html="renderMarkdown(streamContent)"></span>
              <span class="cursor-blink">|</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div v-if="streaming" class="stop-generate">
          <el-button type="danger" size="small" round data-testid="ai-chat-stop-button" @click="handleStopGenerate">
            <el-icon><VideoPause /></el-icon>停止生成
          </el-button>
        </div>
        <div class="input-wrapper">
          <el-input
            data-testid="ai-chat-input"
            v-model="inputMessage"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入消息，按 Enter 发送，Shift+Enter 换行"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
            :disabled="sending"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            circle
            class="send-btn"
            data-testid="ai-chat-send-button"
            :loading="sending"
            :disabled="!inputMessage.trim() || !selectedModelId"
            @click="handleSend"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Delete, Promotion, ChatDotRound, User, Monitor, Edit, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import {
  listConversations,
  listChatMessages,
  deleteConversation,
  renameConversation,
  listAllModels,
  type AiConversation,
  type AiChatMessage,
  type AiModel
} from '@/api/ai'
import { useUserStore } from '@/stores/user'
import { consumeAiStreamResponse, requestAiStream } from '@/utils/ai-stream'

// marked 配置：启用代码高亮
marked.setOptions({
  breaks: true,
  gfm: true,
})

// 自定义 renderer 实现代码高亮
const renderer = new marked.Renderer()
renderer.code = function ({ text, lang }: { text: string; lang?: string }) {
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const highlighted = hljs.highlight(text, { language }).value
  return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
}
marked.use({ renderer })

const messagesRef = ref<HTMLElement>()
const inputMessage = ref('')
const sending = ref(false)
const streaming = ref(false)
const streamContent = ref('')
const selectedModelId = ref<string | number>()
const currentConversationId = ref<string | number>()
const currentConversation = ref<AiConversation>()
const conversationList = ref<AiConversation[]>([])
const messages = ref<AiChatMessage[]>([])
const modelList = ref<AiModel[]>([])
const abortController = ref<AbortController | null>(null)
const editingTitle = ref(false)
const editTitleValue = ref('')
const editingMessageId = ref<string | number | null>(null)
const editMessageContent = ref('')

onMounted(async () => {
  await loadModels()
  await loadConversations()
})

async function loadModels() {
  try {
    const res = await listAllModels('LLM')
    modelList.value = (res as any).data || []
    if (modelList.value.length > 0 && !selectedModelId.value) {
      selectedModelId.value = modelList.value[0].modelId
    }
  } catch (e) {
    console.error('加载模型列表失败', e)
  }
}

async function loadConversations() {
  try {
    const res = await listConversations({ pageNum: 1, pageSize: 50 })
    conversationList.value = (res as any).data?.rows || []
  } catch (e) {
    console.error('加载会话列表失败', e)
  }
}

async function selectConversation(conv: AiConversation) {
  currentConversationId.value = conv.conversationId
  currentConversation.value = conv
  try {
    const res = await listChatMessages(conv.conversationId)
    messages.value = (res as any).data || []
    scrollToBottom()
  } catch (e) {
    console.error('加载消息失败', e)
  }
}

function handleNewChat() {
  currentConversationId.value = undefined
  currentConversation.value = undefined
  messages.value = []
  inputMessage.value = ''
}

async function handleDeleteConversation(id: string | number) {
  try {
    await ElMessageBox.confirm('确认删除该对话？', '提示', { type: 'warning' })
    await deleteConversation(id)
    if (currentConversationId.value === id) {
      handleNewChat()
    }
    await loadConversations()
    ElMessage.success('删除成功')
  } catch (e) {
    // 取消操作
  }
}

async function handleSend() {
  const msg = inputMessage.value.trim()
  if (!msg || !selectedModelId.value || sending.value) return

  sending.value = true
  inputMessage.value = ''

  const userMsg: AiChatMessage = {
    messageId: Date.now(),
    conversationId: currentConversationId.value || 0,
    role: 'user',
    content: msg,
    sortOrder: messages.value.length + 1,
  }
  messages.value.push(userMsg)
  scrollToBottom()

  streaming.value = true
  streamContent.value = ''

  try {
    const userStore = useUserStore()
    const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
    abortController.value = new AbortController()
    const fullContent = await requestAiStream({
      baseUrl,
      path: '/ai/chat/stream',
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        conversationId: currentConversationId.value || null,
        modelId: selectedModelId.value,
        message: msg
      },
      signal: abortController.value.signal,
      onDelta: ({ fullContent }) => {
        streamContent.value = fullContent
        scrollToBottom()
      },
      onError: (message) => {
        ElMessage.error('AI回复出错: ' + (message || '未知错误'))
      }
    })

    streaming.value = false
    if (fullContent) {
      messages.value.push({
        messageId: Date.now() + 1,
        conversationId: currentConversationId.value || 0,
        role: 'assistant',
        content: fullContent,
        sortOrder: messages.value.length + 1,
      })
    }

    await loadConversations()
    if (!currentConversationId.value && conversationList.value.length > 0) {
      const latest = conversationList.value[0]
      currentConversationId.value = latest.conversationId
      currentConversation.value = latest
    }
  } catch (e: any) {
    streaming.value = false
    ElMessage.error('发送失败: ' + (e.message || '未知错误'))
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

// ==================== 停止生成 ====================
function handleStopGenerate() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  streaming.value = false
  sending.value = false
  if (streamContent.value) {
    messages.value.push({
      messageId: Date.now() + 1,
      conversationId: currentConversationId.value || 0,
      role: 'assistant',
      content: streamContent.value + '\n\n*[已停止生成]*',
      sortOrder: messages.value.length + 1,
    })
    streamContent.value = ''
  }
}

// ==================== 重新生成 ====================
async function handleRegenerate() {
  if (!currentConversationId.value || streaming.value) return
  // 移除界面上最后一条 assistant 消息
  if (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'assistant') {
    messages.value.pop()
  }
  sending.value = true
  streaming.value = true
  streamContent.value = ''
  try {
    const userStore = useUserStore()
    const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
    abortController.value = new AbortController()
    const response = await fetch(`${baseUrl}/ai/chat/regenerate/${currentConversationId.value}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userStore.token}`,
        ...(userStore.tenantId ? { 'X-Tenant-Id': String(userStore.tenantId) } : {})
      },
      signal: abortController.value.signal
    })
    await processSSEResponse(response)
  } catch (e: any) {
    if (e.name !== 'AbortError') {
      streaming.value = false
      ElMessage.error('重新生成失败: ' + (e.message || '未知错误'))
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

// ==================== 消息编辑 ====================
function startEditMessage(msg: AiChatMessage) {
  editingMessageId.value = msg.messageId
  editMessageContent.value = msg.content
}

function cancelEditMessage() {
  editingMessageId.value = null
  editMessageContent.value = ''
}

async function submitEditMessage(msg: AiChatMessage) {
  if (!editMessageContent.value.trim() || !currentConversationId.value) return
  editingMessageId.value = null

  // 删除该消息及之后的消息（界面上）
  const idx = messages.value.findIndex(m => m.messageId === msg.messageId)
  if (idx >= 0) {
    messages.value = messages.value.slice(0, idx)
  }

  sending.value = true
  streaming.value = true
  streamContent.value = ''

  // 添加编辑后的用户消息到界面
  messages.value.push({
    messageId: Date.now(),
    conversationId: currentConversationId.value,
    role: 'user',
    content: editMessageContent.value,
    sortOrder: messages.value.length + 1,
  })
  scrollToBottom()

  try {
    const userStore = useUserStore()
    const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
    abortController.value = new AbortController()
    const response = await fetch(`${baseUrl}/ai/chat/edit-regenerate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userStore.token}`,
        ...(userStore.tenantId ? { 'X-Tenant-Id': String(userStore.tenantId) } : {})
      },
      body: JSON.stringify({
        conversationId: currentConversationId.value,
        messageId: msg.messageId,
        content: editMessageContent.value
      }),
      signal: abortController.value.signal
    })
    await processSSEResponse(response)
  } catch (e: any) {
    if (e.name !== 'AbortError') {
      streaming.value = false
      ElMessage.error('发送失败: ' + (e.message || '未知错误'))
    }
  } finally {
    sending.value = false
    editMessageContent.value = ''
    scrollToBottom()
  }
}

// ==================== 会话重命名 ====================
function startEditTitle() {
  editingTitle.value = true
  editTitleValue.value = currentConversation.value?.title || ''
}

async function saveTitle() {
  editingTitle.value = false
  if (!editTitleValue.value.trim() || !currentConversationId.value) return
  try {
    await renameConversation(currentConversationId.value, editTitleValue.value)
    if (currentConversation.value) {
      currentConversation.value.title = editTitleValue.value
    }
    await loadConversations()
  } catch {
    ElMessage.error('重命名失败')
  }
}

// ==================== SSE 响应处理 ====================
async function processSSEResponse(response: Response) {
  const fullContent = await consumeAiStreamResponse(response, {
    onDelta: ({ fullContent }) => {
      streamContent.value = fullContent
      scrollToBottom()
    },
    onError: (message) => {
      ElMessage.error('AI回复出错: ' + (message || '未知错误'))
    }
  })

  streaming.value = false
  if (fullContent) {
    messages.value.push({
      messageId: Date.now() + 1,
      conversationId: currentConversationId.value || 0,
      role: 'assistant',
      content: fullContent,
      sortOrder: messages.value.length + 1,
    })
  }
  await loadConversations()
  if (!currentConversationId.value && conversationList.value.length > 0) {
    const latest = conversationList.value[0]
    currentConversationId.value = latest.conversationId
    currentConversation.value = latest
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    return marked.parse(content) as string
  } catch {
    return content
  }
}
</script>

<style scoped lang="scss">
.ai-chat-container {
  display: flex;
  height: calc(100vh - 84px);
  background: #f5f7fa;
}

.chat-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    padding: 16px;
    border-bottom: 1px solid #e4e7ed;
    .new-chat-btn {
      width: 100%;
    }
  }

  .conversation-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;

    .conversation-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      margin-bottom: 4px;

      &:hover {
        background: #f0f2f5;
        .conv-delete { opacity: 1; }
      }

      &.active {
        background: #ecf5ff;
        color: #409eff;
      }

      .conv-title {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 14px;
      }

      .conv-delete {
        opacity: 0;
        color: #909399;
        transition: opacity 0.2s;
        &:hover { color: #f56c6c; }
      }
    }

    .empty-tip {
      text-align: center;
      color: #909399;
      padding: 40px 0;
      font-size: 14px;
    }
  }
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;

  .chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    background: #fff;
    border-bottom: 1px solid #e4e7ed;

    .chat-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;

    .welcome-screen {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #909399;
      h2 { margin: 16px 0 8px; color: #303133; }
      p { font-size: 14px; }
    }

    .message-item {
      display: flex;
      gap: 12px;
      margin-bottom: 24px;

      &.user {
        flex-direction: row-reverse;
        .message-content {
          align-items: flex-end;
          .message-role { text-align: right; }
          .message-text {
            background: #409eff;
            color: #fff;
            border-radius: 12px 2px 12px 12px;
          }
        }
      }

      &.assistant {
        .message-content .message-text {
          background: #fff;
          border: 1px solid #e4e7ed;
          border-radius: 2px 12px 12px 12px;
        }
      }

      .message-content {
        display: flex;
        flex-direction: column;
        max-width: 70%;

        .message-role {
          font-size: 12px;
          color: #909399;
          margin-bottom: 4px;
        }

        .message-text {
          padding: 12px 16px;
          font-size: 14px;
          line-height: 1.6;
          word-break: break-word;

          :deep(pre) {
            background: #1e1e1e;
            color: #d4d4d4;
            padding: 12px 16px;
            border-radius: 8px;
            overflow-x: auto;
            margin: 8px 0;
            position: relative;
          }

          :deep(code) {
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            font-size: 13px;
          }

          :deep(code:not([class])) {
            background: rgba(0,0,0,0.06);
            padding: 2px 6px;
            border-radius: 3px;
            color: #c7254e;
          }

          :deep(table) {
            border-collapse: collapse;
            width: 100%;
            margin: 8px 0;
            th, td {
              border: 1px solid #dcdfe6;
              padding: 8px 12px;
              text-align: left;
            }
            th {
              background: #f5f7fa;
              font-weight: 600;
            }
          }

          :deep(ul), :deep(ol) {
            padding-left: 20px;
            margin: 4px 0;
          }

          :deep(li) {
            margin: 2px 0;
          }

          :deep(blockquote) {
            border-left: 4px solid #409eff;
            padding: 4px 12px;
            margin: 8px 0;
            background: #f0f7ff;
            color: #606266;
          }

          :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
            margin: 12px 0 6px;
          }

          :deep(p) {
            margin: 4px 0;
          }

          :deep(hr) {
            border: none;
            border-top: 1px solid #e4e7ed;
            margin: 12px 0;
          }

          :deep(a) {
            color: #409eff;
            text-decoration: none;
            &:hover { text-decoration: underline; }
          }
        }

        .message-actions {
          margin-top: 6px;
          opacity: 0;
          transition: opacity 0.2s;
        }

        .edit-actions {
          display: flex;
          gap: 8px;
          margin-top: 8px;
        }
      }

      &:hover .message-actions {
        opacity: 1;
      }
    }
  }

  .chat-input-area {
    padding: 16px 20px;
    background: #fff;
    border-top: 1px solid #e4e7ed;

    .stop-generate {
      display: flex;
      justify-content: center;
      margin-bottom: 12px;
    }

    .input-wrapper {
      display: flex;
      align-items: flex-end;
      gap: 12px;

      .el-textarea {
        flex: 1;
      }

      .send-btn {
        flex-shrink: 0;
        width: 40px;
        height: 40px;
      }
    }
  }
}

.cursor-blink {
  animation: blink 0.8s infinite;
  font-weight: bold;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
