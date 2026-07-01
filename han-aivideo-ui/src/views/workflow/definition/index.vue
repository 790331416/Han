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
        <el-table-column label="流程标识" prop="key" min-width="150" show-overflow-tooltip />
        <el-table-column label="流程名称" prop="name" min-width="150" show-overflow-tooltip />
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
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }">
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

    <!-- 查看XML对话框 -->
    <el-dialog v-model="xmlVisible" title="流程定义XML" width="75%" class="dialog-xl">
      <div class="xml-content">
        <pre><code>{{ xmlContent }}</code></pre>
      </div>
    </el-dialog>

    <!-- 流程图对话框 -->
    <el-dialog v-model="diagramVisible" title="流程图" width="80%" class="dialog-xl">
      <div class="diagram-content">
        <img v-if="diagramUrl" :src="diagramUrl" alt="流程图" style="max-width: 100%;" />
        <el-empty v-else description="暂无流程图" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Upload, View, Delete, VideoPlay, VideoPause, Picture } from '@element-plus/icons-vue'
import {
  listProcessDefinition, deployProcessDefinition, activateProcessDefinition,
  suspendProcessDefinition, deleteProcessDefinition, getProcessDefinitionXml,
  categoryOptions,
  type ProcessDefinition, type ProcessDefinitionQuery
} from '@/api/workflow'
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
  const valid = await deployFormRef.value?.validate()
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
  } finally {
    deployLoading.value = false
  }
}

const handleActivate = async (row: ProcessDefinition) => {
  await ElMessageBox.confirm(`确定激活流程"${row.name}"吗?`, '提示', { type: 'warning' })
  await activateProcessDefinition(row.id)
  ElMessage.success('激活成功')
  getList()
}

const handleSuspend = async (row: ProcessDefinition) => {
  await ElMessageBox.confirm(`确定挂起流程"${row.name}"吗?`, '提示', { type: 'warning' })
  await suspendProcessDefinition(row.id)
  ElMessage.success('挂起成功')
  getList()
}

const handleDelete = async (row: ProcessDefinition) => {
  await ElMessageBox.confirm(`确定删除流程"${row.name}"吗? 此操作不可恢复!`, '提示', { type: 'warning' })
  await deleteProcessDefinition(row.deploymentId, true)
  ElMessage.success('删除成功')
  getList()
}

const handleViewXml = async (row: ProcessDefinition) => {
  const res = await getProcessDefinitionXml(row.id)
  xmlContent.value = res.data
  xmlVisible.value = true
}

const handleDiagram = (row: ProcessDefinition) => {
  const baseUrl = import.meta.env.VITE_APP_BASE_API
  diagramUrl.value = `${baseUrl}/workflow/definition/diagram/${row.id}`
  diagramVisible.value = true
}

onMounted(() => {
  getList()
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
</style>
