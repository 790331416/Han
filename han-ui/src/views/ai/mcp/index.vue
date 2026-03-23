<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="服务名称" prop="serverName">
          <el-input v-model="queryParams.serverName" placeholder="请输入服务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="传输类型" prop="transportType">
          <el-select v-model="queryParams.transportType" placeholder="请选择" clearable>
            <el-option v-for="item in transportTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>MCP服务管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增MCP服务</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="mcpList">
        <el-table-column label="服务名称" prop="serverName" min-width="150" show-overflow-tooltip />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="传输类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.transportType === 'stdio' ? 'warning' : 'primary'">
              {{ getTransportLabel(row.transportType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.transportType === 'stdio' ? row.command : row.url || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="工具数" width="80" align="center">
          <template #default="{ row }">
            {{ getToolCount(row.tools) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }">
            <el-button type="success" link @click="handleRefresh(row)">刷新工具</el-button>
            <el-button type="info" link @click="handleViewTools(row)">查看工具</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.mcpId ? '编辑MCP服务' : '新增MCP服务'" width="65%" class="dialog-lg" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="服务名称" prop="serverName">
          <el-input v-model="form.serverName" placeholder="请输入服务名称" />
        </el-form-item>
        <el-form-item label="传输类型" prop="transportType">
          <el-radio-group v-model="form.transportType">
            <el-radio-button v-for="item in transportTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.transportType === 'stdio'">
          <el-form-item label="命令" prop="command">
            <el-input v-model="form.command" placeholder="如: npx, uvx, node" />
          </el-form-item>
          <el-form-item label="参数" prop="args">
            <el-input v-model="form.args" type="textarea" :rows="2" placeholder='JSON数组格式，如: ["-y", "@modelcontextprotocol/server-filesystem"]' />
          </el-form-item>
          <el-form-item label="环境变量" prop="envVars">
            <el-input v-model="form.envVars" type="textarea" :rows="2" placeholder='JSON对象格式，如: {"API_KEY": "xxx"}' />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="服务URL" prop="url">
            <el-input v-model="form.url" placeholder="如: http://localhost:3001/sse" />
          </el-form-item>
        </template>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="服务描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看工具对话框 -->
    <el-dialog v-model="toolsVisible" title="MCP工具列表" width="65%" class="dialog-lg">
      <el-table :data="toolsList" v-if="toolsList.length > 0">
        <el-table-column label="工具名称" prop="name" width="200" />
        <el-table-column label="描述" prop="description" min-width="300" show-overflow-tooltip />
      </el-table>
      <el-empty v-else description="暂无工具，请先刷新" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  listMcpServer, getMcpServer, addMcpServer, updateMcpServer, deleteMcpServer, refreshMcpTools,
  transportTypeOptions, type McpServer, type McpServerQuery
} from '@/api/ai'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const mcpList = ref<McpServer[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const toolsVisible = ref(false)
const toolsList = ref<any[]>([])
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<McpServerQuery>({ pageNum: 1, pageSize: 10 })

const defaultForm = () => ({
  mcpId: undefined as any, serverName: '', description: '', transportType: 'sse',
  command: '', args: '[]', envVars: '{}', url: '', status: '0'
})
const form = reactive<any>(defaultForm())

const rules: FormRules = {
  serverName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  transportType: [{ required: true, message: '请选择传输类型', trigger: 'change' }]
}

const getTransportLabel = (v: string) => transportTypeOptions.find(i => i.value === v)?.label || v
const getToolCount = (tools?: string) => {
  try { return tools ? JSON.parse(tools).length : 0 } catch { return 0 }
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listMcpServer(queryParams)
    mcpList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }
const handleAdd = () => { Object.assign(form, defaultForm()); dialogVisible.value = true }

const handleEdit = async (row: McpServer) => {
  const res = await getMcpServer(row.mcpId)
  Object.assign(form, res.data)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.mcpId) { await updateMcpServer(form); ElMessage.success('修改成功') }
    else { await addMcpServer(form); ElMessage.success('新增成功') }
    dialogVisible.value = false
    getList()
  } catch { /* 接口不可用 */ } finally { submitLoading.value = false }
}

const handleDelete = async (row: McpServer) => {
  try {
    await ElMessageBox.confirm(`确定删除MCP服务"${row.serverName}"吗?`, '提示', { type: 'warning' })
    await deleteMcpServer(row.mcpId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleRefresh = async (row: McpServer) => {
  try {
    ElMessage.info('正在刷新工具列表...')
    const res = await refreshMcpTools(row.mcpId)
    ElMessage.success(res.data || '刷新完成')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleViewTools = (row: McpServer) => {
  try { toolsList.value = row.tools ? JSON.parse(row.tools) : [] } catch { toolsList.value = [] }
  toolsVisible.value = true
}

onMounted(() => getList())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
