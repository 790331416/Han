<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务组名" prop="jobGroup">
          <el-select v-model="queryParams.jobGroup" placeholder="请选择" clearable>
            <el-option v-for="item in jobGroupOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable>
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志列表</span>
          <div class="table-operations">
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
            <el-button type="danger" :icon="Delete" @click="handleClean">清空</el-button>
            <el-button type="info" :icon="Back" @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务名称" prop="jobName" min-width="150" show-overflow-tooltip />
        <el-table-column label="任务组" prop="jobGroup" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.jobGroup === 'DEFAULT' ? undefined : 'warning'">
              {{ getJobGroupLabel(row.jobGroup) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调用目标" prop="invokeTarget" min-width="200" show-overflow-tooltip />
        <el-table-column label="日志信息" prop="jobMessage" min-width="150" show-overflow-tooltip />
        <el-table-column label="执行状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" prop="costTime" width="100" align="center" />
        <el-table-column label="开始时间" prop="startTime" min-width="180" />
        <el-table-column label="操作" min-width="100">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="65%" class="dialog-lg">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日志ID">{{ detailData.jobLogId }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ detailData.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务组名">{{ getJobGroupLabel(detailData.jobGroup) }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="detailData.status === '0' ? 'success' : 'danger'">
            {{ detailData.status === '0' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="调用目标" :span="2">{{ detailData.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="日志信息" :span="2">{{ detailData.jobMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detailData.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detailData.stopTime }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detailData.costTime }}ms</el-descriptions-item>
        <el-descriptions-item label="异常信息" :span="2" v-if="detailData.status === '1'">
          <div class="exception-info">{{ detailData.exceptionInfo || '-' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, Back } from '@element-plus/icons-vue'
import {
  listJobLog, getJobLog, deleteJobLogs, cleanJobLog,
  jobGroupOptions,
  type JobLog, type JobLogQuery
} from '@/api/job'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const logList = ref<JobLog[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const detailVisible = ref(false)
const dateRange = ref<string[]>([])

const queryFormRef = ref<FormInstance>()

const queryParams = reactive<JobLogQuery>({
  pageNum: 1,
  pageSize: 10,
  jobName: undefined,
  jobGroup: undefined,
  status: undefined,
  startTime: undefined,
  endTime: undefined
})

const detailData = ref<JobLog>({} as JobLog)

// 获取任务组标签
const getJobGroupLabel = (value: string) => {
  return jobGroupOptions.find(item => item.value === value)?.label || value
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await listJobLog(queryParams)
    logList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

// 日期变化
const handleDateChange = (val: string[] | null) => {
  if (val && val.length === 2) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = undefined
    queryParams.endTime = undefined
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  dateRange.value = []
  queryParams.startTime = undefined
  queryParams.endTime = undefined
  handleQuery()
}

// 多选
const handleSelectionChange = (selection: JobLog[]) => {
  selectedIds.value = selection.map(item => item.jobLogId)
}

// 详情
const handleDetail = async (row: JobLog) => {
  const res = await getJobLog(row.jobLogId)
  detailData.value = res.data
  detailVisible.value = true
}

// 批量删除
const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}条日志吗?`, '提示', { type: 'warning' })
  await deleteJobLogs(selectedIds.value)
  ElMessage.success('删除成功')
  getList()
}

// 清空
const handleClean = async () => {
  await ElMessageBox.confirm('确定清空所有任务日志吗? 此操作不可恢复!', '警告', { type: 'warning' })
  await cleanJobLog()
  ElMessage.success('清空成功')
  getList()
}

// 返回
const handleBack = () => {
  router.push('/job')
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

.exception-info {
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: monospace;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  padding: 8px;
  border-radius: 4px;
}
</style>
