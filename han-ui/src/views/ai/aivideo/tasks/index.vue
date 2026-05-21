<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="项目ID" prop="projectId">
          <el-input v-model="queryParams.projectId" placeholder="项目ID" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-input v-model="queryParams.taskType" placeholder="如 VIDEO" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务状态" prop="taskStatus">
          <el-select v-model="queryParams.taskStatus" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in aivideoTaskStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>短剧生成任务</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList">
        <el-table-column label="任务ID" prop="taskId" width="100" />
        <el-table-column label="项目ID" prop="projectId" width="100" />
        <el-table-column label="类型" prop="taskType" width="130" />
        <el-table-column label="业务对象" min-width="150">
          <template #default="{ row }">{{ row.bizType || '-' }} / {{ row.bizId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getTaskTag(row.taskStatus)">{{ getTaskStatusLabel(row.taskStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="170">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" />
          </template>
        </el-table-column>
        <el-table-column label="成本" prop="actualCost" width="100" />
        <el-table-column label="更新时间" prop="updateTime" min-width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="router.push(`/ai/aivideo/tasks/${row.taskId}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { aivideoTaskStatusOptions, listAivideoTask, type AivideoTask, type AivideoTaskQuery } from '@/api/aivideo'

const router = useRouter()
const loading = ref(false)
const total = ref(0)
const taskList = ref<AivideoTask[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive<AivideoTaskQuery>({
  pageNum: 1,
  pageSize: 10
})

function getTaskStatusLabel(value?: string) {
  return aivideoTaskStatusOptions.find((item) => item.value === value)?.label || value || '待执行'
}

function getTaskTag(value?: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'RUNNING') return 'warning'
  return 'info'
}

async function getList() {
  loading.value = true
  try {
    const res = await listAivideoTask(queryParams)
    taskList.value = res.data.rows || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
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
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
