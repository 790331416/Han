<template>
  <div class="page-container p-5 space-y-4">
    <!-- 角色信息 -->
    <el-card shadow="never">
      <div class="flex items-center gap-3">
        <el-button :icon="Back" @click="goBack">返回</el-button>
        <span class="text-gray-800 font-medium">角色「{{ roleName }}」的用户分配</span>
      </div>
    </el-card>

    <!-- 搜索 -->
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 已分配用户列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex justify-between items-center">
          <span class="font-medium text-gray-800">已分配用户</span>
          <div class="flex gap-2">
            <el-button type="primary" :icon="Plus" @click="openAddDialog">添加用户</el-button>
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchCancel">批量取消授权</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="用户名" prop="username" width="150" />
        <el-table-column label="昵称" prop="nickname" width="150" />
        <el-table-column label="手机号" prop="phone" width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleCancel(row)">取消授权</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-4 justify-end"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 添加用户弹窗 -->
    <el-dialog v-model="addDialogVisible" title="选择用户" width="800px" destroy-on-close>
      <el-form :inline="true" :model="addQueryParams" class="mb-4">
        <el-form-item label="用户名">
          <el-input v-model="addQueryParams.username" placeholder="请输入" clearable @keyup.enter="getUnallocatedList" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="addQueryParams.phone" placeholder="请输入" clearable @keyup.enter="getUnallocatedList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="getUnallocatedList">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="addLoading" :data="unallocatedList" @selection-change="handleAddSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="用户名" prop="username" width="150" />
        <el-table-column label="昵称" prop="nickname" width="150" />
        <el-table-column label="手机号" prop="phone" width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="addQueryParams.pageNum"
        v-model:page-size="addQueryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="addTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-4 justify-end"
        @size-change="getUnallocatedList"
        @current-change="getUnallocatedList"
      />

      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!addSelectedIds.length" :loading="addSubmitting" @click="handleAddSubmit">
          确认添加 ({{ addSelectedIds.length }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Back } from '@element-plus/icons-vue'
import {
  listAllocatedUsers, listUnallocatedUsers, authUserSelectAll, authUserCancel,
  getRole, type AllocatedUser
} from '@/api/system/role'

const route = useRoute()
const router = useRouter()
const roleId = Number(route.query.roleId)
const roleName = ref('')

const loading = ref(false)
const userList = ref<AllocatedUser[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])

const queryParams = reactive({ roleId, username: '', phone: '', pageNum: 1, pageSize: 10 })

const addDialogVisible = ref(false)
const addLoading = ref(false)
const addSubmitting = ref(false)
const unallocatedList = ref<AllocatedUser[]>([])
const addTotal = ref(0)
const addSelectedIds = ref<(string | number)[]>([])
const addQueryParams = reactive({ roleId, username: '', phone: '', pageNum: 1, pageSize: 10 })

onMounted(async () => {
  if (!roleId) {
    ElMessage.error('缺少角色ID')
    return
  }
  try {
    const res = await getRole(roleId)
    roleName.value = (res as any).data?.roleName || ''
  } catch { /* ignore */ }
  getList()
})

async function getList() {
  loading.value = true
  try {
    const res = await listAllocatedUsers(queryParams)
    const data = (res as any).data
    userList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.username = ''; queryParams.phone = ''
  handleQuery()
}

function handleSelectionChange(selection: AllocatedUser[]) {
  selectedIds.value = selection.map(u => u.id)
}

async function handleCancel(row: AllocatedUser) {
  try {
    await ElMessageBox.confirm(`确认取消用户"${row.username}"的授权？`, '提示', { type: 'warning' })
    await authUserCancel(roleId, [row.id])
    ElMessage.success('取消授权成功')
    getList()
  } catch { /* cancel */ }
}

async function handleBatchCancel() {
  try {
    await ElMessageBox.confirm(`确认取消选中的${selectedIds.value.length}个用户的授权？`, '提示', { type: 'warning' })
    await authUserCancel(roleId, selectedIds.value)
    ElMessage.success('批量取消授权成功')
    getList()
  } catch { /* cancel */ }
}

async function openAddDialog() {
  addSelectedIds.value = []
  addQueryParams.username = ''
  addQueryParams.phone = ''
  addQueryParams.pageNum = 1
  addDialogVisible.value = true
  getUnallocatedList()
}

async function getUnallocatedList() {
  addLoading.value = true
  try {
    const res = await listUnallocatedUsers(addQueryParams)
    const data = (res as any).data
    unallocatedList.value = data?.records || data?.rows || []
    addTotal.value = data?.total || 0
  } catch { /* ignore */ } finally {
    addLoading.value = false
  }
}

function handleAddSelectionChange(selection: AllocatedUser[]) {
  addSelectedIds.value = selection.map(u => u.id)
}

async function handleAddSubmit() {
  addSubmitting.value = true
  try {
    await authUserSelectAll(roleId, addSelectedIds.value)
    ElMessage.success('授权成功')
    addDialogVisible.value = false
    getList()
  } finally {
    addSubmitting.value = false
  }
}

function goBack() {
  router.push('/system/role')
}
</script>
