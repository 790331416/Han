<template>
  <div class="app-container" data-testid="client-user-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" inline @submit.prevent="handleQuery">
        <el-form-item label="用户名"><el-input v-model="queryParams.username" clearable placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="queryParams.phone" clearable placeholder="请输入手机号" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" native-type="submit">搜索</el-button><el-button :icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><div class="card-header"><span>客户端用户管理</span><el-tag type="info">仅支持查询和解除人员绑定</el-tag></div></template>
      <el-table v-loading="loading" :data="userList" empty-text="暂无客户端用户">
        <el-table-column label="用户名" prop="username" min-width="140" show-overflow-tooltip />
        <el-table-column label="昵称" prop="nickname" min-width="140" show-overflow-tooltip />
        <el-table-column label="手机号" prop="phone" min-width="140" />
        <el-table-column label="状态" width="100" align="center"><template #default="{ row }"><el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatDate(row.createTime) }}</template></el-table-column>
        <el-table-column v-if="canUnbind" label="操作" width="120" fixed="right"><template #default="{ row }"><el-button type="warning" link :icon="Connection" @click="handleUnbind(row)">解除绑定</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-container"><el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="getList" @current-change="getList" /></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Refresh, Search } from '@element-plus/icons-vue'
import { listClientUser, type User, type UserQuery } from '@/api/system/user'
import { unbindClientUser } from '@/api/education'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/request'

const userStore = useUserStore()
const canList = computed(() => userStore.hasPermission('system:client-user:list'))
const canUnbind = computed(() => userStore.hasPermission('system:client-user:unbind'))
const loading = ref(false)
const userList = ref<User[]>([])
const total = ref(0)
const queryParams = reactive<Omit<UserQuery, 'accountType'>>({ pageNum: 1, pageSize: 10, username: undefined, phone: undefined, status: undefined })

async function getList() {
  if (!canList.value) return
  loading.value = true
  try {
    const data = (await listClientUser(queryParams)).data
    userList.value = data.rows || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { queryParams.username = undefined; queryParams.phone = undefined; queryParams.status = undefined; handleQuery() }

async function handleUnbind(row: User) {
  await ElMessageBox.confirm(
    `确认解除“${row.username}”与教育人员的绑定吗？\n\n解除后仅解除该账号的教育身份绑定，人员信息不会删除；若该账号关联多个学校身份，需在人员管理中按学校身份逐条解绑。`,
    '确认解绑',
    { type: 'warning' }
  )
  await unbindClientUser(row.userId)
  ElMessage.success('已解绑')
  await getList()
}

onMounted(getList)
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination-container { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
