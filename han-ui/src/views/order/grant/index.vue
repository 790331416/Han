<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="订购单ID">
          <el-input v-model="query.orderId" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="授权状态">
          <el-select v-model="query.grantStatus" clearable style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="warning" @click="showFailures">只看失败</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>授权台账</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="records">
        <el-table-column label="课程" prop="courseName" min-width="180" show-overflow-tooltip />
        <el-table-column label="课程ID" prop="courseId" min-width="180" show-overflow-tooltip />
        <el-table-column label="上课时间" prop="courseBeginTime" min-width="170" />
        <el-table-column label="听讲班" prop="listenClassId" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.grantStatus)">{{ statusLabel(row.grantStatus) }}</el-tag>
            <el-tag v-if="row.suspendedFlag === 1" type="warning" class="suspended">已挂起</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="听课记录ID" prop="attendId" min-width="180" show-overflow-tooltip />
        <el-table-column label="重试次数" prop="attemptCount" width="100" align="center" />
        <el-table-column label="最近失败原因" prop="lastError" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.grantStatus === 'FAILED' && userStore.hasPermission('order:grant:retry')"
              type="primary"
              link
              @click="handleRetry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  listGrants,
  retryGrant,
  type CourseOrderGrant,
  type GrantQuery,
  type GrantStatus
} from '@/api/order'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const route = useRoute()

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

const statusOptions: Array<{ value: GrantStatus; label: string }> = [
  { value: 'PENDING', label: '待物化' },
  { value: 'MATERIALIZED', label: '已物化' },
  { value: 'REVOKED', label: '已撤销' },
  { value: 'FAILED', label: '失败' }
]

const loading = ref(false)
const records = ref<CourseOrderGrant[]>([])
const total = ref(0)
const query = reactive<GrantQuery>({ grantStatus: '', pageNum: 1, pageSize: 20 })

onMounted(() => {
  const orderId = route.query.orderId
  if (typeof orderId === 'string' && orderId) query.orderId = orderId
  getList()
})

function statusLabel(value?: GrantStatus) {
  return statusOptions.find(item => item.value === value)?.label || '未知'
}

function statusTagType(value?: GrantStatus): TagType {
  if (value === 'MATERIALIZED') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'REVOKED') return 'info'
  return 'primary'
}

async function getList() {
  loading.value = true
  try {
    const response = await listGrants(query)
    records.value = response.data?.rows || []
    total.value = response.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  query.pageNum = 1
  getList()
}

function resetQuery() {
  query.orderId = undefined
  query.grantStatus = ''
  handleQuery()
}

function showFailures() {
  query.grantStatus = 'FAILED'
  handleQuery()
}

async function handleRetry(row: CourseOrderGrant) {
  if (!row.id) return
  const result = (await retryGrant(row.id)).data
  if (result && result.failed > 0) ElMessage.warning('重试仍然失败，请查看失败原因')
  else ElMessage.success('重试完成')
  await getList()
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.suspended { margin-left: 4px; }
</style>
