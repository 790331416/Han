<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="流程名称" prop="processDefinitionName">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
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
          <span>已办任务</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList">
        <el-table-column label="任务名称" prop="taskName" min-width="150" show-overflow-tooltip />
        <el-table-column label="所属流程" prop="processDefinitionName" min-width="150" show-overflow-tooltip />
        <el-table-column label="业务标识" prop="businessKey" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.businessKey || '-' }}</template>
        </el-table-column>
        <el-table-column label="办理人" prop="assigneeName" width="100" align="center">
          <template #default="{ row }">{{ row.assigneeName || row.assignee || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="完成时间" prop="endTime" width="180">
          <template #default="{ row }">{{ row.endTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
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
import { Search, Refresh } from '@element-plus/icons-vue'
import { listDoneTask, type TaskItem, type TaskQuery } from '@/api/workflow'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const taskList = ref<TaskItem[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()

const queryParams = reactive<TaskQuery>({
  pageNum: 1,
  pageSize: 10,
  taskName: undefined,
  processDefinitionName: undefined
})

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
    const res = await listDoneTask(queryParams)
    taskList.value = res.data.rows
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
