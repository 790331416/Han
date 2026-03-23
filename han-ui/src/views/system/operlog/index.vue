<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="系统模块" prop="module">
          <el-input v-model="queryParams.module" placeholder="请输入模块名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="操作人员" prop="operName">
          <el-input v-model="queryParams.operName" placeholder="请输入操作人员" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="操作类型" prop="operType">
          <el-select v-model="queryParams.operType">
            <el-option label="全部" value="" />
            <el-option label="其他" :value="0" />
            <el-option label="新增" :value="1" />
            <el-option label="修改" :value="2" />
            <el-option label="删除" :value="3" />
            <el-option label="查询" :value="4" />
            <el-option label="导出" :value="5" />
            <el-option label="导入" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作状态" prop="status">
          <el-select v-model="queryParams.status">
            <el-option label="全部" value="" />
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>操作日志</span>
          <div class="table-operations">
            <el-button type="warning" :icon="Download" @click="handleExport">导出</el-button>
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
            <el-button type="danger" :icon="Delete" @click="handleClean">清空</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="系统模块" prop="module" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getBusinessTypeTag(row.operType)">{{ getBusinessTypeText(row.operType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人员" prop="operName" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作IP" prop="operIp" min-width="140" show-overflow-tooltip />
        <el-table-column label="归属地" prop="operLocation" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" prop="costTime" width="100" align="center" />
        <el-table-column label="操作时间" prop="operTime" min-width="180" />
        <el-table-column label="操作" min-width="80">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleDetail(row)">详情</el-button>
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

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="65%" class="dialog-lg" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="系统模块">{{ detailData.module }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getBusinessTypeTag(detailData.operType)">{{ getBusinessTypeText(detailData.operType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人员">{{ detailData.operName }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detailData.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ detailData.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ detailData.operIp }}</el-descriptions-item>
        <el-descriptions-item label="归属地">{{ detailData.operLocation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作状态">
          <el-tag :type="detailData.status === 0 ? 'success' : 'danger'">{{ detailData.status === 0 ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detailData.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detailData.operTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2" v-if="detailData.operParam">
          <div class="detail-text">{{ detailData.operParam }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="返回参数" :span="2" v-if="detailData.jsonResult">
          <div class="detail-text">{{ detailData.jsonResult }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="错误消息" :span="2" v-if="detailData.errorMsg">
          <div class="detail-text error-text">{{ detailData.errorMsg }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, Download } from '@element-plus/icons-vue'
import { listOperLog, deleteOperLog, cleanOperLog } from '@/api/system/operlog'
import { downloadExcel } from '@/utils/download'
import type { OperLog } from '@/api/system/operlog'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const logList = ref<OperLog[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const detailVisible = ref(false)
const detailData = ref<OperLog>({} as OperLog)

const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  module: undefined as string | undefined,
  operName: undefined as string | undefined,
  operType: '' as any,
  status: '' as any
})

const businessTypeMap: Record<number, string> = {
  0: '其他', 1: '新增', 2: '修改', 3: '删除', 4: '查询', 5: '导出', 6: '导入', 7: '授权', 8: '强退', 9: '清空'
}

const businessTypeTagMap: Record<number, string> = {
  0: 'info', 1: 'success', 2: 'primary', 3: 'danger', 4: '', 5: 'warning', 6: 'warning', 7: 'primary', 8: 'danger', 9: 'danger'
}

const getBusinessTypeText = (type: number) => businessTypeMap[type] || '其他'
const getBusinessTypeTag = (type: number) => (businessTypeTagMap[type] || 'info') as any

const getList = async () => {
  loading.value = true
  try {
    const res = await listOperLog(queryParams)
    logList.value = res.data.rows
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

const handleSelectionChange = (selection: OperLog[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleDetail = (row: OperLog) => {
  detailData.value = row
  detailVisible.value = true
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}条日志吗?`, '提示', { type: 'warning' })
  await deleteOperLog(selectedIds.value)
  ElMessage.success('删除成功')
  getList()
}

const handleExport = () => {
  downloadExcel('/system/operlog/export', queryParams, '操作日志')
}

const handleClean = async () => {
  await ElMessageBox.confirm('确定清空所有操作日志吗?', '提示', { type: 'warning' })
  await cleanOperLog()
  ElMessage.success('清空成功')
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
.detail-text {
  max-height: 200px;
  overflow-y: auto;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
.error-text {
  color: #f56c6c;
}
</style>
