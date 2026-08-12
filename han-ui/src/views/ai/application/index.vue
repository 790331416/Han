<template>
  <div class="app-container ai-application-page" data-testid="ai-application-page">
    <el-card shadow="never" class="hero-card">
      <div class="hero-content">
        <div class="hero-copy">
          <div class="hero-badge">AI 应用工作台</div>
          <h2>先做应用，再装配模型、知识库与工具</h2>
          <p>
            这一层先把智能体和工作流收拢成统一入口。原有管理页、设计页、调试页全部保留，
            这里只负责把主流程理顺，让应用创建、查看、调试和交付更顺手。
          </p>
          <div class="hero-actions">
            <el-button
              type="primary"
              data-testid="ai-application-create-agent"
              @click="goToAgentCreate"
            >
              创建简单应用
            </el-button>
            <el-button
              data-testid="ai-application-create-workflow"
              @click="goToWorkflowCreate"
            >
              创建高级应用
            </el-button>
            <el-button data-testid="ai-application-open-chat" @click="router.push('/ai/chat')">
              打开 AI 对话
            </el-button>
          </div>
        </div>
        <div class="hero-side">
          <div class="hero-panel">
            <div class="hero-panel-title">能力装配件</div>
            <div class="hero-panel-items">
              <div class="hero-panel-item">
                <el-icon><Cpu /></el-icon>
                <span>模型</span>
              </div>
              <div class="hero-panel-item">
                <el-icon><Collection /></el-icon>
                <span>知识库</span>
              </div>
              <div class="hero-panel-item">
                <el-icon><Link /></el-icon>
                <span>MCP 工具</span>
              </div>
              <div class="hero-panel-item">
                <el-icon><Document /></el-icon>
                <span>Prompt</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="summary-card" data-testid="ai-application-stat-total">
          <div class="summary-label">应用总数</div>
          <div class="summary-value">{{ applicationList.length }}</div>
          <div class="summary-meta">智能体 + 工作流统一视角</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">简单应用</div>
          <div class="summary-value">{{ agentList.length }}</div>
          <div class="summary-meta">适合快速搭建助手</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">高级应用</div>
          <div class="summary-value">{{ workflowList.length }}</div>
          <div class="summary-meta">适合复杂编排场景</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">已发布</div>
          <div class="summary-value">{{ publishedCount }}</div>
          <div class="summary-meta">可直接进入调试与交付</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="resource-card">
      <div class="resource-header">
        <div>
          <h3>应用依赖资源</h3>
          <p>这些配置页继续保留，当前作为应用装配的能力来源使用。</p>
        </div>
        <div class="resource-actions">
          <el-button
            v-if="userStore.hasPermission('ai:model:list')"
            @click="router.push('/ai/model')"
          >
            模型管理
          </el-button>
          <el-button
            v-if="userStore.hasPermission('ai:kb:list')"
            @click="router.push('/ai/knowledge')"
          >
            知识库
          </el-button>
          <el-button
            v-if="userStore.hasPermission('ai:mcp:list')"
            @click="router.push('/ai/mcp')"
          >
            MCP 管理
          </el-button>
          <el-button
            v-if="userStore.hasPermission('ai:prompt:list')"
            @click="router.push('/ai/prompt')"
          >
            Prompt 模板
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="application-list-card">
      <template #header>
        <div class="list-header">
          <div>
            <span class="list-title">AI 应用列表</span>
            <div class="list-subtitle">用统一卡片视角查看智能体和工作流</div>
          </div>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          data-testid="ai-application-search-input"
          placeholder="搜索应用名称或描述"
          clearable
        />
        <el-radio-group v-model="activeType" data-testid="ai-application-type-switch">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="agent">简单应用</el-radio-button>
          <el-radio-button label="workflow">高级应用</el-radio-button>
        </el-radio-group>
      </div>

      <el-alert
        v-if="truncatedNotice"
        type="warning"
        :title="truncatedNotice"
        :closable="false"
        show-icon
        style="margin-bottom: 12px;"
        data-testid="ai-application-truncated-notice"
      />

      <div v-loading="loading" data-testid="ai-application-list">
        <el-row v-if="filteredApplications.length > 0" :gutter="16">
          <el-col
            v-for="item in filteredApplications"
            :key="`${item.type}-${item.id}`"
            :xs="24"
            :sm="12"
            :xl="8"
            class="application-col"
          >
            <el-card
              shadow="hover"
              class="application-card"
              data-testid="ai-application-card"
              :data-application-type="item.type"
              :data-application-id="String(item.id)"
            >
              <div class="application-card-header">
                <div class="application-card-icon" :class="`is-${item.type}`">
                  <el-icon v-if="item.type === 'agent'"><UserFilled /></el-icon>
                  <el-icon v-else><Connection /></el-icon>
                </div>
                <div class="application-card-tags">
                  <el-tag size="small" :type="item.type === 'agent' ? 'primary' : 'warning'">
                    {{ item.type === 'agent' ? '简单应用' : '高级应用' }}
                  </el-tag>
                  <el-tag size="small" :type="item.published ? 'success' : 'info'">
                    {{ item.published ? '已发布' : '未发布' }}
                  </el-tag>
                </div>
              </div>

              <div class="application-card-name">{{ item.name }}</div>
              <div class="application-card-desc">{{ item.description || '暂无描述' }}</div>

              <div class="application-card-meta">
                <div class="meta-row">
                  <span class="meta-label">关联模型</span>
                  <span class="meta-value">{{ item.modelName || '未选择模型' }}</span>
                </div>
                <div class="meta-row">
                  <span class="meta-label">更新时间</span>
                  <span class="meta-value">{{ formatDate(item.createTime) }}</span>
                </div>
              </div>

              <div class="application-card-actions">
                <el-button
                  link
                  type="primary"
                  data-testid="ai-application-detail-link"
                  @click="goToDetail(item)"
                >
                  查看详情
                </el-button>
                <el-button
                  v-if="item.type === 'agent'"
                  link
                  type="primary"
                  @click="router.push('/ai/agent')"
                >
                  进入管理
                </el-button>
                <el-button
                  v-if="item.type === 'workflow'"
                  link
                  type="primary"
                  @click="router.push('/ai/workflow')"
                >
                  进入管理
                </el-button>
                <el-button
                  v-if="item.type === 'workflow'"
                  link
                  @click="goToWorkflowDesigner(item.id)"
                >
                  流程设计
                </el-button>
                <el-button
                  v-if="item.type === 'agent' && item.published"
                  link
                  @click="goToAgentChat(item.id)"
                >
                  调试对话
                </el-button>
                <el-button
                  v-if="item.type === 'workflow' && item.published"
                  link
                  @click="goToWorkflowChat(item.id)"
                >
                  调试对话
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-empty
          v-else
          description="暂无可展示的 AI 应用，先创建一个简单应用或高级应用。"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Collection,
  Connection,
  Cpu,
  Document,
  Link,
  Refresh,
  UserFilled
} from '@element-plus/icons-vue'
import {
  listAiAgent,
  listAiWorkflow,
  listAllModels,
  type AiAgent,
  type AiModel,
  type AiWorkflow
} from '@/api/ai'
import { useUserStore } from '@/stores/user'

type ApplicationType = 'agent' | 'workflow'

interface ApplicationItem {
  id: string | number
  type: ApplicationType
  name: string
  description?: string
  published: boolean
  modelId?: string | number
  modelName?: string
  createTime?: string
}

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const keyword = ref('')
const activeType = ref<'all' | ApplicationType>('all')
const agentList = ref<AiAgent[]>([])
const workflowList = ref<AiWorkflow[]>([])
const modelList = ref<AiModel[]>([])

const canViewAgent = computed(() => userStore.hasPermission('ai:agent:list'))
const canViewWorkflow = computed(() => userStore.hasPermission('ai:workflow:list'))

const modelMap = computed(() => {
  return new Map(modelList.value.map((item) => [String(item.modelId), item.modelName]))
})

const applicationList = computed<ApplicationItem[]>(() => {
  const agentItems = canViewAgent.value
    ? agentList.value.map<ApplicationItem>((item) => ({
        id: item.agentId,
        type: 'agent',
        name: item.agentName,
        description: item.description,
        published: Boolean(item.published),
        modelId: item.modelId,
        modelName: item.modelId ? modelMap.value.get(String(item.modelId)) : undefined,
        createTime: item.createTime
      }))
    : []

  const workflowItems = canViewWorkflow.value
    ? workflowList.value.map<ApplicationItem>((item) => ({
        id: item.workflowId,
        type: 'workflow',
        name: item.workflowName,
        description: item.description,
        published: item.published === '1',
        modelId: item.modelId,
        modelName: item.modelId ? modelMap.value.get(String(item.modelId)) : undefined,
        createTime: item.createTime
      }))
    : []

  return [...agentItems, ...workflowItems].sort((left, right) => {
    return String(right.createTime || '').localeCompare(String(left.createTime || ''))
  })
})

const publishedCount = computed(() => {
  return applicationList.value.filter((item) => item.published).length
})

const filteredApplications = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return applicationList.value.filter((item) => {
    if (activeType.value !== 'all' && item.type !== activeType.value) {
      return false
    }
    if (!normalizedKeyword) {
      return true
    }
    const text = `${item.name} ${item.description || ''} ${item.modelName || ''}`.toLowerCase()
    return text.includes(normalizedKeyword)
  })
})

/** 应用总览一次性拉取的上限；超过这个量说明该做真分页了。 */
const APPLICATION_FETCH_SIZE = 100

async function loadData() {
  loading.value = true
  try {
    const modelResponse = await listAllModels('LLM')
    modelList.value = (modelResponse.data || []) as AiModel[]

    // 走 api/ai 的封装而不是手拼 URL：接口路径只能有一处定义，后端调整时才不会漏改
    const [agentResponse, workflowResponse] = await Promise.all([
      canViewAgent.value
        ? listAiAgent({ pageNum: 1, pageSize: APPLICATION_FETCH_SIZE }, { silentError: true }).catch(() => null)
        : Promise.resolve(null),
      canViewWorkflow.value
        ? listAiWorkflow({ pageNum: 1, pageSize: APPLICATION_FETCH_SIZE }, { silentError: true }).catch(() => null)
        : Promise.resolve(null)
    ])

    agentList.value = canViewAgent.value ? (agentResponse?.data?.rows || []) : []
    workflowList.value = canViewWorkflow.value ? (workflowResponse?.data?.rows || []) : []
    truncatedNotice.value = buildTruncatedNotice(agentResponse?.data?.total, workflowResponse?.data?.total)
  } finally {
    loading.value = false
  }
}

/** 列表被 pageSize 截断时给出明确提示，不要让用户以为应用「消失」了。 */
const truncatedNotice = ref('')

function buildTruncatedNotice(agentTotal?: number, workflowTotal?: number): string {
  const overflow: string[] = []
  if (Number(agentTotal || 0) > APPLICATION_FETCH_SIZE) overflow.push('智能体')
  if (Number(workflowTotal || 0) > APPLICATION_FETCH_SIZE) overflow.push('工作流')
  return overflow.length > 0
    ? `${overflow.join(' / ')}数量超过 ${APPLICATION_FETCH_SIZE} 个，此处仅展示前 ${APPLICATION_FETCH_SIZE} 个，请到对应管理页查看完整列表`
    : ''
}

function redirectToFirstAvailableRoute() {
  const candidates = [
    { permission: 'ai:model:list', path: '/ai/model' },
    { permission: 'ai:kb:list', path: '/ai/knowledge' },
    { permission: 'ai:mcp:list', path: '/ai/mcp' },
    { permission: 'ai:prompt:list', path: '/ai/prompt' },
    { permission: 'ai:token:stats', path: '/ai/token' }
  ]
  const target = candidates.find((item) => userStore.hasPermission(item.permission))
  router.replace(target?.path || '/ai/chat')
}

function goToAgentCreate() {
  router.push({ path: '/ai/agent', query: { action: 'create' } })
}

function goToWorkflowCreate() {
  router.push({ path: '/ai/workflow', query: { action: 'create' } })
}

function goToAgentChat(agentId: string | number) {
  router.push({ path: '/ai/agent', query: { action: 'chat', agentId: String(agentId) } })
}

function goToWorkflowChat(workflowId: string | number) {
  router.push({ path: '/ai/workflow', query: { action: 'chat', workflowId: String(workflowId) } })
}

function goToWorkflowDesigner(workflowId: string | number) {
  router.push(`/ai/workflow/designer/${workflowId}`)
}

function goToDetail(item: ApplicationItem) {
  router.push({
    name: 'AiApplicationDetail',
    params: {
      type: item.type,
      id: String(item.id)
    }
  })
}

function formatDate(value?: string) {
  if (!value) {
    return '暂无'
  }
  return value.length >= 10 ? value.slice(0, 10) : value
}

onMounted(async () => {
  if (!canViewAgent.value && !canViewWorkflow.value) {
    redirectToFirstAvailableRoute()
    return
  }
  await loadData()
})
</script>

<style lang="scss" scoped>
.ai-application-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  overflow: hidden;
  border: none;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.18), transparent 32%),
    linear-gradient(135deg, #f8fbff 0%, #eef4ff 100%);
}

.hero-content {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 20px;
  align-items: stretch;
}

.hero-copy h2 {
  margin: 10px 0 12px;
  font-size: 28px;
  line-height: 1.2;
  color: #0f172a;
}

.hero-copy p {
  margin: 0;
  max-width: 680px;
  color: #475569;
  line-height: 1.75;
}

.hero-badge {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.hero-side {
  display: flex;
}

.hero-panel {
  width: 100%;
  min-height: 100%;
  padding: 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.2);
  backdrop-filter: blur(10px);
}

.hero-panel-title {
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.hero-panel-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.hero-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff;
  color: #334155;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.8);
}

.summary-card {
  min-height: 124px;
}

.summary-label {
  color: #64748b;
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
}

.summary-meta {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.resource-card,
.application-list-card {
  border: 1px solid #e5e7eb;
}

.resource-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.resource-header h3,
.list-title {
  margin: 0;
  color: #111827;
}

.resource-header p,
.list-subtitle {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.resource-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.list-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar :deep(.el-input) {
  width: min(360px, 100%);
}

.application-col {
  margin-bottom: 16px;
}

.application-card {
  height: 100%;
  border-radius: 18px;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.application-card:hover {
  transform: translateY(-3px);
}

.application-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.application-card-icon {
  display: inline-flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 20px;
  color: #fff;
}

.application-card-icon.is-agent {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.application-card-icon.is-workflow {
  background: linear-gradient(135deg, #ea580c, #f97316);
}

.application-card-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.application-card-name {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.application-card-desc {
  display: -webkit-box;
  min-height: 42px;
  margin-top: 10px;
  overflow: hidden;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.application-card-meta {
  margin-top: 16px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.meta-row + .meta-row {
  margin-top: 8px;
}

.meta-label {
  color: #64748b;
}

.meta-value {
  color: #0f172a;
  font-weight: 600;
  text-align: right;
}

.application-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 14px;
}

@media (max-width: 1080px) {
  .hero-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-copy h2 {
    font-size: 24px;
  }

  .resource-header,
  .list-header {
    flex-direction: column;
    align-items: stretch;
  }

  .resource-actions,
  .hero-actions,
  .toolbar {
    width: 100%;
  }
}
</style>
