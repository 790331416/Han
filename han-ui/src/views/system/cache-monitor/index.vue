<template>
  <div class="app-container">
    <el-button :icon="Refresh" @click="refresh" style="margin-bottom: 16px">刷新</el-button>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header><span>Redis 基本信息</span></template>
          <el-descriptions :column="3" border v-if="cacheInfo">
            <el-descriptions-item label="Redis版本">{{ cacheInfo.redisVersion }}</el-descriptions-item>
            <el-descriptions-item label="运行天数">{{ cacheInfo.uptimeInDays }} 天</el-descriptions-item>
            <el-descriptions-item label="连接客户端数">{{ cacheInfo.connectedClients }}</el-descriptions-item>
            <el-descriptions-item label="已用内存">{{ cacheInfo.usedMemory }}</el-descriptions-item>
            <el-descriptions-item label="内存峰值">{{ cacheInfo.usedMemoryPeak }}</el-descriptions-item>
            <el-descriptions-item label="最大内存">{{ cacheInfo.maxMemory }}</el-descriptions-item>
            <el-descriptions-item label="总命令数">{{ cacheInfo.totalCommandsProcessed }}</el-descriptions-item>
            <el-descriptions-item label="每秒操作数">{{ cacheInfo.instantaneousOpsPerSec }}</el-descriptions-item>
            <el-descriptions-item label="键总数">{{ cacheInfo.dbSize }}</el-descriptions-item>
            <el-descriptions-item label="命中次数">{{ cacheInfo.keyspaceHits }}</el-descriptions-item>
            <el-descriptions-item label="未命中次数">{{ cacheInfo.keyspaceMisses }}</el-descriptions-item>
            <el-descriptions-item label="命中率">{{ hitRate }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>缓存键列表</span>
          <el-input v-model="keyPattern" placeholder="匹配模式" style="width: 300px" @keyup.enter="getKeys">
            <template #append>
              <el-button :icon="Search" @click="getKeys" />
            </template>
          </el-input>
        </div>
      </template>
      <el-table :data="keyList" v-loading="keyLoading" max-height="400">
        <el-table-column label="键名" prop="key" show-overflow-tooltip />
        <el-table-column label="过期时间(秒)" prop="ttl" width="140">
          <template #default="{ row }">
            {{ row.ttl === -1 ? '永不过期' : row.ttl === -2 ? '已过期' : row.ttl + 's' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPermission('monitor:cache:remove')" type="danger" link :icon="Delete" @click="handleDelete(row.key)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Delete } from '@element-plus/icons-vue'
import { getCacheInfo, getCacheKeys, deleteCache } from '@/api/system/monitor'
import { useUserStore } from '@/stores/user'

const cacheInfo = ref<any>(null)
const keyList = ref<any[]>([])
const keyLoading = ref(false)
const keyPattern = ref('han:*')
const userStore = useUserStore()

const hitRate = computed(() => {
  if (!cacheInfo.value) return '-'
  const hits = parseInt(cacheInfo.value.keyspaceHits || '0')
  const misses = parseInt(cacheInfo.value.keyspaceMisses || '0')
  const total = hits + misses
  return total === 0 ? '0%' : (hits / total * 100).toFixed(2) + '%'
})

const getData = async () => {
  try {
    const res = await getCacheInfo()
    cacheInfo.value = res.data || {}
  } catch { /* ignore */ }
}

const getKeys = async () => {
  keyLoading.value = true
  try {
    const res = await getCacheKeys(keyPattern.value)
    keyList.value = res.data || []
  } catch { /* ignore */ } finally {
    keyLoading.value = false
  }
}

const handleDelete = async (key: string) => {
  await ElMessageBox.confirm(`确定删除缓存键"${key}"吗?`, '提示', { type: 'warning' })
  await deleteCache(key)
  ElMessage.success('删除成功')
  getKeys()
}

const refresh = () => {
  getData()
  getKeys()
}

onMounted(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
</style>
