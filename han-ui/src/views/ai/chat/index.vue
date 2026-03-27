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
          <el-select
            v-model="selectedModelId"
            data-testid="ai-chat-model-select"
            placeholder="选择模型"
            size="small"
            style="width: 180px"
            @change="handleModelChange"
          >
            <el-option v-for="m in modelList" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
        </div>
      </div>

      <div class="chat-workspace">
        <div class="chat-thread">
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

        <aside class="chat-inspector" data-testid="ai-chat-inspector">
          <section class="inspector-section" data-testid="ai-chat-context-panel">
            <div class="inspector-section-header">
              <div>
                <div class="inspector-eyebrow">应用上下文</div>
                <h3>{{ contextApplication?.name || contextTypeLabel }}</h3>
              </div>
              <el-tag size="small" effect="plain" :type="contextApplication ? 'primary' : 'info'">
                {{ contextTypeLabel }}
              </el-tag>
            </div>
            <p class="inspector-copy">
              {{ contextDescription }}
            </p>
            <div v-loading="contextLoading" class="context-meta-list">
              <div class="context-meta-item">
                <span>运行状态</span>
                <strong>{{ contextApplication?.status || '通用模式' }}</strong>
              </div>
              <div class="context-meta-item">
                <span>调试模型</span>
                <strong>{{ selectedModel?.modelName || resolveModelName(contextApplication?.modelId) || '未选择模型' }}</strong>
              </div>
              <div class="context-meta-item">
                <span>当前会话</span>
                <strong>{{ currentConversation?.title || '新对话' }}</strong>
              </div>
            </div>
            <div class="context-actions">
              <el-button
                type="primary"
                plain
                data-testid="ai-chat-context-detail-button"
                :disabled="!contextApplication"
                @click="openContextDetail"
              >
                查看应用详情
              </el-button>
              <el-button
                data-testid="ai-chat-context-manage-button"
                @click="openContextManagement"
              >
                打开管理页
              </el-button>
            </div>
          </section>

          <section class="inspector-section" data-testid="ai-chat-source-panel">
            <div class="inspector-section-header">
              <div>
                <div class="inspector-eyebrow">知识来源</div>
                <h3>来源卡片</h3>
              </div>
              <el-tag size="small" effect="plain" type="success">
                {{ selectedKnowledgeBases.length }} 个知识库
              </el-tag>
            </div>
            <p class="inspector-copy">
              {{ selectedKnowledgeBases.length > 0 ? '当前先展示知识库规模与状态，后续后端补齐结构化命中片段后，这里会继续承接引用明细。' : '当前上下文还没有绑定知识库，先保留知识来源卡位。' }}
            </p>
            <div class="source-card-list" data-testid="ai-chat-source-card-list">
              <article
                v-for="item in selectedKnowledgeBases"
                :key="String(item.kbId)"
                class="source-card"
                data-testid="ai-chat-source-card"
              >
                <div class="source-card-header">
                  <strong>{{ item.kbName }}</strong>
                  <el-tag size="small" effect="plain" type="success">
                    {{ getKnowledgeStatusLabel(item.status) }}
                  </el-tag>
                </div>
                <div class="source-card-meta">
                  <span>类型：{{ getKnowledgeTypeLabel(item.kbType) }}</span>
                  <span>文档：{{ item.documentCount }}</span>
                  <span>段落：{{ item.paragraphCount }}</span>
                  <span>字符：{{ item.charCount }}</span>
                </div>
              </article>
              <div v-if="selectedKnowledgeBases.length === 0" class="source-card source-card-empty">
                暂无知识来源卡片。后续绑定知识库后，这里会直接展示来源规模和状态。
              </div>
            </div>
          </section>

          <section class="inspector-section" data-testid="ai-chat-execution-panel">
            <div class="inspector-section-header">
              <div>
                <div class="inspector-eyebrow">执行信息</div>
                <h3>解释工作台</h3>
              </div>
              <el-tag size="small" effect="plain" :type="streaming ? 'warning' : 'info'">
                {{ streaming ? '生成中' : '已同步' }}
              </el-tag>
            </div>
            <div class="inspector-stat-list">
              <div
                v-for="item in executionSummaryItems"
                :key="item.label"
                class="inspector-stat-item"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div class="inspector-stage-list" data-testid="ai-chat-execution-stage-list">
              <article
                v-for="item in executionStageItems"
                :key="item.label"
                class="inspector-stage-item"
                :class="`is-${item.state}`"
                data-testid="ai-chat-execution-stage"
              >
                <div class="inspector-stage-main">
                  <span class="inspector-stage-label">{{ item.label }}</span>
                  <p>{{ item.detail }}</p>
                </div>
                <el-tag size="small" effect="plain" :type="item.tagType">
                  {{ item.stateLabel }}
                </el-tag>
              </article>
            </div>
            <div class="inspector-preview">
              <div class="inspector-preview-block">
                <span class="inspector-preview-label">最近问题</span>
                <p>{{ summarizeMessage(latestUserMessage?.content, 120) }}</p>
              </div>
              <div class="inspector-preview-block">
                <span class="inspector-preview-label">最近回复摘要</span>
                <p>{{ summarizeMessage(latestAssistantMessage?.content || streamContent, 160) }}</p>
              </div>
            </div>
          </section>
          <section class="inspector-section" data-testid="ai-chat-source-insight-panel">
            <div class="inspector-section-header">
              <div>
                <div class="inspector-eyebrow">结构化引用</div>
                <h3>引用明细</h3>
              </div>
              <el-tag size="small" effect="plain" :type="latestKnowledgeSources.length > 0 ? 'success' : 'info'">
                {{ knowledgeInsightItems.length }} 条
              </el-tag>
            </div>
            <p class="inspector-copy">
              {{ latestKnowledgeSources.length > 0 ? '当前消息已经回传结构化知识引用，这里直接展示命中片段与摘录。' : knowledgeInsightItems.length > 0 ? '当前先回退展示绑定知识库概况，后续消息返回结构化引用后会自动切到命中明细。' : '当前还没有可展示的知识来源明细。' }}
            </p>
            <div class="source-card-list" data-testid="ai-chat-source-insight-list">
              <article
                v-for="item in knowledgeInsightItems"
                :key="`${String(item.kbId)}-${item.paragraphTitle || 'base'}-${item.mode}`"
                class="source-card"
                data-testid="ai-chat-source-insight-card"
              >
                <div class="source-card-header">
                  <strong>{{ item.kbName }}</strong>
                  <el-tag size="small" effect="plain" :type="item.mode === 'structured' ? 'success' : 'info'">
                    {{ item.mode === 'structured' ? '已命中' : '上下文' }}
                  </el-tag>
                </div>
                <div class="source-card-meta">
                  <span>类型：{{ getKnowledgeTypeLabel(item.kbType) }}</span>
                  <span>状态：{{ getKnowledgeStatusLabel(item.kbStatus) }}</span>
                  <span>文档：{{ item.documentCount ?? 0 }}</span>
                  <span>段落：{{ item.paragraphCount ?? 0 }}</span>
                </div>
                <div v-if="item.paragraphTitle" class="source-card-detail">
                  命中段落：{{ item.paragraphTitle }}
                  <span v-if="item.hitCount">，命中次数 {{ item.hitCount }}</span>
                </div>
                <p v-if="item.excerpt" class="source-card-excerpt">{{ item.excerpt }}</p>
              </article>
              <div v-if="knowledgeInsightItems.length === 0" class="source-card source-card-empty" data-testid="ai-chat-source-insight-empty">
                当前还没有结构化知识引用，等消息命中知识库后会展示在这里。
              </div>
            </div>
          </section>

          <section class="inspector-section" data-testid="ai-chat-tool-trace-panel">
            <div class="inspector-section-header">
              <div>
                <div class="inspector-eyebrow">工具轨迹</div>
                <h3>MCP 服务摘要</h3>
              </div>
              <el-tag size="small" effect="plain" :type="latestToolExecutions.length > 0 ? 'success' : 'info'">
                {{ toolTraceItems.length }} 组
              </el-tag>
            </div>
            <p class="inspector-copy">
              {{ latestToolExecutions.length > 0 ? '当前消息已经回传结构化工具轨迹摘要，这里优先展示真实服务与工具清单。' : toolTraceItems.length > 0 ? '当前先回退展示已绑定的 MCP 服务元数据，后续消息返回真实轨迹后会自动升级。' : '当前还没有可展示的 MCP 服务轨迹。' }}
            </p>
            <div class="source-card-list" data-testid="ai-chat-tool-trace-list">
              <article
                v-for="item in toolTraceItems"
                :key="`${String(item.mcpId)}-${item.mode}`"
                class="source-card"
                data-testid="ai-chat-tool-trace-card"
              >
                <div class="source-card-header">
                  <strong>{{ item.serverName }}</strong>
                  <el-tag size="small" effect="plain" :type="item.mode === 'structured' ? 'success' : 'info'">
                    {{ getTransportTypeLabel(item.transportType) }}
                  </el-tag>
                </div>
                <div class="source-card-meta">
                  <span>状态：{{ getEnableStatusLabel(item.status) }}</span>
                  <span>工具数：{{ item.toolCount }}</span>
                </div>
                <p class="source-card-excerpt">{{ item.summary }}</p>
                <div v-if="item.toolNames.length > 0" class="trace-tool-list">
                  <el-tag
                    v-for="toolName in item.toolNames"
                    :key="toolName"
                    size="small"
                    effect="plain"
                    type="primary"
                  >
                    {{ toolName }}
                  </el-tag>
                </div>
              </article>
              <div v-if="toolTraceItems.length === 0" class="source-card source-card-empty" data-testid="ai-chat-tool-trace-empty">
                当前还没有工具轨迹，等绑定 MCP 服务并回传结构化摘要后会展示在这里。
              </div>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, watch } from 'vue'
import { Plus, Delete, Promotion, ChatDotRound, User, Monitor, Edit, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import {
  getAiAgent,
  getAiWorkflow,
  listConversations,
  listChatMessages,
  deleteConversation,
  renameConversation,
  listAllModels,
  listAllKnowledgeBases,
  listAllMcpServers,
  kbTypeOptions,
  indexStatusOptions,
  transportTypeOptions,
  type AiAgent,
  type AiConversation,
  type AiChatKnowledgeSource,
  type AiChatMessage,
  type AiChatToolTrace,
  type AiModel,
  type AiWorkflow,
  type KnowledgeBase,
  type McpServer
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
const route = useRoute()
const router = useRouter()

const CHAT_STORAGE_KEYS = {
  conversationId: 'HAN-ai-chat-conversation-id',
  modelId: 'HAN-ai-chat-model-id'
} as const

interface ChatContextApplication {
  type: 'agent' | 'workflow'
  id: string
  name: string
  description?: string
  modelId?: string | number
  knowledgeBaseIds: string[]
  mcpServerIds: string[]
  status?: string
  published?: boolean
}

interface InspectorStageItem {
  label: string
  detail: string
  state: 'ready' | 'pending' | 'inactive'
  stateLabel: string
  tagType: 'success' | 'warning' | 'info'
}

interface KnowledgeInsightItem {
  kbId?: string | number
  kbName: string
  kbType?: string
  kbStatus?: string
  documentCount?: number
  paragraphCount?: number
  charCount?: number
  paragraphTitle?: string
  hitCount?: number
  excerpt?: string
  mode: 'structured' | 'fallback'
}

interface ToolTraceInsightItem {
  mcpId?: string | number
  serverName: string
  transportType?: string
  status?: string
  toolCount: number
  toolNames: string[]
  summary: string
  mode: 'structured' | 'fallback'
}

const knowledgeBaseList = ref<KnowledgeBase[]>([])
const mcpServerList = ref<McpServer[]>([])
const contextApplication = ref<ChatContextApplication>()
const contextLoading = ref(false)

const selectedModel = computed(() => {
  return modelList.value.find((item) => String(item.modelId) === String(selectedModelId.value ?? ''))
})

const latestAssistantMessage = computed(() => {
  const assistantMessages = messages.value.filter((item) => item.role === 'assistant')
  return assistantMessages[assistantMessages.length - 1]
})

const latestUserMessage = computed(() => {
  const userMessages = messages.value.filter((item) => item.role === 'user')
  return userMessages[userMessages.length - 1]
})

const routeAgentId = computed(() => normalizeConversationIdQuery(route.query.agentId))
const routeWorkflowId = computed(() => normalizeConversationIdQuery(route.query.workflowId))

const contextType = computed<'agent' | 'workflow' | null>(() => {
  if (routeAgentId.value) {
    return 'agent'
  }
  if (routeWorkflowId.value || currentConversation.value?.workflowId) {
    return 'workflow'
  }
  return null
})

const contextId = computed(() => {
  if (contextType.value === 'agent') {
    return routeAgentId.value
  }
  if (contextType.value === 'workflow') {
    const workflowId = routeWorkflowId.value || currentConversation.value?.workflowId
    return workflowId === undefined || workflowId === null || workflowId === ''
      ? undefined
      : String(workflowId)
  }
  return undefined
})

const contextTypeLabel = computed(() => {
  if (contextType.value === 'agent') {
    return '简单应用 / 智能体'
  }
  if (contextType.value === 'workflow') {
    return '高级应用 / 工作流'
  }
  return '通用对话'
})

const contextDescription = computed(() => {
  if (contextApplication.value?.description) {
    return contextApplication.value.description
  }
  if (contextType.value === 'workflow') {
    return '当前会话已绑定工作流上下文，可以在这里同步查看知识来源和执行阶段。'
  }
  if (contextType.value === 'agent') {
    return '当前会话已绑定智能体上下文，可以继续补充应用解释信息。'
  }
  return '当前页面保持通用对话能力，同时把模型、知识和执行信息收拢到右侧工作台。'
})

const currentKnowledgeBaseIds = computed(() => {
  return contextApplication.value?.knowledgeBaseIds || []
})

const currentMcpServerIds = computed(() => {
  return contextApplication.value?.mcpServerIds || []
})

const selectedKnowledgeBases = computed(() => {
  const targetIds = new Set(currentKnowledgeBaseIds.value.map((item) => String(item)))
  return knowledgeBaseList.value.filter((item) => targetIds.has(String(item.kbId)))
})

const selectedMcpServers = computed(() => {
  const targetIds = new Set(currentMcpServerIds.value.map((item) => String(item)))
  return mcpServerList.value.filter((item) => targetIds.has(String(item.mcpId)))
})

const latestKnowledgeSources = computed<AiChatKnowledgeSource[]>(() => {
  return latestAssistantMessage.value?.knowledgeSources || []
})

const latestToolExecutions = computed<AiChatToolTrace[]>(() => {
  return latestAssistantMessage.value?.toolExecutions || []
})

const knowledgeInsightItems = computed<KnowledgeInsightItem[]>(() => {
  if (latestKnowledgeSources.value.length > 0) {
    return latestKnowledgeSources.value.map((item) => ({
      kbId: item.kbId,
      kbName: item.kbName || `知识库 #${item.kbId || '--'}`,
      kbType: item.kbType,
      kbStatus: item.kbStatus,
      documentCount: item.documentCount,
      paragraphCount: item.paragraphCount,
      charCount: item.charCount,
      paragraphTitle: item.paragraphTitle,
      hitCount: item.hitCount,
      excerpt: item.excerpt,
      mode: 'structured'
    }))
  }
  return selectedKnowledgeBases.value.map((item) => ({
    kbId: item.kbId,
    kbName: item.kbName,
    kbType: item.kbType,
    kbStatus: item.status,
    documentCount: item.documentCount,
    paragraphCount: item.paragraphCount,
    charCount: item.charCount,
    mode: 'fallback'
  }))
})

const toolTraceItems = computed<ToolTraceInsightItem[]>(() => {
  if (latestToolExecutions.value.length > 0) {
    return latestToolExecutions.value.map((item) => ({
      mcpId: item.mcpId,
      serverName: item.serverName || `MCP #${item.mcpId || '--'}`,
      transportType: item.transportType,
      status: item.status,
      toolCount: item.toolCount || 0,
      toolNames: item.toolNames || [],
      summary: item.summary || '当前会话已挂载工具服务。',
      mode: 'structured'
    }))
  }
  return selectedMcpServers.value.map((item) => {
    const parsedToolNames = parseMcpToolNames(item.tools)
    return {
      mcpId: item.mcpId,
      serverName: item.serverName,
      transportType: item.transportType,
      status: item.status,
      toolCount: parsedToolNames.length,
      toolNames: parsedToolNames,
      summary: `当前已绑定 ${item.serverName}，待后端回传真实工具执行轨迹。`,
      mode: 'fallback' as const
    }
  })
})

const executionSummaryItems = computed(() => {
  return [
    {
      label: '当前模型',
      value: selectedModel.value?.modelName || resolveModelName(contextApplication.value?.modelId) || '未选择模型'
    },
    {
      label: '会话 ID',
      value: currentConversationId.value ? String(currentConversationId.value) : '新对话'
    },
    {
      label: '消息数',
      value: String(messages.value.length)
    },
    {
      label: '最近回复 Token',
      value: latestAssistantMessage.value?.tokenCount ? String(latestAssistantMessage.value.tokenCount) : '暂无'
    },
    {
      label: '最近更新时间',
      value: formatDateTime(currentConversation.value?.updateTime || currentConversation.value?.createTime)
    }
  ]
})

const executionStageItems = computed<InspectorStageItem[]>(() => {
  const hasReply = Boolean(latestAssistantMessage.value?.content || streamContent.value)
  const selectedKnowledgeBaseCount = selectedKnowledgeBases.value.length
  const selectedMcpCount = selectedMcpServers.value.length
  return [
    {
      label: '应用上下文',
      detail: contextApplication.value
        ? `当前已绑定${contextTypeLabel.value}「${contextApplication.value.name}」`
        : '当前是通用对话模式，暂未绑定具体应用上下文。',
      state: contextApplication.value ? 'ready' : 'inactive',
      stateLabel: contextApplication.value ? '已绑定' : '通用模式',
      tagType: contextApplication.value ? 'success' : 'info'
    },
    {
      label: '会话定位',
      detail: currentConversation.value
        ? `${currentConversation.value.title || '未命名会话'}，消息数 ${currentConversation.value.messageCount || messages.value.length}`
        : '当前还没有会话记录，发送消息后会自动创建对话。',
      state: currentConversation.value ? 'ready' : 'pending',
      stateLabel: currentConversation.value ? '已定位' : '待创建',
      tagType: currentConversation.value ? 'success' : 'warning'
    },
    {
      label: '知识增强',
      detail: selectedKnowledgeBaseCount > 0
        ? `已绑定 ${selectedKnowledgeBaseCount} 个知识库，当前先展示来源规模，后续再补结构化命中片段。`
        : '当前上下文没有绑定知识库，本次不会展示知识命中。',
      state: selectedKnowledgeBaseCount > 0 ? 'pending' : 'inactive',
      stateLabel: selectedKnowledgeBaseCount > 0 ? '待命中' : '未启用',
      tagType: selectedKnowledgeBaseCount > 0 ? 'warning' : 'info'
    },
    {
      label: '工具执行',
      detail: selectedMcpCount > 0
        ? `已绑定 ${selectedMcpCount} 个 MCP 服务，当前先保留执行轨迹位。`
        : '当前上下文没有绑定 MCP 服务，本次不会展示工具轨迹。',
      state: selectedMcpCount > 0 ? 'pending' : 'inactive',
      stateLabel: selectedMcpCount > 0 ? '待轨迹' : '未启用',
      tagType: selectedMcpCount > 0 ? 'warning' : 'info'
    },
    {
      label: '模型回复',
      detail: hasReply
        ? '当前已经生成助手回复，可以继续从右侧查看摘要与上下文。'
        : streaming.value
          ? '模型正在生成回复，解释面板会在完成后刷新摘要。'
          : '当前还没有助手回复。',
      state: hasReply ? 'ready' : (streaming.value ? 'pending' : 'inactive'),
      stateLabel: hasReply ? '已生成' : (streaming.value ? '生成中' : '待生成'),
      tagType: hasReply ? 'success' : (streaming.value ? 'warning' : 'info')
    }
  ]
})

onMounted(async () => {
  await Promise.all([
    loadModels(),
    loadKnowledgeBaseCatalog(),
    loadMcpServerCatalog(),
    loadConversations()
  ])
  await restoreConversationState()
  await loadContextApplication()
})

watch(
  () => route.query.conversationId,
  async (value) => {
    const targetConversationId = normalizeConversationIdQuery(value)
    if (!targetConversationId) {
      return
    }
    if (String(currentConversationId.value ?? '') === targetConversationId) {
      return
    }
    if (conversationList.value.length === 0) {
      await loadConversations()
    }
    const targetConversation = conversationList.value.find((item) => {
      return String(item.conversationId) === targetConversationId
    })
    if (!targetConversation) {
      ElMessage.warning('指定对话不存在或已删除')
      syncConversationRoute(null)
      persistConversationId(null)
      return
    }
    await selectConversation(targetConversation, { syncRoute: false })
  }
)

watch(
  [routeAgentId, routeWorkflowId, () => currentConversation.value?.workflowId],
  async () => {
    await loadContextApplication()
  }
)

async function loadModels() {
  try {
    const res = await listAllModels('LLM')
    modelList.value = (res as any).data || []
    const currentModel = modelList.value.find((item) => String(item.modelId) === String(selectedModelId.value ?? ''))
    const storedModelId = restoreSelectedModelId()
    const storedModel = modelList.value.find((item) => String(item.modelId) === String(storedModelId ?? ''))
    if (currentModel) {
      selectedModelId.value = currentModel.modelId
    } else if (storedModel) {
      selectedModelId.value = storedModel.modelId
    } else if (modelList.value.length > 0) {
      selectedModelId.value = modelList.value[0].modelId
    }
    persistSelectedModelId(selectedModelId.value)
  } catch (e) {
    console.error('加载模型列表失败', e)
  }
}

async function loadKnowledgeBaseCatalog() {
  try {
    const res = await listAllKnowledgeBases()
    knowledgeBaseList.value = (res as any).data || []
  } catch (e) {
    console.error('加载知识库列表失败', e)
  }
}

async function loadMcpServerCatalog() {
  try {
    const res = await listAllMcpServers()
    mcpServerList.value = (res as any).data || []
  } catch (e) {
    console.error('加载MCP服务列表失败', e)
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

async function loadContextApplication() {
  const type = contextType.value
  const id = contextId.value
  if (!type || !id) {
    contextApplication.value = undefined
    return
  }
  contextLoading.value = true
  try {
    const res = type === 'agent' ? await getAiAgent(id) : await getAiWorkflow(id)
    const data = (res as any).data
    if (!data) {
      contextApplication.value = undefined
      return
    }
    contextApplication.value = normalizeContextApplication(type, data)
  } catch (e) {
    console.error('加载应用上下文失败', e)
    contextApplication.value = undefined
  } finally {
    contextLoading.value = false
  }
}

async function reloadCurrentConversationMessages() {
  if (!currentConversationId.value) {
    return
  }
  try {
    const res = await listChatMessages(currentConversationId.value)
    messages.value = (res as any).data || []
    currentConversation.value = conversationList.value.find((item) => item.conversationId === currentConversationId.value)
    scrollToBottom()
  } catch (e) {
    console.error('刷新当前会话消息失败', e)
  }
}

async function restoreConversationState() {
  const routeConversationId = normalizeConversationIdQuery(route.query.conversationId)
  const storedConversationId = routeConversationId || restoreConversationId()
  const storedConversation = storedConversationId
    ? conversationList.value.find((item) => String(item.conversationId) === storedConversationId)
    : undefined
  const fallbackConversation = routeConversationId ? undefined : conversationList.value[0]
  const targetConversation = storedConversation || fallbackConversation
  if (!targetConversation) {
    if (routeConversationId) {
      ElMessage.warning('指定对话不存在或已删除')
      syncConversationRoute(null)
      persistConversationId(null)
    }
    return
  }
  if (!routeConversationId && !storedConversation && storedConversationId) {
    persistConversationId(null)
  }
  await selectConversation(targetConversation)
}

async function syncCurrentConversationState() {
  await loadConversations()
  if (!currentConversationId.value && conversationList.value.length > 0) {
    const latest = conversationList.value[0]
    currentConversationId.value = latest.conversationId
    currentConversation.value = latest
  }
  persistConversationId(currentConversationId.value ?? null)
  syncConversationRoute(currentConversationId.value ?? null)
  await reloadCurrentConversationMessages()
}

async function selectConversation(conv: AiConversation, options: { syncRoute?: boolean } = {}) {
  const { syncRoute = true } = options
  currentConversationId.value = conv.conversationId
  currentConversation.value = conv
  persistConversationId(conv.conversationId)
  if (syncRoute) {
    syncConversationRoute(conv.conversationId)
  }
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
  persistConversationId(null)
  syncConversationRoute(null)
}

async function handleDeleteConversation(id: string | number) {
  try {
    await ElMessageBox.confirm('确认删除该对话？', '提示', { type: 'warning' })
    await deleteConversation(id)
    if (String(currentConversationId.value ?? '') === String(id)) {
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

    await syncCurrentConversationState()
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
  scrollToBottom()
}

function handleModelChange(value: string | number) {
  selectedModelId.value = value
  persistSelectedModelId(value)
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
  await syncCurrentConversationState()
}

function normalizeContextApplication(type: 'agent' | 'workflow', data: AiAgent | AiWorkflow): ChatContextApplication {
  if (type === 'agent') {
    const agent = data as AiAgent
    return {
      type,
      id: String(agent.agentId),
      name: agent.agentName,
      description: agent.description,
      modelId: agent.modelId,
      knowledgeBaseIds: parseIdArray(agent.knowledgeBaseIds),
      mcpServerIds: parseIdArray(agent.mcpServerIds),
      status: agent.status,
      published: Boolean(agent.published)
    }
  }
  const workflow = data as AiWorkflow
  return {
    type,
    id: String(workflow.workflowId),
    name: workflow.workflowName,
    description: workflow.description,
    modelId: workflow.modelId,
    knowledgeBaseIds: parseIdArray(workflow.knowledgeBaseIds),
    mcpServerIds: parseIdArray(workflow.mcpServerIds),
    status: workflow.status,
    published: Boolean(workflow.published)
  }
}

function parseIdArray(value?: string) {
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : []
  } catch {
    return []
  }
}

function resolveModelName(modelId?: string | number) {
  if (modelId === undefined || modelId === null || modelId === '') {
    return ''
  }
  return modelList.value.find((item) => String(item.modelId) === String(modelId))?.modelName || ''
}

function formatDateTime(value?: string) {
  if (!value) {
    return '暂无'
  }
  return value.length >= 16 ? value.slice(0, 16) : value
}

function getKnowledgeTypeLabel(value?: string) {
  return kbTypeOptions.find((item) => item.value === value)?.label || value || '未知'
}

function getKnowledgeStatusLabel(value?: string) {
  return indexStatusOptions.find((item) => item.value === value)?.label || value || '待处理'
}

function getTransportTypeLabel(value?: string) {
  return transportTypeOptions.find((item) => item.value === value)?.label || value || '未知'
}

function getEnableStatusLabel(value?: string) {
  if (value === '0') {
    return '启用'
  }
  if (value === '1') {
    return '停用'
  }
  return value || '未知'
}

function parseMcpToolNames(rawTools?: string) {
  if (!rawTools) {
    return []
  }
  try {
    const parsed = JSON.parse(rawTools)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .map((item) => typeof item?.name === 'string' ? item.name : '')
      .filter((item): item is string => Boolean(item))
  } catch {
    return []
  }
}

function summarizeMessage(content?: string, limit = 96) {
  if (!content) {
    return '暂无'
  }
  const normalized = content.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '暂无'
  }
  return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized
}

function openContextDetail() {
  if (!contextApplication.value || !contextType.value) {
    ElMessage.info('当前通用对话暂无应用详情入口')
    return
  }
  router.push(`/ai/application/${contextType.value}/${contextApplication.value.id}`)
}

function openContextManagement() {
  if (contextType.value === 'agent') {
    router.push('/ai/agent')
    return
  }
  if (contextType.value === 'workflow') {
    router.push('/ai/workflow')
    return
  }
  ElMessage.info('当前通用对话没有专属管理入口')
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

function persistConversationId(value: string | number | null | undefined) {
  try {
    if (value === null || value === undefined || value === '') {
      localStorage.removeItem(CHAT_STORAGE_KEYS.conversationId)
      return
    }
    localStorage.setItem(CHAT_STORAGE_KEYS.conversationId, String(value))
  } catch (error) {
    console.warn('Persist conversation id failed', error)
  }
}

function normalizeConversationIdQuery(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] ? String(value[0]) : undefined
  }
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  return String(value)
}

function syncConversationRoute(value: string | number | null | undefined) {
  const normalizedValue = normalizeConversationIdQuery(value)
  const currentValue = normalizeConversationIdQuery(route.query.conversationId)
  if (normalizedValue === currentValue) {
    return
  }
  const nextQuery = { ...route.query }
  if (normalizedValue) {
    nextQuery.conversationId = normalizedValue
  } else {
    delete nextQuery.conversationId
  }
  router.replace({
    path: route.path,
    query: nextQuery
  }).catch(() => undefined)
}

function restoreConversationId() {
  try {
    return localStorage.getItem(CHAT_STORAGE_KEYS.conversationId) || undefined
  } catch (error) {
    console.warn('Restore conversation id failed', error)
    return undefined
  }
}

function persistSelectedModelId(value: string | number | null | undefined) {
  try {
    if (value === null || value === undefined || value === '') {
      localStorage.removeItem(CHAT_STORAGE_KEYS.modelId)
      return
    }
    localStorage.setItem(CHAT_STORAGE_KEYS.modelId, String(value))
  } catch (error) {
    console.warn('Persist model id failed', error)
  }
}

function restoreSelectedModelId() {
  try {
    return localStorage.getItem(CHAT_STORAGE_KEYS.modelId) || undefined
  } catch (error) {
    console.warn('Restore model id failed', error)
    return undefined
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

  .chat-workspace {
    flex: 1;
    min-height: 0;
    display: flex;
  }

  .chat-thread {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
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

  .chat-inspector {
    width: 360px;
    flex-shrink: 0;
    border-left: 1px solid #e4e7ed;
    background: #fbfcfe;
    padding: 18px 18px 20px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .inspector-section {
    background: #fff;
    border: 1px solid #e9edf5;
    border-radius: 16px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .inspector-section-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;

    h3 {
      margin: 4px 0 0;
      font-size: 16px;
      color: #1f2a37;
    }
  }

  .inspector-eyebrow {
    font-size: 12px;
    line-height: 1;
    color: #8a94a6;
    letter-spacing: 0.08em;
  }

  .inspector-copy {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: #5f6b7a;
  }

  .context-meta-list,
  .inspector-stat-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .context-meta-item,
  .inspector-stat-item {
    border-radius: 12px;
    background: #f7f9fc;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;

    span {
      font-size: 12px;
      color: #8a94a6;
    }

    strong {
      color: #1f2a37;
      word-break: break-word;
    }
  }

  .context-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .source-card-list,
  .inspector-stage-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .source-card {
    border-radius: 14px;
    border: 1px solid #ecf1f7;
    background: #f9fbff;
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .source-card-empty {
    color: #7d8798;
    line-height: 1.7;
  }

  .source-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    strong {
      color: #1f2a37;
    }
  }

  .source-card-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 12px;
    font-size: 12px;
    color: #5f6b7a;
  }

  .source-card-detail {
    font-size: 13px;
    line-height: 1.6;
    color: #374151;
  }

  .source-card-excerpt {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: #5f6b7a;
    word-break: break-word;
  }

  .trace-tool-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .inspector-stage-item {
    border-radius: 14px;
    border: 1px solid #ecf1f7;
    background: #fff;
    padding: 14px;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;

    &.is-ready {
      border-color: #cfe7d5;
      background: #f4fbf6;
    }

    &.is-pending {
      border-color: #f7dfb6;
      background: #fff9ee;
    }
  }

  .inspector-stage-main {
    display: flex;
    flex-direction: column;
    gap: 6px;

    p {
      margin: 0;
      font-size: 13px;
      line-height: 1.7;
      color: #5f6b7a;
    }
  }

  .inspector-stage-label,
  .inspector-preview-label {
    font-size: 13px;
    font-weight: 600;
    color: #1f2a37;
  }

  .inspector-preview {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .inspector-preview-block {
    border-radius: 14px;
    background: #f7f9fc;
    padding: 14px;

    p {
      margin: 8px 0 0;
      font-size: 13px;
      line-height: 1.7;
      color: #5f6b7a;
      word-break: break-word;
    }
  }
}

.cursor-blink {
  animation: blink 0.8s infinite;
  font-weight: bold;
}

@media (max-width: 1280px) {
  .chat-main {
    .chat-inspector {
      width: 320px;
    }
  }
}

@media (max-width: 1100px) {
  .ai-chat-container {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 84px);
  }

  .chat-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }

  .chat-main {
    min-height: 0;

    .chat-workspace {
      flex-direction: column;
    }

    .chat-inspector {
      width: 100%;
      border-left: none;
      border-top: 1px solid #e4e7ed;
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
