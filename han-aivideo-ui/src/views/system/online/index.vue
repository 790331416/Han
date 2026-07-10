<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="登录IP">
          <el-input v-model="queryParams.ipAddr" placeholder="请输入IP地址" clearable @keyup.enter="getList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="getList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>在线用户列表</span>
          <el-tag type="success" effect="plain">当前在线：{{ onlineList.length }} 人</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="onlineList">
        <el-table-column label="用户名" prop="username" min-width="120" show-overflow-tooltip />
        <el-table-column label="昵称" prop="nickname" min-width="120" show-overflow-tooltip />
        <el-table-column label="登录IP" prop="ipAddr" min-width="150" show-overflow-tooltip />
        <el-table-column label="客户端" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="clientTypeTag(row.clientType)">{{ clientTypeText(row.clientType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录时间" min-width="180">
          <template #default="{ row }">
            {{ formatLoginTime(row.loginTime) }}
          </template>
        </el-table-column>
        <el-table-column label="在线时长" min-width="120">
          <template #default="{ row }">
            {{ formatDuration(row.loginTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button type="danger" link :icon="SwitchButton" @click="handleForceLogout(row)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { listOnlineUser, forceLogout, type OnlineUser } from '@/api/system/online'

const loading = ref(false)
const onlineList = ref<OnlineUser[]>([])
let refreshTimer: ReturnType<typeof setInterval> | null = null

const queryParams = reactive({
  username: '',
  ipAddr: ''
})

async function getList() {
  loading.value = true
  try {
    const query: Record<string, string> = {}
    if (queryParams.username) query.username = queryParams.username
    if (queryParams.ipAddr) query.ipAddr = queryParams.ipAddr
    const res = await listOnlineUser(Object.keys(query).length ? query : undefined)
    onlineList.value = (res as any).data || []
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.username = ''
  queryParams.ipAddr = ''
  getList()
}

async function handleForceLogout(row: OnlineUser) {
  try {
    await ElMessageBox.confirm(`确认强制下线用户"${row.username}"？`, '提示', { type: 'warning' })
    await forceLogout(row.tokenId)
    ElMessage.success('已强制下线')
    getList()
  } catch { /* cancel */ }
}

function clientTypeText(type: string) {
  const map: Record<string, string> = { PC: 'PC', APP: 'APP', H5: 'H5', WECHAT_MP: '小程序', WECHAT_OA: '公众号' }
  return map[type] || type || 'PC'
}

function clientTypeTag(type: string) {
  const map: Record<string, string> = { PC: '', APP: 'success', H5: 'warning', WECHAT_MP: 'primary', WECHAT_OA: 'primary' }
  return (map[type] || 'info') as any
}

function formatLoginTime(ts: string | number) {
  if (!ts) return '—'
  const d = new Date(Number(ts))
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDuration(ts: string | number) {
  if (!ts) return '—'
  const diff = Math.floor((Date.now() - Number(ts)) / 1000)
  if (diff < 60) return `${diff}秒`
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟`
  const h = Math.floor(diff / 3600)
  const m = Math.floor((diff % 3600) / 60)
  return `${h}小时${m}分`
}

onMounted(() => {
  getList()
  refreshTimer = setInterval(getList, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>
