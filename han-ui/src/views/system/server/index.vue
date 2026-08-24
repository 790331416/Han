<template>
  <div class="app-container">
    <el-button v-if="canList || canJvm || canSystem" :icon="Refresh" @click="getServerData" style="margin-bottom: 16px">刷新</el-button>

    <el-row :gutter="16">
      <el-col v-if="canList || canJvm" :span="12">
        <el-card shadow="never">
          <template #header><span>JVM信息</span></template>
          <el-descriptions :column="1" border v-if="serverInfo.jvm">
            <el-descriptions-item label="Java版本">{{ serverInfo.jvm.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="Java路径">{{ serverInfo.jvm.javaHome }}</el-descriptions-item>
            <el-descriptions-item label="最大内存">{{ serverInfo.jvm.maxMemory }} MB</el-descriptions-item>
            <el-descriptions-item label="已分配内存">{{ serverInfo.jvm.totalMemory }} MB</el-descriptions-item>
            <el-descriptions-item label="已使用内存">{{ serverInfo.jvm.usedMemory }} MB</el-descriptions-item>
            <el-descriptions-item label="空闲内存">{{ serverInfo.jvm.freeMemory }} MB</el-descriptions-item>
            <el-descriptions-item label="堆已使用">{{ serverInfo.jvm.heapUsed }} MB</el-descriptions-item>
            <el-descriptions-item label="堆最大值">{{ serverInfo.jvm.heapMax }} MB</el-descriptions-item>
            <el-descriptions-item label="运行时间">{{ formatUptime(serverInfo.jvm.uptime) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col v-if="canList || canSystem" :span="12">
        <el-card shadow="never">
          <template #header><span>服务器信息</span></template>
          <el-descriptions :column="1" border v-if="serverInfo.sys">
            <el-descriptions-item label="操作系统">{{ serverInfo.sys.osName }}</el-descriptions-item>
            <el-descriptions-item label="系统架构">{{ serverInfo.sys.osArch }}</el-descriptions-item>
            <el-descriptions-item label="系统版本">{{ serverInfo.sys.osVersion }}</el-descriptions-item>
            <el-descriptions-item label="处理器数量">{{ serverInfo.sys.availableProcessors }}</el-descriptions-item>
            <el-descriptions-item label="系统负载">{{ serverInfo.sys.systemLoadAverage?.toFixed(2) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主机名">{{ serverInfo.sys.hostName }}</el-descriptions-item>
            <el-descriptions-item label="主机IP">{{ serverInfo.sys.hostAddress }}</el-descriptions-item>
            <el-descriptions-item label="工作目录">{{ serverInfo.sys.userDir }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getServerInfo, getServerJvmInfo, getServerSystemInfo } from '@/api/system/monitor'
import { useUserStore } from '@/stores/user'

const serverInfo = ref<any>({})
const userStore = useUserStore()
const canList = computed(() => userStore.hasPermission('monitor:server:list'))
const canJvm = computed(() => userStore.hasPermission('monitor:server:jvm'))
const canSystem = computed(() => userStore.hasPermission('monitor:server:system'))

const getServerData = async () => {
  try {
    if (canList.value) {
      const res = await getServerInfo()
      serverInfo.value = res.data || {}
      return
    }
    const [jvm, sys] = await Promise.all([
      canJvm.value ? getServerJvmInfo() : Promise.resolve({ data: undefined }),
      canSystem.value ? getServerSystemInfo() : Promise.resolve({ data: undefined })
    ])
    serverInfo.value = { jvm: jvm.data, sys: sys.data }
  } catch { /* ignore */ }
}

const formatUptime = (seconds?: number) => {
  if (!seconds) return '-'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${d}天${h}小时${m}分钟`
}

onMounted(() => { if (canList.value || canJvm.value || canSystem.value) getServerData() })
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
</style>
