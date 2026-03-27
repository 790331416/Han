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
              {{ applicationDetail.description || '当前应用还没有补充说明，可先进入设置页完善业务定位。' }}
            </p>
            <div class="detail-actions">
              <el-button type="primary" @click="goToManagement">进入管理</el-button>
              <el-button v-if="canDebugCurrentApplication" @click="goToDebugChat">调试对话</el-button>
              <el-button v-if="applicationType === 'workflow'" @click="goToWorkflowDesigner">
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
                <span class="signal-label">创建时间</span>
                <strong>{{ formatDate(applicationDetail.createTime) }}</strong>
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
                    {{ applicationDetail.description || '暂无描述，可在管理页中继续补充业务说明。' }}
                  </div>
                </section>

                <section class="detail-section">
                  <div class="section-label">角色与系统提示词</div>
                  <pre class="prompt-block">{{ applicationDetail.systemPrompt || '当前未配置系统提示词。' }}</pre>
                </section>

                <section class="detail-section">
                  <div class="section-label">{{ applicationType === 'agent' ? '欢迎语' : '开场白' }}</div>
                  <div class="section-body">
                    {{ applicationDetail.prologue || '当前未配置开场内容。' }}
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
                <span class="settings-label">状态</span>
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
                <span class="settings-label">配置建议</span>
                <span class="settings-value">
                  当前为第二版详情视图，后续会继续向“应用概览、设置、调试、发布、日志”闭环深化。
                </span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="调试" name="debug">
            <div class="debug-panel" data-testid="ai-application-debug-panel">
              <div class="debug-copy">
                <h3>调试入口</h3>
                <p>
                  当前继续复用原有智能体页、工作流页和流程设计页，不新造一套平行逻辑。
                  这样先把应用主线收顺，再逐步收口统一调试、发布和日志工作台。
                </p>
              </div>
              <div class="debug-actions">
                <el-button type="primary" @click="goToManagement">进入管理页</el-button>
                <el-button v-if="canDebugCurrentApplication" @click="goToDebugChat">
                  打开对话调试
                </el-button>
                <el-button v-if="applicationType === 'workflow'" @click="goToWorkflowDesigner">
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
                    <span class="meta-value">{{ applicationDetail.published ? '已发布' : '未发布' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">最近更新</span>
                    <span class="meta-value">{{ formatDateTime(applicationDetail.createTime) }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">说明</span>
                    <span class="meta-value">这里直接复用现有发布能力，不重写原有业务逻辑。</span>
                  </div>
                  <div class="access-actions">
                    <el-button :loading="publishLoading" type="primary" @click="togglePublish">
                      {{ applicationDetail.published ? '取消发布' : '立即发布' }}
                    </el-button>
                  </div>
                </div>
              </section>

              <section class="detail-section" data-testid="ai-application-access-panel">
                <div class="section-label">访问入口</div>
                <div class="section-body">
                  <div class="meta-row">
                    <span class="meta-label">调试入口</span>
                    <span class="meta-value">{{ canDebugCurrentApplication ? '可用' : '需先发布' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">访问路径</span>
                    <span class="meta-value access-path">{{ accessPath || '暂无' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">访问提示</span>
                    <span class="meta-value">当前先通过管理页和对话调试进入应用。</span>
                  </div>
                  <div class="access-actions">
                    <el-button type="primary" @click="goToManagement">进入管理</el-button>
                    <el-button v-if="canDebugCurrentApplication" @click="goToDebugChat">
                      打开对话
                    </el-button>
                    <el-button @click="copyAccessLink">复制入口</el-button>
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
                    >
                      <div class="log-title">{{ conversation.title }}</div>
                      <div class="log-meta">
                        <span>消息数 {{ conversation.messageCount }}</span>
                        <span>{{ formatDateTime(conversation.updateTime || conversation.createTime) }}</span>
                      </div>
                    </div>
                  </template>
                  <template v-else-if="applicationType === 'workflow'">
                    <div class="meta-row">
                      <span class="meta-label">日志状态</span>
                      <span class="meta-value">当前工作流还没有最近对话记录。</span>
                    </div>
                  </template>
                  <template v-else>
                    <div class="meta-row">
                      <span class="meta-label">日志状态</span>
                      <span class="meta-value">智能体日志当前仍需补应用级关联字段。</span>
                    </div>
                    <div class="meta-row">
                      <span class="meta-label">说明</span>
                      <span class="meta-value">这轮先不伪造日志，避免把后续真实链路做偏。</span>
                    </div>
                  </template>
                </div>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  getAiAgent,
  getAiWorkflow,
  listAllKnowledgeBases,
  listAllMcpServers,
  listAllModels,
  listConversations,
  publishAiAgent,
  publishAiWorkflow,
  type AiAgent,
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

const applicationType = computed<ApplicationType>(() => {
  return route.params.type === 'workflow' ? 'workflow' : 'agent'
})

const applicationTypeLabel = computed(() => {
  return applicationType.value === 'agent' ? '简单应用' : '高级应用'
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

const accessPath = computed(() => {
  if (!applicationDetail.value) {
    return ''
  }
  if (applicationType.value === 'agent') {
    return `/ai/agent?action=chat&agentId=${applicationDetail.value.id}`
  }
  return `/ai/workflow?action=chat&workflowId=${applicationDetail.value.id}`
})

const knowledgeBaseNames = computed(() => {
  const map = new Map(knowledgeBaseList.value.map((item) => [String(item.kbId), item.kbName]))
  return (applicationDetail.value?.knowledgeBaseIds || []).map((item) => map.get(String(item)) || String(item))
})

const mcpServerNames = computed(() => {
  const map = new Map(mcpServerList.value.map((item) => [String(item.mcpId), item.serverName]))
  return (applicationDetail.value?.mcpServerIds || []).map((item) => map.get(String(item)) || String(item))
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

function formatDate(value?: string) {
  if (!value) {
    return '暂无'
  }
  return value.length >= 10 ? value.slice(0, 10) : value
}

function formatDateTime(value?: string) {
  if (!value) {
    return '暂无'
  }
  return value.length >= 16 ? value.slice(0, 16) : value
}

function goToManagement() {
  router.push(applicationType.value === 'agent' ? '/ai/agent' : '/ai/workflow')
}

function goToDebugChat() {
  if (!applicationDetail.value) {
    return
  }
  if (applicationType.value === 'agent') {
    router.push({
      path: '/ai/agent',
      query: { action: 'chat', agentId: String(applicationDetail.value.id) }
    })
    return
  }
  router.push({
    path: '/ai/workflow',
    query: { action: 'chat', workflowId: String(applicationDetail.value.id) }
  })
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

async function copyAccessLink() {
  if (!accessPath.value) {
    return
  }
  const absolutePath = typeof window !== 'undefined'
    ? `${window.location.origin}${accessPath.value}`
    : accessPath.value
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(absolutePath)
    ElMessage.success('访问入口已复制')
    return
  }
  ElMessage.info(absolutePath)
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

.log-item + .log-item {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #eef2f7;
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

  .settings-row,
  .meta-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .debug-panel {
    flex-direction: column;
  }
}
</style>
