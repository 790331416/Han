<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="queryParams.name" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="流程标识" prop="key">
          <el-input v-model="queryParams.key" placeholder="请输入流程标识" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="流程分类" prop="category">
          <el-select v-model="queryParams.category" placeholder="请选择" clearable>
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 + 数据表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>流程定义列表</span>
          <div class="table-operations">
            <el-button type="primary" :icon="Upload" @click="handleDeploy">部署流程</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="definitionList">
        <el-table-column label="流程标识" prop="processKey" min-width="150" show-overflow-tooltip />
        <el-table-column label="流程名称" prop="processName" min-width="150" show-overflow-tooltip />
        <el-table-column label="流程分类" prop="category" width="120" align="center">
          <template #default="{ row }">
            <el-tag>{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="info">V{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.suspended ? 'danger' : 'success'">
              {{ row.suspended ? '已挂起' : '已激活' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部署时间" prop="deploymentTime" min-width="180" />
        <el-table-column label="操作" min-width="330">
          <template #default="{ row }">
            <el-button
              type="success"
              link
              :icon="Promotion"
              :disabled="row.suspended"
              data-testid="workflow-start-button"
              @click="handleStart(row)"
            >
              发起
            </el-button>
            <el-button type="primary" link :icon="View" @click="handleViewXml(row)">查看</el-button>
            <el-button v-if="row.suspended" type="success" link :icon="VideoPlay" @click="handleActivate(row)">激活</el-button>
            <el-button v-else type="warning" link :icon="VideoPause" @click="handleSuspend(row)">挂起</el-button>
            <el-button type="info" link :icon="Picture" @click="handleDiagram(row)">流程图</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- 部署对话框 -->
    <el-dialog v-model="deployVisible" title="部署流程定义" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="deployFormRef" :model="deployForm" :rules="deployRules" label-width="100px">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="deployForm.name" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程分类" prop="category">
          <el-select v-model="deployForm.category" placeholder="请选择流程分类">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".bpmn,.bpmn20.xml,.xml"
            :on-change="handleFileChange"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 .bpmn, .bpmn20.xml, .xml 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deployVisible = false">取消</el-button>
        <el-button type="primary" :loading="deployLoading" @click="handleDeploySubmit">部署</el-button>
      </template>
    </el-dialog>

    <!-- 发起流程对话框 -->
    <el-dialog v-model="startVisible" :title="`发起流程 - ${startTarget?.processName || ''}`" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="startFormRef" :model="startForm" label-width="110px" data-testid="workflow-start-form">
        <el-form-item label="流程标识">
          <el-input :model-value="startTarget?.processKey" disabled />
        </el-form-item>
        <el-form-item label="业务标识" prop="businessKey">
          <el-input v-model="startForm.businessKey" placeholder="业务单据号（可选）" />
        </el-form-item>
        <el-form-item label="流程标题" prop="title">
          <el-input v-model="startForm.title" placeholder="本次流程标题（可选）" />
        </el-form-item>
        <el-form-item label="指派审批人" prop="assignee">
          <el-select
            v-model="startForm.assignee"
            placeholder="按用户搜索（流程含审批节点时必选）"
            filterable
            remote
            clearable
            :remote-method="searchAssignee"
            :loading="assigneeLoading"
            data-testid="workflow-start-assignee"
          >
            <el-option
              v-for="user in assigneeOptions"
              :key="user.userId"
              :label="`${user.nickname || user.username}（${user.username}）`"
              :value="String(user.userId)"
            />
          </el-select>
          <div class="form-tip">审批节点按用户ID指派（assignee），请从搜索结果中选择用户。</div>
        </el-form-item>
        <el-form-item label="流程变量" prop="variablesJson">
          <el-input
            v-model="startForm.variablesJson"
            type="textarea"
            :rows="4"
            placeholder='附加变量 JSON（可选），如 {"amount": 1000}'
            data-testid="workflow-start-variables"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="startLoading" data-testid="workflow-start-submit" @click="handleStartSubmit">发起</el-button>
      </template>
    </el-dialog>

    <!-- 删除流程定义对话框：级联删除必须由用户显式勾选 -->
    <el-dialog v-model="deleteVisible" title="删除流程定义" width="45%" class="dialog-sm" destroy-on-close>
      <p class="delete-text">
        确定删除流程「{{ deleteTarget?.processName }}」（版本 V{{ deleteTarget?.version }}）吗？此操作不可恢复。
      </p>
      <el-checkbox v-model="deleteCascade" data-testid="workflow-delete-cascade">
        同时删除该部署下的全部流程实例与历史记录
      </el-checkbox>
      <el-alert
        v-if="deleteCascade"
        type="error"
        show-icon
        :closable="false"
        class="delete-alert"
      >
        级联删除会一并清除运行中的流程实例、已办历史和审批意见，删除后无法恢复，请确认业务方已知悉。
      </el-alert>
      <p v-else class="form-tip">
        不勾选时只删除流程定义本身；若该部署下仍存在流程实例，后端会拒绝删除。
      </p>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="deleteLoading"
          data-testid="workflow-delete-submit"
          @click="handleDeleteSubmit"
        >
          确定删除
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看XML对话框 -->
    <el-dialog v-model="xmlVisible" title="流程定义XML" width="75%" class="dialog-xl">
      <div class="xml-content">
        <pre><code>{{ xmlContent }}</code></pre>
      </div>
    </el-dialog>

    <!-- 流程图对话框 -->
    <el-dialog v-model="diagramVisible" title="流程图" width="80%" class="dialog-xl" @closed="releaseDiagram">
      <div v-loading="diagramLoading" class="diagram-content">
        <img v-if="diagramUrl" :src="diagramUrl" alt="流程图" style="max-width: 100%;" />
        <el-empty v-else-if="!diagramLoading" :description="diagramError || '暂无流程图'" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Upload, View, Delete, VideoPlay, VideoPause, Picture, Promotion } from '@element-plus/icons-vue'
import {
  listProcessDefinition, deployProcessDefinition, activateProcessDefinition,
  suspendProcessDefinition, deleteProcessDefinition, getProcessDefinitionXml,
  fetchProcessDefinitionDiagram, startProcessInstance, categoryOptions,
  type ProcessDefinition, type ProcessDefinitionQuery
} from '@/api/workflow'
import { listUser, type User } from '@/api/system/user'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'

const loading = ref(false)
const definitionList = ref<ProcessDefinition[]>([])
const total = ref(0)
const deployVisible = ref(false)
const deployLoading = ref(false)
const xmlVisible = ref(false)
const xmlContent = ref('')
const diagramVisible = ref(false)
const diagramUrl = ref('')
const diagramLoading = ref(false)
const diagramError = ref('')
const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deleteCascade = ref(false)
const deleteTarget = ref<ProcessDefinition | null>(null)

const queryFormRef = ref<FormInstance>()
const deployFormRef = ref<FormInstance>()

const queryParams = reactive<ProcessDefinitionQuery>({
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  key: undefined,
  category: undefined
})

const deployForm = reactive({
  name: '',
  category: '',
  file: null as File | null
})

const deployRules: FormRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择流程分类', trigger: 'change' }]
}

const getCategoryLabel = (value: string) => {
  return categoryOptions.find(item => item.value === value)?.label || value || '-'
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listProcessDefinition(queryParams)
    definitionList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handleDeploy = () => {
  deployForm.name = ''
  deployForm.category = ''
  deployForm.file = null
  deployVisible.value = true
}

const handleFileChange = (file: UploadFile) => {
  deployForm.file = file.raw || null
}

const handleDeploySubmit = async () => {
  const valid = await deployFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!deployForm.file) {
    ElMessage.warning('请选择流程文件')
    return
  }

  deployLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', deployForm.file)
    formData.append('name', deployForm.name)
    formData.append('category', deployForm.category)
    await deployProcessDefinition(formData)
    ElMessage.success('部署成功')
    deployVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    deployLoading.value = false
  }
}

const handleActivate = async (row: ProcessDefinition) => {
  try {
    await ElMessageBox.confirm(`确定激活流程"${row.processName}"吗?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await activateProcessDefinition(row.processDefinitionId)
    ElMessage.success('激活成功')
    getList()
  } catch { /* 失败提示由请求层统一处理 */ }
}

const handleSuspend = async (row: ProcessDefinition) => {
  try {
    await ElMessageBox.confirm(`确定挂起流程"${row.processName}"吗?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await suspendProcessDefinition(row.processDefinitionId)
    ElMessage.success('挂起成功')
    getList()
  } catch { /* 失败提示由请求层统一处理 */ }
}

const handleDelete = (row: ProcessDefinition) => {
  deleteTarget.value = row
  // 级联删除默认关闭：历史实现写死 cascade=true，会连带删掉运行中的实例与全部历史
  deleteCascade.value = false
  deleteVisible.value = true
}

const handleDeleteSubmit = async () => {
  if (!deleteTarget.value) return
  deleteLoading.value = true
  try {
    await deleteProcessDefinition(deleteTarget.value.deploymentId, deleteCascade.value)
    ElMessage.success('删除成功')
    deleteVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    deleteLoading.value = false
  }
}

const handleViewXml = async (row: ProcessDefinition) => {
  try {
    const res = await getProcessDefinitionXml(row.processDefinitionId)
    xmlContent.value = res.data
    xmlVisible.value = true
  } catch { /* 失败提示由请求层统一处理 */ }
}

/** 释放上一张流程图的 object URL，避免 blob 常驻内存 */
const releaseDiagram = () => {
  if (diagramUrl.value) {
    URL.revokeObjectURL(diagramUrl.value)
    diagramUrl.value = ''
  }
}

const handleDiagram = async (row: ProcessDefinition) => {
  releaseDiagram()
  diagramError.value = ''
  diagramVisible.value = true
  diagramLoading.value = true
  try {
    const blob = await fetchProcessDefinitionDiagram(row.processDefinitionId)
    diagramUrl.value = URL.createObjectURL(blob)
  } catch (e: any) {
    diagramError.value = e?.message || '流程图加载失败，请稍后重试'
  } finally {
    diagramLoading.value = false
  }
}

// ==================== 发起流程（E-flowstart：UI 化流程发起入口） ====================
const router = useRouter()
const startVisible = ref(false)
const startLoading = ref(false)
const startTarget = ref<ProcessDefinition | null>(null)
const startFormRef = ref<FormInstance>()
const assigneeOptions = ref<User[]>([])
const assigneeLoading = ref(false)

const startForm = reactive({
  businessKey: '',
  title: '',
  assignee: '',
  variablesJson: ''
})

const handleStart = (row: ProcessDefinition) => {
  startTarget.value = row
  startForm.businessKey = ''
  startForm.title = ''
  startForm.assignee = ''
  startForm.variablesJson = ''
  assigneeOptions.value = []
  startVisible.value = true
}

/** 审批人远程搜索：按用户名/昵称模糊查，选项值为 userId（审批节点 assignee 必须 userId）。 */
const searchAssignee = async (keyword: string) => {
  if (!keyword?.trim()) {
    assigneeOptions.value = []
    return
  }
  assigneeLoading.value = true
  try {
    const res = await listUser({ pageNum: 1, pageSize: 20, username: keyword.trim() })
    assigneeOptions.value = (res as any).data?.rows || []
  } catch { assigneeOptions.value = [] } finally {
    assigneeLoading.value = false
  }
}

const handleStartSubmit = async () => {
  if (!startTarget.value) return
  let extraVariables: Record<string, any> = {}
  if (startForm.variablesJson.trim()) {
    try {
      extraVariables = JSON.parse(startForm.variablesJson.trim())
      if (typeof extraVariables !== 'object' || Array.isArray(extraVariables) || extraVariables === null) {
        ElMessage.warning('流程变量必须是 JSON 对象')
        return
      }
    } catch {
      ElMessage.warning('流程变量不是合法 JSON')
      return
    }
  }
  const variables: Record<string, any> = { ...extraVariables }
  if (startForm.title.trim()) {
    variables.title = startForm.title.trim()
  }
  if (startForm.assignee) {
    variables.assignee = startForm.assignee
  }

  startLoading.value = true
  try {
    await startProcessInstance({
      processDefinitionKey: startTarget.value.processKey,
      businessKey: startForm.businessKey.trim() || undefined,
      variables
    })
    ElMessage.success('流程发起成功')
    startVisible.value = false
    // 发起后跳待办：指派他人时自己待办可能为空，仍以待办页为流转入口
    router.push('/workflow/todo')
  } catch { /* 接口不可用或校验失败，由拦截器提示 */ } finally {
    startLoading.value = false
  }
}

onMounted(() => {
  getList()
})

onBeforeUnmount(() => {
  releaseDiagram()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.xml-content {
  max-height: 500px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 16px;

  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 13px;
    line-height: 1.6;
  }
}

.diagram-content {
  text-align: center;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

.delete-text {
  margin: 0 0 12px;
  line-height: 1.6;
}

.delete-alert {
  margin-top: 12px;
}
</style>
