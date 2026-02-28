<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="工作流名称" prop="workflowName">
          <el-input v-model="queryParams.workflowName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="workflowType">
          <el-select v-model="queryParams.workflowType" placeholder="请选择" clearable>
            <el-option v-for="item in workflowTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
          <span>AI工作流管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">创建工作流</el-button>
        </div>
      </template>

      <el-row :gutter="20" v-loading="loading">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="wf in workflowList" :key="wf.workflowId" class="wf-col">
          <el-card shadow="hover" class="wf-card">
            <div class="wf-card-header">
              <el-icon :size="28" :color="wf.published === '1' ? '#67c23a' : '#909399'"><ChatDotRound /></el-icon>
              <div class="wf-card-actions">
                <el-tag size="small" :type="wf.published === '1' ? 'success' : 'info'">
                  {{ wf.published === '1' ? '已发布' : '未发布' }}
                </el-tag>
                <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, wf)">
                  <el-icon class="wf-more"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="design">设计流程</el-dropdown-item>
                      <el-dropdown-item command="chat" v-if="wf.published === '1'">对话测试</el-dropdown-item>
                      <el-dropdown-item :command="wf.published === '1' ? 'unpublish' : 'publish'">
                        {{ wf.published === '1' ? '取消发布' : '发布' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <h3 class="wf-name">{{ wf.workflowName }}</h3>
            <p class="wf-desc">{{ wf.description || '暂无描述' }}</p>
            <div class="wf-footer">
              <el-tag size="small" type="info">{{ getWorkflowTypeLabel(wf.workflowType) }}</el-tag>
              <span class="wf-time">{{ wf.createTime?.substring(0, 10) }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="24" v-if="!loading && workflowList.length === 0">
          <el-empty description="暂无AI工作流" />
        </el-col>
      </el-row>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[8, 16, 32]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.workflowId ? '编辑工作流' : '创建工作流'" width="800px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工作流名称" prop="workflowName">
              <el-input v-model="form.workflowName" placeholder="请输入名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="workflowType">
              <el-select v-model="form.workflowType" placeholder="请选择">
                <el-option v-for="item in workflowTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="AI模型" prop="modelId">
          <el-select v-model="form.modelId" placeholder="请选择AI模型" filterable>
            <el-option v-for="m in llmModels" :key="m.modelId" :label="`${m.modelName} (${m.modelCode})`" :value="m.modelId" />
          </el-select>
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
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" placeholder="设置AI的角色和行为，如：你是一个专业的客服助手..." />
        </el-form-item>
        <el-form-item label="开场白" prop="prologue">
          <el-input v-model="form.prologue" type="textarea" :rows="2" placeholder="用户打开对话时显示的欢迎语" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="工作流描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 对话测试对话框 -->
    <el-dialog v-model="chatVisible" :title="`对话测试 - ${currentWf?.workflowName || ''}`" width="700px" destroy-on-close>
      <div class="chat-container">
        <div class="chat-messages" ref="chatMessagesRef">
          <div v-if="currentWf?.prologue" class="chat-message assistant">
            <div class="chat-bubble">{{ currentWf.prologue }}</div>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, ChatDotRound, MoreFilled, Promotion, Loading } from '@element-plus/icons-vue'
import {
  listAiWorkflow, getAiWorkflow, addAiWorkflow, updateAiWorkflow, deleteAiWorkflow,
  publishAiWorkflow, unpublishAiWorkflow, chatWithWorkflow,
  listAllModels, listAllKnowledgeBases, listAllMcpServers,
  workflowTypeOptions,
  type AiWorkflow, type AiWorkflowQuery, type AiModel, type KnowledgeBase, type McpServer
} from '@/api/ai'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const workflowList = ref<AiWorkflow[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const chatVisible = ref(false)
const chatLoading = ref(false)
const chatInput = ref('')
const chatMessages = ref<{ role: string; content: string }[]>([])
const currentWf = ref<AiWorkflow | null>(null)
const chatMessagesRef = ref<HTMLElement>()

const llmModels = ref<AiModel[]>([])
const allKbs = ref<KnowledgeBase[]>([])
const allMcps = ref<McpServer[]>([])
const selectedKbIds = ref<number[]>([])
const selectedMcpIds = ref<number[]>([])

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<AiWorkflowQuery>({ pageNum: 1, pageSize: 8 })

const defaultForm = () => ({
  workflowId: undefined as any, workflowName: '', description: '', workflowType: 'simple',
  modelId: undefined as any, systemPrompt: '', prologue: '', status: '0'
})
const form = reactive<any>(defaultForm())

const rules: FormRules = {
  workflowName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  workflowType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  modelId: [{ required: true, message: '请选择AI模型', trigger: 'change' }]
}

const getWorkflowTypeLabel = (v: string) => workflowTypeOptions.find(i => i.value === v)?.label || v

const getList = async () => {
  loading.value = true
  try {
    const res = await listAiWorkflow(queryParams)
    workflowList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }

const loadOptions = async () => {
  try {
    const [m, k, mc] = await Promise.all([listAllModels('LLM'), listAllKnowledgeBases(), listAllMcpServers()])
    llmModels.value = m.data || []
    allKbs.value = k.data || []
    allMcps.value = mc.data || []
  } catch { /* 接口不可用 */ }
}

const handleAdd = async () => {
  Object.assign(form, defaultForm())
  selectedKbIds.value = []
  selectedMcpIds.value = []
  await loadOptions()
  dialogVisible.value = true
}

const handleCommand = (cmd: string, wf: AiWorkflow) => {
  if (cmd === 'edit') handleEdit(wf)
  else if (cmd === 'design') handleDesign(wf)
  else if (cmd === 'delete') handleDelete(wf)
  else if (cmd === 'publish') handlePublish(wf)
  else if (cmd === 'unpublish') handleUnpublish(wf)
  else if (cmd === 'chat') handleChat(wf)
}

const handleDesign = (wf: AiWorkflow) => {
  router.push(`/ai/workflow/designer/${wf.workflowId}`)
}

const handleEdit = async (wf: AiWorkflow) => {
  const res = await getAiWorkflow(wf.workflowId)
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
    if (form.workflowId) { await updateAiWorkflow(form); ElMessage.success('修改成功') }
    else { await addAiWorkflow(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    getList()
  } catch { /* 接口不可用 */ } finally { submitLoading.value = false }
}

const handleDelete = async (wf: AiWorkflow) => {
  try {
    await ElMessageBox.confirm(`确定删除工作流"${wf.workflowName}"吗?`, '提示', { type: 'warning' })
    await deleteAiWorkflow(wf.workflowId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 接口不可用 */ }
}

const handlePublish = async (wf: AiWorkflow) => {
  try {
    await publishAiWorkflow(wf.workflowId)
    ElMessage.success('发布成功')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleUnpublish = async (wf: AiWorkflow) => {
  try {
    await unpublishAiWorkflow(wf.workflowId)
    ElMessage.success('已取消发布')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleChat = (wf: AiWorkflow) => {
  currentWf.value = wf
  chatMessages.value = []
  chatInput.value = ''
  chatVisible.value = true
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  })
}

const handleSendMessage = async () => {
  const msg = chatInput.value.trim()
  if (!msg || !currentWf.value || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: msg })
  chatInput.value = ''
  chatLoading.value = true
  scrollToBottom()
  try {
    const res = await chatWithWorkflow(currentWf.value.workflowId, msg)
    let content = res.data || '无响应'
    // 尝试解析OpenAI格式的响应
    try {
      const parsed = JSON.parse(content)
      if (parsed.choices?.[0]?.message?.content) {
        content = parsed.choices[0].message.content
      }
    } catch { /* 非JSON格式，直接使用 */ }
    chatMessages.value.push({ role: 'assistant', content })
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: '请求失败: ' + (e.message || '未知错误') })
  } finally {
    chatLoading.value = false
    scrollToBottom()
  }
}

onMounted(() => getList())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

.wf-col { margin-bottom: 20px; }
.wf-card { transition: transform 0.2s; &:hover { transform: translateY(-4px); } }
.wf-card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.wf-card-actions { display: flex; align-items: center; gap: 8px; }
.wf-more { cursor: pointer; color: #909399; &:hover { color: #409eff; } }
.wf-name { margin: 10px 0 6px; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wf-desc { color: #909399; font-size: 13px; margin-bottom: 12px; height: 36px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.wf-footer { display: flex; justify-content: space-between; align-items: center; }
.wf-time { font-size: 12px; color: #c0c4cc; }

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
