<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="流程名称" prop="processDefinitionName">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable>
            <el-option v-for="item in instanceStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>流程实例列表</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="instanceList">
        <el-table-column label="实例ID" prop="instanceId" width="180" show-overflow-tooltip />
        <el-table-column label="流程名称" prop="processDefinitionName" min-width="150" show-overflow-tooltip />
        <el-table-column label="流程标识" prop="processDefinitionKey" width="150" show-overflow-tooltip />
        <el-table-column label="业务标识" prop="businessKey" width="150" show-overflow-tooltip />
        <el-table-column label="发起人" prop="startUserName" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" prop="startTime" width="180" />
        <el-table-column label="结束时间" prop="endTime" width="180">
          <template #default="{ row }">{{ row.endTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'running'">
              <el-button type="warning" link :icon="VideoPause" @click="handleSuspend(row)">挂起</el-button>
              <el-button type="danger" link :icon="CircleClose" @click="handleStop(row)">终止</el-button>
            </template>
            <template v-if="row.status === 'suspended'">
              <el-button type="success" link :icon="VideoPlay" @click="handleActivate(row)">激活</el-button>
            </template>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)" v-if="row.status !== 'running'">删除</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, VideoPlay, VideoPause, CircleClose, Delete } from '@element-plus/icons-vue'
import {
  listProcessInstance, stopProcessInstance, suspendProcessInstance,
  activateProcessInstance, deleteProcessInstance,
  instanceStatusOptions,
  type ProcessInstance, type ProcessInstanceQuery
} from '@/api/workflow'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const instanceList = ref<ProcessInstance[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()

const queryParams = reactive<ProcessInstanceQuery>({
  pageNum: 1,
  pageSize: 10,
  processDefinitionName: undefined,
  status: undefined
})

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const getStatusType = (status: string): TagType => {
  const map: Record<string, TagType> = { running: 'primary', completed: 'success', suspended: 'warning', terminated: 'danger' }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  return instanceStatusOptions.find(item => item.value === status)?.label || status
}

const formatDuration = (ms?: number) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  if (ms < 3600000) return `${Math.round(ms / 60000)}m`
  return `${Math.round(ms / 3600000)}h`
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listProcessInstance(queryParams)
    instanceList.value = res.data.rows
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

const handleSuspend = async (row: ProcessInstance) => {
  await ElMessageBox.confirm(`确定挂起该流程实例吗?`, '提示', { type: 'warning' })
  await suspendProcessInstance(row.instanceId)
  ElMessage.success('挂起成功')
  getList()
}

const handleActivate = async (row: ProcessInstance) => {
  await ElMessageBox.confirm(`确定激活该流程实例吗?`, '提示', { type: 'warning' })
  await activateProcessInstance(row.instanceId)
  ElMessage.success('激活成功')
  getList()
}

const handleStop = async (row: ProcessInstance) => {
  await ElMessageBox.confirm(`确定终止该流程实例吗? 此操作不可恢复!`, '提示', { type: 'warning' })
  await stopProcessInstance(row.instanceId, '管理员终止')
  ElMessage.success('终止成功')
  getList()
}

const handleDelete = async (row: ProcessInstance) => {
  await ElMessageBox.confirm(`确定删除该流程实例吗?`, '提示', { type: 'warning' })
  await deleteProcessInstance(row.instanceId)
  ElMessage.success('删除成功')
  getList()
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
</style>
