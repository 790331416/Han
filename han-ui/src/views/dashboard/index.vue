<template>
  <div class="dashboard">
    <!-- 欢迎区 -->
    <div class="welcome-section">
      <div>
        <h2 class="welcome-title">{{ greeting }}，{{ userStore.nickname || userStore.username }} 👋</h2>
        <p class="welcome-desc">欢迎回到 HAN Cloud 管理平台</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" v-for="item in statItems" :key="item.label">
        <div class="stat-icon-wrap" :style="{ background: item.bg }">
          <el-icon :size="22" :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <span class="stat-value">{{ item.value }}</span>
          <span class="stat-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 下方区域 -->
    <div class="bottom-grid">
      <div class="info-card">
        <div class="card-title">系统信息</div>
        <div class="info-list">
          <div class="info-row" v-for="info in sysInfo" :key="info.label">
            <span class="info-label">{{ info.label }}</span>
            <span class="info-value">{{ info.value }}</span>
          </div>
        </div>
      </div>

      <div class="info-card">
        <div class="card-title">快捷入口</div>
        <div class="shortcut-grid">
          <div class="shortcut-item" v-for="s in shortcuts" :key="s.label" @click="router.push(s.path)">
            <div class="shortcut-icon" :style="{ background: s.bg }">
              <el-icon :size="20" :style="{ color: s.color }"><component :is="s.icon" /></el-icon>
            </div>
            <span class="shortcut-label">{{ s.label }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, OfficeBuilding, UserFilled, Document, Setting, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { get } from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const stats = reactive({ userCount: 0, deptCount: 0, roleCount: 0, postCount: 0, onlineCount: 0 })

const statItems = computed(() => [
  { label: '用户总数', value: stats.userCount, icon: User, bg: '#eff6ff', color: '#2563eb' },
  { label: '部门数量', value: stats.deptCount, icon: OfficeBuilding, bg: '#f0fdf4', color: '#16a34a' },
  { label: '在线用户', value: stats.onlineCount, icon: UserFilled, bg: '#fefce8', color: '#ca8a04' },
  { label: '岗位数量', value: stats.postCount, icon: Document, bg: '#fef2f2', color: '#dc2626' },
])

const sysInfo = [
  { label: '系统名称', value: 'HAN Cloud' },
  { label: '系统版本', value: 'v1.0.0' },
  { label: 'Spring Boot', value: '4.0.2' },
  { label: 'Spring Cloud', value: '2025.1.0' },
  { label: 'Vue', value: '3.5.x' },
  { label: 'Element Plus', value: '2.9.x' },
]

const shortcuts = [
  { label: '用户管理', path: '/system/user', icon: User, bg: '#eff6ff', color: '#2563eb' },
  { label: '角色管理', path: '/system/role', icon: Setting, bg: '#fefce8', color: '#ca8a04' },
  { label: '部门管理', path: '/system/dept', icon: OfficeBuilding, bg: '#f0fdf4', color: '#16a34a' },
  { label: '菜单管理', path: '/system/menu', icon: Key, bg: '#fdf4ff', color: '#a855f7' },
  { label: '字典管理', path: '/system/dict', icon: Document, bg: '#fff7ed', color: '#ea580c' },
  { label: '任务调度', path: '/job', icon: Setting, bg: '#f0f9ff', color: '#0284c7' },
]

onMounted(async () => {
  try {
    const res = await get<any>('/system/dashboard/stats')
    if ((res as any)?.data) Object.assign(stats, (res as any).data)
  } catch { /* 接口不可用保持默认 */ }
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 24px;
  max-width: 1200px;
}

.welcome-section {
  margin-bottom: 24px;
}
.welcome-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.02em;
  margin-bottom: 4px;
}
.welcome-desc {
  font-size: 14px;
  color: #9ca3af;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: box-shadow 0.2s ease;
  &:hover { box-shadow: 0 4px 12px rgb(0 0 0 / 0.05); }
}
.stat-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-body {
  display: flex;
  flex-direction: column;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #9ca3af;
  margin-top: 2px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.info-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 24px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 16px;
}
.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f9fafb;
  &:last-child { border-bottom: none; }
}
.info-label {
  font-size: 14px;
  color: #6b7280;
}
.info-value {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
  &:hover { background: #f9fafb; }
}
.shortcut-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.shortcut-label {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
}
</style>
