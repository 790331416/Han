<template>
  <div class="app-container" data-testid="ai-agent-page">
    <el-card shadow="never" class="search-form">
      <el-form :inline="true">
        <el-form-item label="智能体名称">
          <el-input v-model="queryParams.agentName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>智能体管理</span>
          <el-button type="primary" :icon="Plus" data-testid="ai-agent-create-button" @click="handleAdd">
            创建智能体
          </el-button>
        </div>
      </template>

      <el-row :gutter="20" v-loading="loading" data-testid="ai-agent-list">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.agentId" class="agent-col">
          <el-card shadow="hover" class="agent-card" data-testid="ai-agent-card">
            <div class="agent-card-header">
              <el-avatar :size="40" :src="agent.avatar" style="background: #409eff;">
                {{ agent.agentName?.charAt(0) }}
              </el-avatar>
              <div class="agent-card-actions">
                <el-tag size="small" :type="agent.published ? 'success' : 'info'">
                  {{ agent.published ? '已发布' : '未发布' }}
                </el-tag>
                <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, agent)">
                  <el-icon class="agent-more"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="chat" v-if="agent.published">对话测试</el-dropdown-item>
                      <el-dropdown-item :command="agent.published ? 'unpublish' : 'publish'">
                        {{ agent.published ? '取消发布' : '发布' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <h3 class="agent-name">{{ agent.agentName }}</h3>
            <p class="agent-desc">{{ agent.description || '暂无描述' }}</p>
            <div class="agent-footer">
              <span class="agent-time">{{ agent.createTime?.substring(0, 10) }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="24" v-if="!loading && agentList.length === 0">
          <el-empty description="暂无智能体" />
        </el-col>
      </el-row>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[8, 16, 32]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.agentId ? '编辑智能体' : '创建智能体'" width="70%" class="dialog-xl" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" data-testid="ai-agent-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="智能体名称" prop="agentName">
              <el-input v-model="form.agentName" placeholder="请输入名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AI模型" prop="modelId">
              <el-select v-model="form.modelId" placeholder="请选择" filterable>
                <el-option v-for="m in llmModels" :key="m.modelId" :label="`${m.modelName} (${m.modelCode})`" :value="m.modelId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色设定" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" placeholder="定义智能体的角色、能力和行为规则..." />
        </el-form-item>
        <el-form-item label="开场白">
          <el-input v-model="form.prologue" type="textarea" :rows="2" placeholder="用户打开对话时的欢迎语" />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="selectedKbIds" multiple placeholder="选择知识库(可选)" filterable>
            <el-option v-for="kb in allKbs" :key="kb.kbId" :label="kb.kbName" :value="kb.kbId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联MCP服务">
          <el-select v-model="selectedMcpIds" multiple placeholder="选择MCP服务(可选)" filterable>
            <el-option v-for="mcp in allMcps" :key="mcp.mcpId" :label="mcp.serverName" :value="mcp.mcpId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="温度">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大Token">
              <el-input-number v-model="form.maxTokens" :min="256" :max="32768" :step="256" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="智能体描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 对话测试 -->
    <el-dialog v-model="chatVisible" :title="`对话测试 - ${currentAgent?.agentName || ''}`" width="65%" class="dialog-lg" destroy-on-close>
      <div class="chat-container">
        <div class="chat-messages" ref="chatMessagesRef">
          <div v-if="currentAgent?.prologue" class="chat-message assistant">
            <div class="chat-bubble">{{ currentAgent.prologue }}</div>
          </div>
          <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['chat-message', msg.role]">
            <div class="chat-bubble">
              <pre v-if="msg.role === 'assistant'" class="chat-pre">{{ msg.content }}</pre>
              <span v-else>{{ msg.content }}</span>
            </div>
          </div>
          <div v-if="chatLoading" class="chat-message assistant">
            <div class="chat-bubble"><el-icon class="is-loading"><Loading /></el-icon> 思考中...</div>
          </div>
        </div>
        <div class="chat-input">
          <el-input v-model="chatInput" placeholder="输入消息..." @keyup.enter="handleSendMessage" :disabled="chatLoading">
            <template #append>
              <el-button :icon="Promotion" @click="handleSendMessage" :loading="chatLoading" />
            </template>
          </el-input>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MoreFilled, Promotion, Loading } from '@element-plus/icons-vue'
import {
  listAiAgent, getAiAgent, addAiAgent, updateAiAgent, deleteAiAgent,
  publishAiAgent, unpublishAiAgent, chatWithAgent,
  listAllModels, listAllKnowledgeBases, listAllMcpServers,
  type AiAgent, type AiAgentQuery, type AiModel, type KnowledgeBase, type McpServer
} from '@/api/ai'
import type { FormInstance, FormRules } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const agentList = ref<AiAgent[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const chatVisible = ref(false)
const chatLoading = ref(false)
const chatInput = ref('')
const chatMessages = ref<{ role: string; content: string }[]>([])
const currentAgent = ref<AiAgent | null>(null)
const chatMessagesRef = ref<HTMLElement>()

const llmModels = ref<AiModel[]>([])
const allKbs = ref<KnowledgeBase[]>([])
const allMcps = ref<McpServer[]>([])
const selectedKbIds = ref<(string | number)[]>([])
const selectedMcpIds = ref<(string | number)[]>([])

const formRef = ref<FormInstance>()
const queryParams = reactive<AiAgentQuery>({ pageNum: 1, pageSize: 8 })

const defaultForm = () => ({
  agentId: undefined as any, agentName: '', description: '', systemPrompt: '', prologue: '',
  modelId: undefined as any, temperature: 0.7, maxTokens: 2048, status: '0'
})
const form = reactive<any>(defaultForm())

const rules: FormRules = {
  agentName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelId: [{ required: true, message: '请选择AI模型', trigger: 'change' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listAiAgent(queryParams)
    agentList.value = res.data.rows
    total.value = res.data.total
  } catch { /* ignore */ } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryParams.agentName = ''; handleQuery() }

const loadOptions = async () => {
  try {
    const [m, k, mc] = await Promise.all([listAllModels('LLM'), listAllKnowledgeBases(), listAllMcpServers()])
    llmModels.value = m.data || []
    allKbs.value = k.data || []
    allMcps.value = mc.data || []
  } catch { /* ignore */ }
}

const handleAdd = async () => {
  Object.assign(form, defaultForm())
  selectedKbIds.value = []
  selectedMcpIds.value = []
  await loadOptions()
  dialogVisible.value = true
}

const handleCommand = (cmd: string, agent: AiAgent) => {
  if (cmd === 'edit') handleEdit(agent)
  else if (cmd === 'delete') handleDelete(agent)
  else if (cmd === 'publish') handlePublish(agent)
  else if (cmd === 'unpublish') handleUnpublish(agent)
  else if (cmd === 'chat') handleChat(agent)
}

const handleEdit = async (agent: AiAgent) => {
  const res = await getAiAgent(agent.agentId)
  Object.assign(form, res.data)
  try { selectedKbIds.value = res.data.knowledgeBaseIds ? JSON.parse(res.data.knowledgeBaseIds) : [] } catch { selectedKbIds.value = [] }
  try { selectedMcpIds.value = res.data.mcpServerIds ? JSON.parse(res.data.mcpServerIds) : [] } catch { selectedMcpIds.value = [] }
  await loadOptions()
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  form.knowledgeBaseIds = JSON.stringify(selectedKbIds.value)
  form.mcpServerIds = JSON.stringify(selectedMcpIds.value)
  try {
    if (form.agentId) { await updateAiAgent(form); ElMessage.success('修改成功') }
    else { await addAiAgent(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    getList()
  } catch { /* ignore */ } finally { submitLoading.value = false }
}

const handleDelete = async (agent: AiAgent) => {
  await ElMessageBox.confirm(`确定删除智能体"${agent.agentName}"吗?`, '提示', { type: 'warning' })
  await deleteAiAgent(agent.agentId)
  ElMessage.success('删除成功')
  getList()
}

const handlePublish = async (agent: AiAgent) => {
  await publishAiAgent(agent.agentId)
  ElMessage.success('发布成功')
  getList()
}

const handleUnpublish = async (agent: AiAgent) => {
  await unpublishAiAgent(agent.agentId)
  ElMessage.success('已取消发布')
  getList()
}

const handleChat = (agent: AiAgent) => {
  currentAgent.value = agent
  chatMessages.value = []
  chatInput.value = ''
  chatVisible.value = true
}

async function handleRouteAction() {
  const action = String(route.query.action || '')
  if (!action) {
    return
  }

  if (action === 'create') {
    await handleAdd()
    clearRouteAction()
    return
  }

  if (action === 'chat' && route.query.agentId) {
    const agentId = String(route.query.agentId)
    const target = agentList.value.find((item) => String(item.agentId) === agentId)
    if (target) {
      handleChat(target)
    } else {
      try {
        const res = await getAiAgent(agentId)
        handleChat(res.data)
      } catch {
        // ignore
      }
    }
    clearRouteAction()
  }
}

function clearRouteAction() {
  const nextQuery = { ...route.query }
  delete nextQuery.action
  delete nextQuery.agentId
  router.replace({ path: route.path, query: nextQuery })
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatMessagesRef.value) chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  })
}

const handleSendMessage = async () => {
  const msg = chatInput.value.trim()
  if (!msg || !currentAgent.value || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: msg })
  chatInput.value = ''
  chatLoading.value = true
  scrollToBottom()
  try {
    const res = await chatWithAgent(currentAgent.value.agentId, msg)
    chatMessages.value.push({ role: 'assistant', content: res.data || '无响应' })
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: '请求失败: ' + (e.message || '未知错误') })
  } finally {
    chatLoading.value = false
    scrollToBottom()
  }
}

onMounted(async () => {
  await getList()
  await handleRouteAction()
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

.agent-col { margin-bottom: 20px; }
.agent-card { transition: transform 0.2s; &:hover { transform: translateY(-4px); } }
.agent-card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.agent-card-actions { display: flex; align-items: center; gap: 8px; }
.agent-more { cursor: pointer; color: #909399; &:hover { color: #409eff; } }
.agent-name { margin: 10px 0 6px; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.agent-desc { color: #909399; font-size: 13px; margin-bottom: 12px; height: 36px; overflow: hidden; }
.agent-footer { display: flex; justify-content: flex-end; }
.agent-time { font-size: 12px; color: #c0c4cc; }

.chat-container { display: flex; flex-direction: column; height: 500px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px; background: #f5f7fa; border-radius: 8px; margin-bottom: 12px; }
.chat-message { display: flex; margin-bottom: 16px;
  &.user { justify-content: flex-end; }
  &.assistant { justify-content: flex-start; }
}
.chat-bubble { max-width: 80%; padding: 10px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; word-break: break-word;
  .user & { background: #409eff; color: #fff; border-bottom-right-radius: 4px; }
  .assistant & { background: #fff; color: #303133; border: 1px solid #e4e7ed; border-bottom-left-radius: 4px; }
}
.chat-pre { margin: 0; white-space: pre-wrap; font-family: inherit; font-size: inherit; }
.chat-input { flex-shrink: 0; }
</style>
