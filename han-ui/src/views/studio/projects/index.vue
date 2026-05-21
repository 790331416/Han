<template>
  <div class="studio-page">
    <section class="toolbar">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="projectStatus">
          <el-select v-model="queryParams.projectStatus" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in aivideoProjectStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段" prop="currentStage">
          <el-select v-model="queryParams.currentStage" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="item in aivideoProjectStageOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button type="primary" :icon="Plus" @click="router.push('/studio/projects/create')">新建项目</el-button>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="projectList" height="calc(100vh - 240px)">
        <el-table-column label="项目名称" prop="projectName" min-width="200" show-overflow-tooltip />
        <el-table-column label="题材" prop="topicType" width="120" />
        <el-table-column label="平台" prop="targetPlatform" width="120" />
        <el-table-column label="画幅" prop="defaultRatio" width="90" align="center" />
        <el-table-column label="阶段" min-width="140" align="center">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain">{{ getStageLabel(row.currentStage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.projectStatus)">{{ getStatusLabel(row.projectStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Operation" @click="openWorkbench(row)">工作台</el-button>
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Operation, Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import {
  aivideoProjectStageOptions,
  aivideoProjectStatusOptions,
  listAivideoProject,
  type AivideoProject,
  type AivideoProjectQuery
} from '@/api/aivideo'

const router = useRouter()
const loading = ref(false)
const total = ref(0)
const projectList = ref<AivideoProject[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive<AivideoProjectQuery>({
  pageNum: 1,
  pageSize: 10
})

function getStageLabel(value?: string) {
  return aivideoProjectStageOptions.find((item) => item.value === value)?.label || value || '草稿'
}

function getStatusLabel(value?: string) {
  return aivideoProjectStatusOptions.find((item) => item.value === value)?.label || value || '草稿'
}

function getStatusTag(value?: string) {
  if (value === 'FINISHED') return 'success'
  if (value === 'PAUSED' || value === 'ARCHIVED') return 'info'
  return 'warning'
}

async function getList() {
  loading.value = true
  try {
    const res = await listAivideoProject(queryParams)
    projectList.value = res.data.rows || []
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

function openWorkbench(row: AivideoProject) {
  router.push(`/studio/projects/${row.projectId}/workbench`)
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.studio-page {
  padding: 20px;
}

.toolbar,
.table-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 16px 0;
  margin-bottom: 16px;
}

.table-panel {
  padding: 16px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
