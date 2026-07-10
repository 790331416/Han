<template>
  <div class="app-container ai-application-detail-page" data-testid="ai-application-detail-page">
    <div v-loading="loading" class="detail-shell">
      <el-empty
        v-if="!applicationDetail && !loading"
        description="未找到对应应用，请返回列表重新选择。"
      />

      <template v-else-if="applicationDetail">
        <section class="detail-hero">
          <div class="detail-hero-main">
            <el-button link class="back-link" @click="router.push('/ai/application')">
              返回应用列表
            </el-button>
            <div class="detail-badges">
              <el-tag :type="applicationType === 'agent' ? 'primary' : 'warning'">
                {{ applicationTypeLabel }}
              </el-tag>
              <el-tag :type="applicationDetail.published ? 'success' : 'info'">
                {{ applicationDetail.published ? '已发布' : '未发布' }}
              </el-tag>
              <el-tag type="info">{{ statusLabel }}</el-tag>
            </div>
            <h2 data-testid="ai-application-detail-title">{{ applicationDetail.name }}</h2>
            <p class="detail-description">
              {{ applicationDetail.description || '当前应用还没有补充业务说明，可以先进入设置页完善定位。' }}
            </p>
            <div class="detail-actions">
              <el-button
                type="primary"
                data-testid="ai-application-open-management-link"
                @click="goToManagement"
              >
                进入管理
              </el-button>
              <el-button
                v-if="canDebugCurrentApplication"
                data-testid="ai-application-open-debug-link"
                @click="goToDebugChat"
              >
                打开调试
              </el-button>
              <el-button
                v-if="applicationType === 'workflow'"
                data-testid="ai-application-open-designer-link"
                @click="goToWorkflowDesigner"
              >
                流程设计
              </el-button>
            </div>
          </div>

          <aside class="detail-hero-side">
            <div class="signal-panel">
              <div class="signal-item">
                <span class="signal-label">关联模型</span>
                <strong>{{ applicationDetail.modelName || '未选择模型' }}</strong>
              </div>
              <div class="signal-item">
                <span class="signal-label">知识库</span>
                <strong>{{ knowledgeBaseNames.length }}</strong>
              </div>
              <div class="signal-item">
                <span class="signal-label">MCP 服务</span>
                <strong>{{ mcpServerNames.length }}</strong>
              </div>
              <div class="signal-item">
                <span class="signal-label">最近更新时间</span>
                <strong>{{ formatDateTime(applicationDetail.createTime) }}</strong>
              </div>
            </div>
          </aside>
        </section>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="概览" name="overview">
            <div class="detail-grid" data-testid="ai-application-overview-panel">
              <div class="overview-main">
                <section class="detail-section">
                  <div class="section-label">应用定位</div>
                  <div class="section-body">
                    {{ applicationDetail.description || '暂无业务描述，可在管理页继续补充。' }}
                  </div>
                </section>

                <section class="detail-section">
                  <div class="section-label">系统提示词</div>
                  <pre class="prompt-block">{{ applicationDetail.systemPrompt || '当前未配置系统提示词。' }}</pre>
                </section>

                <section class="detail-section">
                  <div class="section-label">{{ applicationType === 'agent' ? '欢迎语' : '开场白' }}</div>
                  <div class="section-body">
                    {{ applicationDetail.prologue || '当前未配置欢迎语或开场白。' }}
                  </div>
                </section>
              </div>

              <aside class="resource-side">
                <section class="detail-section">
                  <div class="section-label">绑定资源</div>
                  <div class="resource-group">
                    <span class="resource-title">模型</span>
                    <el-tag size="small" type="primary">
                      {{ applicationDetail.modelName || '未选择模型' }}
                    </el-tag>
                  </div>
                  <div class="resource-group">
                    <span class="resource-title">知识库</span>
                    <div class="tag-list">
                      <el-tag
                        v-for="item in knowledgeBaseNames"
                        :key="item"
                        size="small"
                        effect="plain"
                      >
                        {{ item }}
                      </el-tag>
                      <span v-if="knowledgeBaseNames.length === 0" class="resource-empty">未绑定</span>
                    </div>
                  </div>
                  <div class="resource-group">
                    <span class="resource-title">MCP 服务</span>
                    <div class="tag-list">
                      <el-tag
                        v-for="item in mcpServerNames"
                        :key="item"
                        size="small"
                        effect="plain"
                        type="warning"
                      >
                        {{ item }}
                      </el-tag>
                      <span v-if="mcpServerNames.length === 0" class="resource-empty">未绑定</span>
                    </div>
                  </div>
                </section>
              </aside>
            </div>
          </el-tab-pane>

          <el-tab-pane label="设置" name="settings">
            <div class="settings-panel" data-testid="ai-application-settings-panel">
              <div class="settings-row">
                <span class="settings-label">应用类型</span>
                <span class="settings-value">{{ applicationTypeLabel }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">发布状态</span>
                <span class="settings-value">{{ applicationDetail.published ? '已发布' : '未发布' }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">运行状态</span>
                <span class="settings-value">{{ statusLabel }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">模型</span>
                <span class="settings-value">{{ applicationDetail.modelName || '未选择模型' }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">工作流类型</span>
                <span class="settings-value">{{ workflowTypeLabel }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">知识库数量</span>
                <span class="settings-value">{{ knowledgeBaseNames.length }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">MCP 数量</span>
                <span class="settings-value">{{ mcpServerNames.length }}</span>
              </div>
              <div class="settings-row">
                <span class="settings-label">当前阶段</span>
                <span class="settings-value">
                  这一版先把应用详情工作台做实，保留原有管理页、设计页和调试入口，不重复造一套平行业务。
                </span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="调试" name="debug">
            <div class="debug-panel" data-testid="ai-application-debug-panel">
              <div class="debug-copy">
                <h3>应用调试工作台</h3>
                <p>
                  当前继续复用已有智能体页、工作流页和对话页来完成调试、设计和发布。
                  这层详情页负责把入口、状态和最近运行情况收拢起来，保证主流程更顺手。
                </p>
              </div>
              <div class="debug-actions">
                <el-button
                  type="primary"
                  data-testid="ai-application-debug-manage-button"
                  @click="goToManagement"
                >
                  进入管理页
                </el-button>
                <el-button
                  v-if="canDebugCurrentApplication"
                  data-testid="ai-application-debug-chat-button"
                  @click="goToDebugChat"
                >
                  打开调试对话
                </el-button>
                <el-button
                  v-if="applicationType === 'workflow'"
                  data-testid="ai-application-debug-designer-button"
                  @click="goToWorkflowDesigner"
                >
                  进入流程设计
                </el-button>
              </div>
            </div>

            <div class="detail-grid debug-grid" data-testid="ai-application-extra-panels">
              <section class="detail-section" data-testid="ai-application-publish-panel">
                <div class="section-label">发布状态</div>
                <div class="section-body">
                  <div class="meta-row">
                    <span class="meta-label">当前状态</span>
                    <span class="meta-value">{{ publishStatusLabel }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">最近更新时间</span>
                    <span class="meta-value">{{ formatDateTime(applicationDetail.createTime) }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">发布说明</span>
                    <span class="meta-value">{{ publishHint }}</span>
                  </div>
                  <div
                    class="publish-checklist"
                    data-testid="ai-application-publish-readiness"
                  >
                    <div
                      v-for="item in publishReadiness"
                      :key="item.label"
                      class="check-item"
                      :class="{ 'is-ready': item.ready }"
                    >
                      <div class="check-item-main">
                        <span class="check-item-label">{{ item.label }}</span>
                        <p class="check-item-hint">{{ item.hint }}</p>
                      </div>
                      <strong>{{ item.ready ? '已就绪' : '待完善' }}</strong>
                    </div>
                  </div>
                  <div class="access-actions">
                    <el-button
                      :loading="publishLoading"
                      type="primary"
                      data-testid="ai-application-publish-toggle"
                      @click="togglePublish"
                    >
                      {{ applicationDetail.published ? '取消发布' : '立即发布' }}
                    </el-button>
                    <el-button
                      data-testid="ai-application-copy-detail-link"
                      @click="copyAccessLink(detailPath, '详情入口')"
                    >
                      复制详情入口
                    </el-button>
                  </div>

                  <!-- 公开访问三件套（agent 发布后可用：链接 / iframe / JS 浮窗脚本） -->
                  <div
                    v-if="applicationDetail.type === 'agent' && applicationDetail.published && shareUrl"
                    class="share-embed-block"
                    data-testid="ai-application-share-panel"
                  >
                    <div class="share-embed-item">
                      <div class="share-embed-head">
                        <span class="share-embed-label">公开访问链接</span>
                        <div>
                          <el-button size="small" data-testid="ai-application-copy-share-link" @click="copyShareText(shareUrl, '访问链接')">复制</el-button>
                          <el-button size="small" type="danger" plain data-testid="ai-application-reset-share-key" @click="handleResetShareKey">重置链接</el-button>
                        </div>
                      </div>
                      <el-input :model-value="shareUrl" readonly size="small" data-testid="ai-application-share-url" />
                    </div>
                    <div class="share-embed-item">
                      <div class="share-embed-head">
                        <span class="share-embed-label">iframe 嵌入代码</span>
                        <el-button size="small" data-testid="ai-application-copy-iframe" @click="copyShareText(iframeCode, 'iframe 代码')">复制</el-button>
                      </div>
                      <el-input :model-value="iframeCode" readonly type="textarea" :rows="3" size="small" />
                    </div>
                    <div class="share-embed-item">
                      <div class="share-embed-head">
                        <span class="share-embed-label">JS 浮窗脚本</span>
                        <el-button size="small" data-testid="ai-application-copy-sdk" @click="copyShareText(sdkCode, 'JS 脚本')">复制</el-button>
                      </div>
                      <el-input :model-value="sdkCode" readonly type="textarea" :rows="4" size="small" />
                    </div>
                  </div>
                  <div
                    v-else-if="applicationDetail.type === 'agent' && applicationDetail.published && !shareUrl"
                    class="share-embed-tip"
                  >
                    分享链接尚未生成，请重新发布一次或点击「立即发布」补生成。
                  </div>
                </div>
              </section>

              <section class="detail-section" data-testid="ai-application-access-panel">
                <div class="section-label">访问入口</div>
                <div class="section-body">
                  <div class="meta-row">
                    <span class="meta-label">详情入口</span>
                    <span class="meta-value access-path">{{ detailPath }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">管理入口</span>
                    <span class="meta-value access-path">{{ managementPath }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">调试入口</span>
                    <span class="meta-value access-path">{{ debugPath || '需要先发布后才能启用' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">访问说明</span>
                    <span class="meta-value">{{ accessHint }}</span>
                  </div>
                  <div class="access-entry-list" data-testid="ai-application-access-entry-list">
                    <div
                      v-for="item in accessEntryList"
                      :key="item.key"
                      class="access-entry-card"
                      :data-access-key="item.key"
                      data-testid="ai-application-access-item"
                    >
                      <div class="access-entry-main">
                        <div class="access-entry-title">{{ item.label }}</div>
                        <div class="access-entry-desc">{{ item.description }}</div>
                        <div class="access-entry-path">{{ item.path || '当前不可用' }}</div>
                      </div>
                      <div class="access-entry-actions">
                        <el-button
                          size="small"
                          :disabled="!item.available"
                          @click="openAccessPath(item.path)"
                        >
                          打开
                        </el-button>
                        <el-button
                          size="small"
                          :disabled="!item.available"
                          @click="copyAccessLink(item.path, item.label)"
                        >
                          复制
                        </el-button>
                      </div>
                    </div>
                  </div>
                  <div class="access-actions">
                    <el-button
                      type="primary"
                      data-testid="ai-application-access-manage-button"
                      @click="goToManagement"
                    >
                      打开管理页
                    </el-button>
                    <el-button
                      v-if="canDebugCurrentApplication"
                      data-testid="ai-application-access-debug-button"
                      @click="goToDebugChat"
                    >
                      打开调试页
                    </el-button>
                    <el-button
                      data-testid="ai-application-copy-management-link"
                      @click="copyAccessLink(managementPath, '管理入口')"
                    >
                      复制管理入口
                    </el-button>
                    <el-button
                      v-if="canDebugCurrentApplication"
                      data-testid="ai-application-copy-debug-link"
                      @click="copyAccessLink(debugPath, '调试入口')"
                    >
                      复制调试入口
                    </el-button>
                  </div>
                </div>
              </section>

              <section class="detail-section" data-testid="ai-application-log-panel">
                <div class="section-label">最近日志</div>
                <div class="section-body">
                  <template v-if="applicationType === 'workflow' && recentConversations.length > 0">
                    <div
                      v-for="conversation in recentConversations"
                      :key="conversation.conversationId"
                      class="log-item"
                      data-testid="ai-application-log-item"
                      @click="openConversationLogDrawer(conversation)"
                    >
                      <div class="log-main">
                        <div class="log-title">{{ conversation.title }}</div>
                        <div class="log-meta">
                          <span>消息数 {{ conversation.messageCount }}</span>
                          <span>{{ formatDateTime(conversation.updateTime || conversation.createTime) }}</span>
                        </div>
                      </div>
                      <div class="log-actions">
                        <el-button
                          link
                          type="primary"
                          data-testid="ai-application-open-log-link"
                          @click.stop="openConversationLog(conversation)"
                        >
                          查看对话
                        </el-button>
                      </div>
                    </div>
                  </template>
                  <template v-else-if="applicationType === 'workflow'">
                    <div class="meta-row">
                      <span class="meta-label">日志状态</span>
                      <span class="meta-value">当前工作流还没有最近的对话记录。</span>
                    </div>
                    <div class="meta-row">
                      <span class="meta-label">建议</span>
                      <span class="meta-value">可以先进入调试页跑一轮，再回到这里查看最近日志。</span>
                    </div>
                  </template>
                  <template v-else>
                    <div class="meta-row">
                      <span class="meta-label">日志状态</span>
                      <span class="meta-value">智能体日志目前还缺应用级会话关联字段。</span>
                    </div>
                    <div class="meta-row">
                      <span class="meta-label">处理原则</span>
                      <span class="meta-value">这轮先保持诚实占位，不伪造不存在的日志链路。</span>
                    </div>
                  </template>
                </div>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <el-drawer
      v-model="logDrawerVisible"
      title="运行日志详情"
      size="460px"
      append-to-body
      data-testid="ai-application-log-drawer"
    >
      <div v-if="selectedConversation" class="log-drawer-body" data-testid="ai-application-log-drawer-body">
        <div class="drawer-summary">
          <h3>{{ selectedConversation.title }}</h3>
          <p>
            当前先复用已有会话数据展示运行摘要，后续如果后端补齐执行节点、来源片段和耗时信息，
            这里可以继续扩成更完整的执行详情侧栏。
          </p>
        </div>

        <div class="drawer-metrics">
          <div class="drawer-metric">
            <span class="drawer-metric-label">会话 ID</span>
            <strong>{{ selectedConversation.conversationId }}</strong>
          </div>
          <div class="drawer-metric">
            <span class="drawer-metric-label">消息数</span>
            <strong>{{ selectedConversation.messageCount }}</strong>
          </div>
          <div class="drawer-metric">
            <span class="drawer-metric-label">创建时间</span>
            <strong>{{ formatDateTime(selectedConversation.createTime) }}</strong>
          </div>
          <div class="drawer-metric">
            <span class="drawer-metric-label">最近更新时间</span>
            <strong>{{ formatDateTime(selectedConversation.updateTime || selectedConversation.createTime) }}</strong>
          </div>
        </div>

        <div class="drawer-route-list">
          <div class="drawer-route-item">
            <span class="drawer-route-label">对话入口</span>
            <code>{{ conversationDetailPath(selectedConversation) }}</code>
          </div>
          <div class="drawer-route-item">
            <span class="drawer-route-label">应用调试入口</span>
            <code>{{ debugPath || '需要先发布后才能启用' }}</code>
          </div>
        </div>

        <div v-loading="logDetailLoading" class="drawer-side-panels">
          <section
            class="drawer-section"
            data-testid="ai-application-log-source-panel"
          >
            <div class="drawer-section-label">知识来源</div>
            <div class="drawer-section-copy">
              {{ sourcePanelTitle }}
            </div>
            <div class="drawer-tag-list">
              <el-tag
                v-for="item in knowledgeBaseNames"
                :key="item"
                size="small"
                effect="plain"
              >
                {{ item }}
              </el-tag>
              <span v-if="knowledgeBaseNames.length === 0" class="drawer-empty-text">当前没有知识库来源可展示</span>
            </div>
            <div
              class="source-card-list"
              data-testid="ai-application-log-source-card-list"
            >
              <article
                v-for="item in selectedKnowledgeBases"
                :key="String(item.kbId)"
                class="source-card"
                data-testid="ai-application-log-source-card"
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
              <div
                v-if="selectedKnowledgeBases.length === 0"
                class="source-card source-card-empty"
              >
                当前应用还没有绑定知识库，所以这里先保留知识来源卡片位，等绑定后直接展示文档规模和状态。
              </div>
            </div>
            <div class="drawer-note">
              当前消息接口尚未返回结构化引用片段、文档命中位置和来源得分，后续后端补齐后会优先接到这里。
            </div>
          </section>

          <section
            class="drawer-section"
            data-testid="ai-application-log-execution-panel"
          >
            <div class="drawer-section-label">执行信息</div>
            <div class="drawer-stat-list">
              <div
                v-for="item in executionSummaryItems"
                :key="item.label"
                class="drawer-stat-item"
              >
                <span class="drawer-stat-label">{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div
              class="drawer-stage-list"
              data-testid="ai-application-log-execution-stage-list"
            >
              <article
                v-for="item in executionStageItems"
                :key="item.label"
                class="drawer-stage-item"
                :class="`is-${item.state}`"
                data-testid="ai-application-log-execution-stage"
              >
                <div class="drawer-stage-main">
                  <span class="drawer-stage-label">{{ item.label }}</span>
                  <p>{{ item.detail }}</p>
                </div>
                <el-tag size="small" effect="plain" :type="item.tagType">
                  {{ item.stateLabel }}
                </el-tag>
              </article>
            </div>
            <div class="drawer-preview">
              <div class="drawer-preview-block">
                <span class="drawer-preview-label">最近问题</span>
                <p>{{ summarizeMessage(selectedLatestUserMessage?.content, 120) }}</p>
              </div>
              <div class="drawer-preview-block">
                <span class="drawer-preview-label">最近回复摘要</span>
                <p>{{ summarizeMessage(selectedLatestAssistantMessage?.content, 160) }}</p>
              </div>
            </div>
          </section>
        </div>

        <div class="drawer-actions">
          <el-button
            type="primary"
            data-testid="ai-application-log-drawer-open-button"
            @click="openConversationLog(selectedConversation)"
          >
            打开对话
          </el-button>
          <el-button
            data-testid="ai-application-log-drawer-copy-button"
            @click="copyConversationLink(selectedConversation)"
          >
            复制对话入口
          </el-button>
          <el-button
            v-if="canDebugCurrentApplication"
            data-testid="ai-application-log-drawer-debug-button"
            @click="goToDebugChat"
          >
            回到应用调试
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import {
  getAiAgent,
  getAiWorkflow,
  listAllKnowledgeBases,
  listAllMcpServers,
  listAllModels,
  listChatMessages,
  listConversations,
  kbTypeOptions,
  indexStatusOptions,
  publishAiAgent,
  publishAiWorkflow,
  resetAgentShareKey,
  type AiAgent,
  type AiChatMessage,
  type AiConversation,
  type AiModel,
  type AiWorkflow,
  type KnowledgeBase,
  type McpServer,
  unpublishAiAgent,
  unpublishAiWorkflow,
  workflowTypeOptions
} from '@/api/ai'
import { useUserStore } from '@/stores/user'

type ApplicationType = 'agent' | 'workflow'

interface ApplicationDetail {
  id: string | number
  type: ApplicationType
  name: string
  description?: string
  published: boolean
  /** 公开分享链接 key（agent 发布时生成） */
  shareKey?: string
  status?: string
  modelId?: string | number
  modelName?: string
  systemPrompt?: string
  prologue?: string
  createTime?: string
  workflowType?: string
  knowledgeBaseIds: string[]
  mcpServerIds: string[]
}

interface PublishReadinessItem {
  label: string
  hint: string
  ready: boolean
}

interface ExecutionStageItem {
  label: string
  detail: string
  state: 'ready' | 'pending' | 'inactive'
  stateLabel: string
  tagType: 'success' | 'warning' | 'info'
}

interface AccessEntryItem {
  key: string
  label: string
  description: string
  path: string
  available: boolean
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const publishLoading = ref(false)
const activeTab = ref<'overview' | 'settings' | 'debug'>('overview')
const applicationDetail = ref<ApplicationDetail | null>(null)
const modelList = ref<AiModel[]>([])
const knowledgeBaseList = ref<KnowledgeBase[]>([])
const mcpServerList = ref<McpServer[]>([])
const recentConversations = ref<AiConversation[]>([])
const logDrawerVisible = ref(false)
const selectedConversation = ref<AiConversation | null>(null)
const logDetailLoading = ref(false)
const selectedConversationMessages = ref<AiChatMessage[]>([])

const applicationType = computed<ApplicationType>(() => {
  return route.params.type === 'workflow' ? 'workflow' : 'agent'
})

const applicationTypeLabel = computed(() => {
  return applicationType.value === 'agent' ? '简单应用' : '高级应用'
})

const publishStatusLabel = computed(() => {
  return applicationDetail.value?.published ? '已发布，可进入调试和交付链路' : '未发布，当前以配置和管理为主'
})

const publishHint = computed(() => {
  return applicationDetail.value?.published
    ? '当前应用已经开放调试入口，后续可以继续衔接用户侧访问和交付。'
    : '建议先完成配置校验与调试，再切换为发布状态。'
})

const statusLabel = computed(() => {
  const status = applicationDetail.value?.status
  if (status === '0') {
    return '启用中'
  }
  if (status === '1') {
    return '停用中'
  }
  return status || '未知'
})

const workflowTypeLabel = computed(() => {
  if (applicationType.value !== 'workflow') {
    return '不适用'
  }
  const value = applicationDetail.value?.workflowType
  return workflowTypeOptions.find((item) => item.value === value)?.label || '未配置'
})

const canManageCurrentType = computed(() => {
  return applicationType.value === 'agent'
    ? userStore.hasPermission('ai:agent:list')
    : userStore.hasPermission('ai:workflow:list')
})

const canDebugCurrentApplication = computed(() => {
  return Boolean(applicationDetail.value?.published)
})

const detailPath = computed(() => {
  if (!applicationDetail.value) {
    return ''
  }
  return `/ai/application/${applicationType.value}/${applicationDetail.value.id}`
})

const managementPath = computed(() => {
  return router.resolve(getManagementRoute()).fullPath
})

const debugPath = computed(() => {
  const target = getDebugRoute()
  return target ? router.resolve(target).fullPath : ''
})

const accessHint = computed(() => {
  return canDebugCurrentApplication.value
    ? '当前详情页、管理页和调试页已经连成一条工作路径，可以直接切换使用。'
    : '当前还未发布，先通过管理页完善配置，发布后再开放调试入口。'
})

const publishReadiness = computed<PublishReadinessItem[]>(() => {
  const detail = applicationDetail.value
  return [
    {
      label: '模型配置',
      hint: detail?.modelName ? `当前模型为 ${detail.modelName}` : '还没有绑定模型，建议先补齐模型配置',
      ready: Boolean(detail?.modelId)
    },
    {
      label: '系统提示词',
      hint: detail?.systemPrompt ? '已配置系统提示词，可直接用于调试' : '当前还未配置系统提示词',
      ready: Boolean(detail?.systemPrompt)
    },
    {
      label: '交付入口',
      hint: canDebugCurrentApplication.value ? '当前已开放调试入口，可继续做交付和验证' : '需要先发布后才能开放调试入口',
      ready: canDebugCurrentApplication.value
    },
    {
      label: '知识增强',
      hint: knowledgeBaseNames.value.length > 0 ? `已绑定 ${knowledgeBaseNames.value.length} 个知识库` : '当前未绑定知识库，可按场景选配',
      ready: knowledgeBaseNames.value.length > 0
    }
  ]
})

const accessEntryList = computed<AccessEntryItem[]>(() => {
  return [
    {
      key: 'detail',
      label: '详情入口',
      description: '查看应用概览、配置、日志和交付信息',
      path: detailPath.value,
      available: Boolean(detailPath.value)
    },
    {
      key: 'management',
      label: '管理入口',
      description: '继续编辑智能体或工作流的核心配置',
      path: managementPath.value,
      available: Boolean(managementPath.value)
    },
    {
      key: 'debug',
      label: '调试入口',
      description: '进入应用的真实调试链路和对话工作区',
      path: debugPath.value,
      available: Boolean(debugPath.value)
    }
  ]
})

const selectedLatestAssistantMessage = computed(() => {
  const assistantMessages = selectedConversationMessages.value.filter((item) => item.role === 'assistant')
  return assistantMessages[assistantMessages.length - 1]
})

const selectedLatestUserMessage = computed(() => {
  const userMessages = selectedConversationMessages.value.filter((item) => item.role === 'user')
  return userMessages[userMessages.length - 1]
})

const sourcePanelTitle = computed(() => {
  if (knowledgeBaseNames.value.length > 0) {
    return '当前应用已绑定知识能力，但本次会话还没有返回结构化引用片段。'
  }
  return '当前应用没有绑定知识库，本次会话也没有结构化来源信息。'
})

const executionSummaryItems = computed(() => {
  const latestAssistant = selectedLatestAssistantMessage.value
  return [
    {
      label: '最新回复消息 ID',
      value: latestAssistant?.messageId ? String(latestAssistant.messageId) : '暂无'
    },
    {
      label: 'Token 消耗',
      value: latestAssistant?.tokenCount ? String(latestAssistant.tokenCount) : '暂无'
    },
    {
      label: '回复时间',
      value: formatDateTime(latestAssistant?.createTime)
    },
    {
      label: '回复长度',
      value: latestAssistant?.content ? `${latestAssistant.content.length} 字符` : '暂无'
    }
  ]
})

const executionStageItems = computed<ExecutionStageItem[]>(() => {
  const hasMessages = selectedConversationMessages.value.length > 0
  const latestAssistant = selectedLatestAssistantMessage.value
  const selectedConversationTitle = selectedConversation.value?.title || '未命名会话'
  return [
    {
      label: '会话定位',
      detail: selectedConversation.value
        ? `${selectedConversationTitle}，会话 ID ${selectedConversation.value.conversationId}`
        : '当前还没有选中的运行日志。',
      state: selectedConversation.value ? 'ready' : 'inactive',
      stateLabel: selectedConversation.value ? '已定位' : '未载入',
      tagType: selectedConversation.value ? 'success' : 'info'
    },
    {
      label: '消息明细',
      detail: hasMessages
        ? `已加载 ${selectedConversationMessages.value.length} 条消息，可继续还原问答过程。`
        : logDetailLoading.value
          ? '正在加载会话消息，请稍候。'
          : '当前只保留基础摘要，后续可继续补细节。',
      state: hasMessages ? 'ready' : (logDetailLoading.value ? 'pending' : 'inactive'),
      stateLabel: hasMessages ? '已拉取' : (logDetailLoading.value ? '加载中' : '待补充'),
      tagType: hasMessages ? 'success' : (logDetailLoading.value ? 'warning' : 'info')
    },
    {
      label: '知识增强',
      detail: knowledgeBaseNames.value.length > 0
        ? `已绑定 ${knowledgeBaseNames.value.length} 个知识库，等待后端补回命中片段与来源得分。`
        : '当前应用没有绑定知识库，本次不会展示知识命中。',
      state: knowledgeBaseNames.value.length > 0 ? 'pending' : 'inactive',
      stateLabel: knowledgeBaseNames.value.length > 0 ? '待引用' : '未启用',
      tagType: knowledgeBaseNames.value.length > 0 ? 'warning' : 'info'
    },
    {
      label: '工具执行',
      detail: mcpServerNames.value.length > 0
        ? `已绑定 ${mcpServerNames.value.length} 个 MCP 服务，等待后端补执行轨迹。`
        : '当前应用没有绑定 MCP 服务，本次不会展示工具轨迹。',
      state: mcpServerNames.value.length > 0 ? 'pending' : 'inactive',
      stateLabel: mcpServerNames.value.length > 0 ? '待轨迹' : '未启用',
      tagType: mcpServerNames.value.length > 0 ? 'warning' : 'info'
    },
    {
      label: '模型回复',
      detail: latestAssistant?.content
        ? `当前会话已生成回复摘要，可继续跳转到对话页查看完整上下文。`
        : '当前会话还没有可展示的助手回复。',
      state: latestAssistant?.content ? 'ready' : 'inactive',
      stateLabel: latestAssistant?.content ? '已生成' : '待生成',
      tagType: latestAssistant?.content ? 'success' : 'info'
    }
  ]
})

const knowledgeBaseNames = computed(() => {
  const map = new Map(knowledgeBaseList.value.map((item) => [String(item.kbId), item.kbName]))
  return (applicationDetail.value?.knowledgeBaseIds || []).map((item) => {
    return map.get(String(item)) || String(item)
  })
})

const selectedKnowledgeBases = computed(() => {
  const targetIds = new Set((applicationDetail.value?.knowledgeBaseIds || []).map((item) => String(item)))
  return knowledgeBaseList.value.filter((item) => targetIds.has(String(item.kbId)))
})

const mcpServerNames = computed(() => {
  const map = new Map(mcpServerList.value.map((item) => [String(item.mcpId), item.serverName]))
  return (applicationDetail.value?.mcpServerIds || []).map((item) => {
    return map.get(String(item)) || String(item)
  })
})

function parseJsonArray(value?: string) {
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

function getManagementRoute(): RouteLocationRaw {
  return applicationType.value === 'agent' ? '/ai/agent' : '/ai/workflow'
}

function getDebugRoute(): RouteLocationRaw | null {
  if (!applicationDetail.value || !canDebugCurrentApplication.value) {
    return null
  }
  if (applicationType.value === 'agent') {
    return {
      path: '/ai/agent',
      query: {
        action: 'chat',
        agentId: String(applicationDetail.value.id)
      }
    }
  }
  return {
    path: '/ai/workflow',
    query: {
      action: 'chat',
      workflowId: String(applicationDetail.value.id)
    }
  }
}

function goToManagement() {
  router.push(getManagementRoute())
}

function goToDebugChat() {
  const target = getDebugRoute()
  if (!target) {
    return
  }
  router.push(target)
}

function goToWorkflowDesigner() {
  if (applicationType.value !== 'workflow' || !applicationDetail.value) {
    return
  }
  router.push(`/ai/workflow/designer/${applicationDetail.value.id}`)
}

async function togglePublish() {
  if (!applicationDetail.value) {
    return
  }
  publishLoading.value = true
  try {
    if (applicationType.value === 'agent') {
      if (applicationDetail.value.published) {
        await unpublishAiAgent(applicationDetail.value.id)
        applicationDetail.value.published = false
        ElMessage.success('已取消发布')
      } else {
        await publishAiAgent(applicationDetail.value.id)
        applicationDetail.value.published = true
        ElMessage.success('已发布')
        // 发布会补生成分享 key，重载详情拿最新 shareKey
        await loadDetail()
      }
    } else if (applicationDetail.value.published) {
      await unpublishAiWorkflow(applicationDetail.value.id)
      applicationDetail.value.published = false
      ElMessage.success('已取消发布')
    } else {
      await publishAiWorkflow(applicationDetail.value.id)
      applicationDetail.value.published = true
      ElMessage.success('已发布')
    }
  } finally {
    publishLoading.value = false
  }
}

async function copyAccessLink(path: string, label: string) {
  if (!path) {
    ElMessage.warning(`${label}当前不可用`)
    return
  }
  const absolutePath = typeof window !== 'undefined'
    ? `${window.location.origin}${path}`
    : path
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(absolutePath)
    ElMessage.success(`${label}已复制`)
    return
  }
  ElMessage.info(absolutePath)
}

// ==================== 公开访问三件套（链接 / iframe / JS 浮窗脚本） ====================
const shareUrl = computed(() => {
  const shareKey = applicationDetail.value?.shareKey
  if (!shareKey || typeof window === 'undefined') {
    return ''
  }
  return `${window.location.origin}/chat/share/${shareKey}`
})

const iframeCode = computed(() => {
  if (!shareUrl.value) {
    return ''
  }
  return `<iframe src="${shareUrl.value}" style="width: 420px; height: 640px; border: none; border-radius: 12px;" allow="clipboard-write"></iframe>`
})

const sdkCode = computed(() => {
  if (!shareUrl.value) {
    return ''
  }
  return [
    '<script>',
    '(function () {',
    `  var btn = document.createElement('div');`,
    `  btn.innerHTML = '💬';`,
    `  btn.style.cssText = 'position:fixed;right:24px;bottom:24px;width:52px;height:52px;border-radius:50%;background:#409eff;color:#fff;font-size:24px;display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,.18);z-index:99998;';`,
    `  var frame = document.createElement('iframe');`,
    `  frame.src = '${shareUrl.value}';`,
    `  frame.style.cssText = 'position:fixed;right:24px;bottom:88px;width:400px;height:600px;border:none;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,.2);display:none;z-index:99999;';`,
    `  btn.onclick = function () { frame.style.display = frame.style.display === 'none' ? 'block' : 'none'; };`,
    '  document.body.appendChild(btn);',
    '  document.body.appendChild(frame);',
    '})();',
    '<\/script>'
  ].join('\n')
})

async function copyShareText(text: string, label: string) {
  if (!text) {
    ElMessage.warning(`${label}当前不可用`)
    return
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
    return
  }
  ElMessage.info(text)
}

async function handleResetShareKey() {
  if (!applicationDetail.value) return
  try {
    await ElMessageBox.confirm('重置后旧分享链接立即失效，已嵌入第三方页面的地址需要同步更新。确定重置吗?', '提示', { type: 'warning' })
  } catch {
    return
  }
  const res = await resetAgentShareKey(applicationDetail.value.id)
  const newKey = (res as any).data as string
  if (newKey && applicationDetail.value) {
    applicationDetail.value.shareKey = newKey
  }
  ElMessage.success('分享链接已重置')
}

function openAccessPath(path: string) {
  if (!path) {
    ElMessage.warning('当前入口不可用')
    return
  }
  router.push(path)
}

function conversationDetailPath(conversation: AiConversation) {
  return `/ai/chat?conversationId=${conversation.conversationId}`
}

async function openConversationLogDrawer(conversation: AiConversation) {
  selectedConversation.value = conversation
  logDrawerVisible.value = true
  logDetailLoading.value = true
  selectedConversationMessages.value = []
  try {
    const response = await listChatMessages(conversation.conversationId)
    selectedConversationMessages.value = response.data || []
  } catch {
    selectedConversationMessages.value = []
    ElMessage.warning('当前会话详情加载失败，已保留基础摘要信息')
  } finally {
    logDetailLoading.value = false
  }
}

function openConversationLog(conversation: AiConversation) {
  logDrawerVisible.value = false
  router.push({
    path: '/ai/chat',
    query: {
      conversationId: String(conversation.conversationId)
    }
  })
}

async function copyConversationLink(conversation: AiConversation) {
  await copyAccessLink(conversationDetailPath(conversation), '对话入口')
}

function summarizeMessage(content?: string, limit = 120) {
  if (!content) {
    return '暂无'
  }
  const normalized = content.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '暂无'
  }
  return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized
}

async function loadRecentConversations(type: ApplicationType, id: string) {
  if (type !== 'workflow') {
    recentConversations.value = []
    return
  }
  const response = await listConversations({ pageNum: 1, pageSize: 5, workflowId: id })
  recentConversations.value = response.data?.rows || []
}

async function loadDetail() {
  const id = route.params.id
  const type = route.params.type
  if (!id || (type !== 'agent' && type !== 'workflow')) {
    router.replace('/ai/application')
    return
  }

  if (!canManageCurrentType.value) {
    ElMessage.warning('当前账号没有查看该应用的权限')
    router.replace('/ai/application')
    return
  }

  loading.value = true
  try {
    const [modelRes, kbRes, mcpRes, detailRes] = await Promise.all([
      listAllModels('LLM'),
      listAllKnowledgeBases(),
      listAllMcpServers(),
      type === 'agent' ? getAiAgent(String(id)) : getAiWorkflow(String(id))
    ])

    modelList.value = modelRes.data || []
    knowledgeBaseList.value = kbRes.data || []
    mcpServerList.value = mcpRes.data || []

    const modelMap = new Map(modelList.value.map((item) => [String(item.modelId), item.modelName]))

    if (type === 'agent') {
      const detail = detailRes.data as AiAgent
      applicationDetail.value = {
        id: detail.agentId,
        type,
        name: detail.agentName,
        description: detail.description,
        published: Boolean(detail.published),
        shareKey: detail.shareKey,
        status: detail.status,
        modelId: detail.modelId,
        modelName: detail.modelId ? modelMap.get(String(detail.modelId)) : undefined,
        systemPrompt: detail.systemPrompt,
        prologue: detail.prologue || detail.welcomeMessage,
        createTime: detail.createTime,
        knowledgeBaseIds: parseJsonArray(detail.knowledgeBaseIds),
        mcpServerIds: parseJsonArray(detail.mcpServerIds)
      }
    } else {
      const detail = detailRes.data as AiWorkflow
      applicationDetail.value = {
        id: detail.workflowId,
        type,
        name: detail.workflowName,
        description: detail.description,
        published: detail.published === '1',
        status: detail.status,
        modelId: detail.modelId,
        modelName: detail.modelId ? modelMap.get(String(detail.modelId)) : undefined,
        systemPrompt: detail.systemPrompt,
        prologue: detail.prologue,
        createTime: detail.createTime,
        workflowType: detail.workflowType,
        knowledgeBaseIds: parseJsonArray(detail.knowledgeBaseIds),
        mcpServerIds: parseJsonArray(detail.mcpServerIds)
      }
    }

    await loadRecentConversations(type, String(id))
  } catch (_error) {
    applicationDetail.value = null
    recentConversations.value = []
    ElMessage.error('加载应用详情失败，请返回列表后重试')
  } finally {
    loading.value = false
  }
}

watch(
  () => [route.params.type, route.params.id],
  async () => {
    activeTab.value = 'overview'
    await loadDetail()
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.ai-application-detail-page {
  min-height: 100%;
}

.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(260px, 0.7fr);
  gap: 20px;
  padding: 24px 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.14), transparent 30%),
    linear-gradient(135deg, #f8fbff 0%, #f2f7ff 100%);
  border: 1px solid rgba(191, 219, 254, 0.8);
}

.back-link {
  padding: 0;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.detail-hero-main h2 {
  margin: 14px 0 10px;
  font-size: 30px;
  line-height: 1.18;
  color: #0f172a;
}

.detail-description {
  margin: 0;
  max-width: 760px;
  color: #475569;
  line-height: 1.8;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.detail-hero-side {
  display: flex;
}

.signal-panel {
  display: grid;
  width: 100%;
  gap: 12px;
  align-content: start;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.8);
}

.signal-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.signal-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.signal-label {
  color: #64748b;
  font-size: 12px;
}

.signal-item strong {
  color: #0f172a;
  font-size: 16px;
}

.detail-tabs {
  padding: 0 6px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 16px;
  align-items: start;
}

.overview-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section,
.settings-panel,
.debug-panel {
  padding: 18px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  background: #fff;
}

.section-label {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #475569;
}

.section-body {
  color: #0f172a;
  line-height: 1.8;
}

.prompt-block {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  color: #0f172a;
  font-family: inherit;
  line-height: 1.8;
  white-space: pre-wrap;
}

.resource-side {
  display: flex;
}

.resource-group + .resource-group {
  margin-top: 16px;
}

.resource-title {
  display: block;
  margin-bottom: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-empty {
  color: #94a3b8;
  font-size: 13px;
}

.settings-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #eef2f7;
}

.settings-row:first-child {
  padding-top: 0;
}

.settings-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.settings-label {
  color: #64748b;
  font-size: 13px;
}

.settings-value {
  color: #0f172a;
  line-height: 1.75;
}

.debug-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.debug-grid {
  margin-top: 16px;
}

.debug-copy h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 18px;
}

.debug-copy p {
  margin: 0;
  color: #475569;
  line-height: 1.8;
}

.debug-actions,
.access-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.access-actions {
  margin-top: 14px;
}

.share-embed-block {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  background: #f7f9fc;
  border-radius: 12px;

  .share-embed-item {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .share-embed-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .share-embed-label {
    font-size: 13px;
    font-weight: 600;
    color: #606266;
  }

  :deep(textarea) {
    font-family: monospace;
    font-size: 12px;
  }
}

.share-embed-tip {
  margin-top: 14px;
  font-size: 12px;
  color: #909399;
}

.publish-checklist {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.check-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f8fafc;
}

.check-item.is-ready {
  border-color: rgba(34, 197, 94, 0.28);
  background: rgba(240, 253, 244, 0.9);
}

.check-item-main {
  min-width: 0;
}

.check-item-label {
  display: block;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.check-item-hint {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.check-item strong {
  flex-shrink: 0;
  color: #0f172a;
  font-size: 13px;
}

.meta-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.meta-row + .meta-row {
  margin-top: 10px;
}

.meta-label {
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

.meta-value {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.7;
  text-align: right;
}

.access-path {
  word-break: break-all;
}

.access-entry-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.access-entry-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #f8fafc;
}

.access-entry-main {
  min-width: 0;
}

.access-entry-title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.access-entry-desc {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.access-entry-path {
  margin-top: 8px;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.7;
  word-break: break-all;
}

.access-entry-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.log-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.log-item + .log-item {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #eef2f7;
}

.log-main {
  min-width: 0;
}

.log-title {
  color: #0f172a;
  font-weight: 600;
  line-height: 1.6;
}

.log-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.log-actions {
  flex-shrink: 0;
}

.log-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.drawer-summary {
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 100%);
  border: 1px solid rgba(191, 219, 254, 0.9);
}

.drawer-summary h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 20px;
}

.drawer-summary p {
  margin: 0;
  color: #475569;
  line-height: 1.75;
}

.drawer-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.drawer-metric {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.drawer-metric-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
}

.drawer-metric strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-all;
}

.drawer-route-list {
  display: grid;
  gap: 12px;
}

.drawer-route-item {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.drawer-route-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
}

.drawer-route-item code {
  display: block;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.7;
  word-break: break-all;
  white-space: pre-wrap;
}

.drawer-side-panels {
  display: grid;
  gap: 16px;
}

.drawer-section {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.drawer-section-label {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.drawer-section-copy {
  color: #475569;
  font-size: 13px;
  line-height: 1.75;
}

.drawer-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.drawer-empty-text {
  color: #94a3b8;
  font-size: 12px;
}

.drawer-note {
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.source-card-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.source-card {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.source-card-empty {
  color: #64748b;
  font-size: 12px;
  line-height: 1.75;
}

.source-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.source-card-header strong {
  color: #0f172a;
  font-size: 13px;
}

.source-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
}

.drawer-stat-list {
  display: grid;
  gap: 10px;
}

.drawer-stat-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.drawer-stat-label {
  color: #64748b;
  font-size: 12px;
}

.drawer-stat-item strong {
  color: #0f172a;
  font-size: 13px;
  text-align: right;
}

.drawer-preview {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.drawer-stage-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.drawer-stage-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.drawer-stage-item.is-ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.drawer-stage-item.is-pending {
  border-color: #fde68a;
  background: #fffbeb;
}

.drawer-stage-main {
  display: grid;
  gap: 6px;
}

.drawer-stage-label {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.drawer-stage-main p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.drawer-preview-block {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.drawer-preview-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
}

.drawer-preview-block p {
  margin: 0;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.75;
  word-break: break-word;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 1080px) {
  .detail-hero,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .detail-hero {
    padding: 20px;
  }

  .detail-hero-main h2 {
    font-size: 24px;
  }

  .settings-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .meta-row,
  .log-item {
    flex-direction: column;
  }

  .access-entry-card {
    flex-direction: column;
  }

  .access-entry-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .drawer-metrics {
    grid-template-columns: 1fr;
  }

  .debug-panel {
    flex-direction: column;
  }
}
</style>
