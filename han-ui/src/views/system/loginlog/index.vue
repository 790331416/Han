<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="登录IP" prop="ipaddr">
          <el-input v-model="queryParams.ipaddr" placeholder="请输入登录IP" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="登录状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable>
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
          <span>登录日志</span>
          <div class="table-operations">
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
            <el-button type="danger" :icon="Delete" @click="handleClean">清空</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="日志ID" prop="id" width="80" />
        <el-table-column label="用户名" prop="username" width="120" />
        <el-table-column label="登录IP" prop="ipaddr" width="140" />
        <el-table-column label="登录地点" prop="loginLocation" width="150" />
        <el-table-column label="浏览器" prop="browser" width="120" />
        <el-table-column label="操作系统" prop="os" width="120" />
        <el-table-column label="登录状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提示消息" prop="msg" width="200" show-overflow-tooltip />
        <el-table-column label="登录时间" prop="loginTime" width="180" />
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
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { listLoginLog, deleteLoginLog, cleanLoginLog } from '@/api/system/loginlog'
import type { LoginLog } from '@/api/system/loginlog'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const logList = ref<LoginLog[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  username: undefined as string | undefined,
  status: undefined as number | undefined,
  ipaddr: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await listLoginLog(queryParams)
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

const handleSelectionChange = (selection: LoginLog[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}条日志吗?`, '提示', { type: 'warning' })
  await deleteLoginLog(selectedIds.value)
  ElMessage.success('删除成功')
  getList()
}

const handleClean = async () => {
  await ElMessageBox.confirm('确定清空所有登录日志吗?', '提示', { type: 'warning' })
  await cleanLoginLog()
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
</style>
