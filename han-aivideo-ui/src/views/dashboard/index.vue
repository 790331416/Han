<template>
  <div class="dashboard" data-testid="dashboard-page">
    <!-- 欢迎区 -->
    <div class="welcome-section">
      <div>
        <h2 class="welcome-title">{{ greeting }}，{{ userStore.nickname || userStore.username }} 👋</h2>
        <p class="welcome-desc">欢迎回到 HAN Cloud 管理平台</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid" v-if="visibleStats.length">
      <div class="stat-card" v-for="item in visibleStats" :key="item.label" @click="item.path && router.push(item.path)">
        <div class="stat-icon-wrap" :style="{ background: item.bg }">
          <el-icon :size="22" :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <span class="stat-value">{{ item.value }}</span>
          <span class="stat-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="chart-grid" v-if="chartData.loginTrend || chartData.operModules">
      <div class="info-card" v-if="chartData.loginTrend">
        <div class="card-title">近7日登录趋势</div>
        <div ref="loginChartRef" class="chart-container"></div>
      </div>
      <div class="info-card" v-if="chartData.operModules">
        <div class="card-title">操作模块分布（近30日）</div>
        <div ref="operChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 中间区域：快捷入口 + 系统信息 -->
    <div class="middle-grid">
      <div class="info-card" v-if="sortedShortcuts.length" data-testid="dashboard-shortcuts">
        <div class="card-title">快捷入口</div>
        <div class="shortcut-grid">
          <div class="shortcut-item" v-for="s in sortedShortcuts" :key="s.path" @click="handleShortcutClick(s.path)">
            <div class="shortcut-icon" :style="{ background: s.bg }">
              <el-icon :size="20" :style="{ color: s.color }"><component :is="s.icon" /></el-icon>
            </div>
            <span class="shortcut-label">{{ s.label }}</span>
          </div>
        </div>
      </div>

      <div class="info-card">
        <div class="card-title">系统信息</div>
        <div class="info-list">
          <div class="info-row" v-for="info in sysInfo" :key="info.label">
            <span class="info-label">{{ info.label }}</span>
            <span class="info-value">{{ info.value }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部区域：最近日志 -->
    <div class="bottom-grid" v-if="stats.recentLogins || stats.recentOperLogs">
      <div class="info-card" v-if="stats.recentLogins">
        <div class="card-title">最近登录</div>
        <el-table :data="stats.recentLogins" size="small" :show-header="true" max-height="260">
          <el-table-column label="用户" prop="username" min-width="80" />
          <el-table-column label="IP" prop="ipAddr" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" prop="loginTime" min-width="160" />
        </el-table>
      </div>

      <div class="info-card" v-if="stats.recentOperLogs">
        <div class="card-title">最近操作</div>
        <el-table :data="stats.recentOperLogs" size="small" :show-header="true" max-height="260">
          <el-table-column label="模块" prop="module" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作人" prop="operName" min-width="80" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" prop="operTime" min-width="160" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  User, OfficeBuilding, UserFilled, Document, Setting, Key,
  Connection, Notebook, Bell, Timer, Postcard, Menu as MenuIcon
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { get } from '@/utils/request'

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

interface StatsData {
  userCount: number | null
  roleCount: number | null
  deptCount: number | null
  postCount: number | null
  onlineCount: number | null
  dictCount: number | null
  noticeCount: number | null
  jobCount: number | null
  recentLogins: any[] | null
  recentOperLogs: any[] | null
  springBootVersion: string | null
}

const stats = reactive<StatsData>({
  userCount: null, roleCount: null, deptCount: null, postCount: null,
  onlineCount: null, dictCount: null, noticeCount: null, jobCount: null,
  recentLogins: null, recentOperLogs: null, springBootVersion: null
})

const allStatItems = computed(() => [
  { key: 'userCount', label: '用户总数', value: stats.userCount, icon: User, bg: '#eff6ff', color: '#2563eb', path: '/system/user' },
  { key: 'roleCount', label: '角色数量', value: stats.roleCount, icon: UserFilled, bg: '#fdf4ff', color: '#a855f7', path: '/system/role' },
  { key: 'deptCount', label: '部门数量', value: stats.deptCount, icon: OfficeBuilding, bg: '#f0fdf4', color: '#16a34a', path: '/system/dept' },
  { key: 'postCount', label: '岗位数量', value: stats.postCount, icon: Postcard, bg: '#fef2f2', color: '#dc2626', path: '/system/post' },
  { key: 'onlineCount', label: '在线用户', value: stats.onlineCount, icon: Connection, bg: '#fefce8', color: '#ca8a04', path: '/system/online' },
  { key: 'dictCount', label: '字典类型', value: stats.dictCount, icon: Notebook, bg: '#fff7ed', color: '#ea580c', path: '/system/dict' },
  { key: 'noticeCount', label: '通知公告', value: stats.noticeCount, icon: Bell, bg: '#f0f9ff', color: '#0284c7', path: '/system/notice' },
])

const visibleStats = computed(() => allStatItems.value.filter(item => item.value !== null))

const allShortcuts = [
  { label: '用户管理', path: '/system/user', icon: User, bg: '#eff6ff', color: '#2563eb', perm: 'system:user:list' },
  { label: '角色管理', path: '/system/role', icon: UserFilled, bg: '#fdf4ff', color: '#a855f7', perm: 'system:role:list' },
  { label: '部门管理', path: '/system/dept', icon: OfficeBuilding, bg: '#f0fdf4', color: '#16a34a', perm: 'system:dept:list' },
  { label: '菜单管理', path: '/system/menu', icon: MenuIcon, bg: '#fefce8', color: '#ca8a04', perm: 'system:menu:list' },
  { label: '岗位管理', path: '/system/post', icon: Postcard, bg: '#fef2f2', color: '#dc2626', perm: 'system:post:list' },
  { label: '字典管理', path: '/system/dict', icon: Notebook, bg: '#fff7ed', color: '#ea580c', perm: 'system:dict:list' },
  { label: '通知公告', path: '/system/notice', icon: Bell, bg: '#f0f9ff', color: '#0284c7', perm: 'system:notice:list' },
  { label: '参数配置', path: '/system/config', icon: Setting, bg: '#f5f3ff', color: '#7c3aed', perm: 'system:config:list' },
  { label: '任务调度', path: '/job', icon: Timer, bg: '#ecfdf5', color: '#059669', perm: 'job:list' },
  { label: '操作日志', path: '/system/operlog', icon: Document, bg: '#fef3c7', color: '#d97706', perm: 'system:operlog:list' },
  { label: '在线用户', path: '/system/online', icon: Connection, bg: '#e0f2fe', color: '#0369a1', perm: 'monitor:online:list' },
  { label: '系统监控', path: '/system/server', icon: Key, bg: '#fce7f3', color: '#db2777', perm: 'system:monitor:server' },
]

const sortedShortcuts = computed(() => {
  const filtered = allShortcuts.filter(s => userStore.hasPermission(s.perm))
  return [...filtered].sort((a, b) => {
    const ca = appStore.getShortcutClickCount(a.path)
    const cb = appStore.getShortcutClickCount(b.path)
    return cb - ca
  })
})

function handleShortcutClick(path: string) {
  appStore.recordShortcutClick(path)
  router.push(path)
}

// ==================== 图表 ====================
const loginChartRef = ref<HTMLElement>()
const operChartRef = ref<HTMLElement>()
let loginChart: echarts.ECharts | null = null
let operChart: echarts.ECharts | null = null

interface ChartData {
  loginTrend: { dates: string[]; success: number[]; fail: number[] } | null
  operModules: { name: string; value: number }[] | null
}

const chartData = reactive<ChartData>({ loginTrend: null, operModules: null })

function renderLoginChart() {
  if (!loginChartRef.value || !chartData.loginTrend) return
  loginChart = echarts.init(loginChartRef.value)
  loginChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败'], bottom: 0 },
    grid: { top: 16, left: 40, right: 16, bottom: 40 },
    xAxis: { type: 'category', data: chartData.loginTrend.dates, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '成功', type: 'line', data: chartData.loginTrend.success, smooth: true, itemStyle: { color: '#2563eb' }, areaStyle: { color: 'rgba(37,99,235,0.08)' } },
      { name: '失败', type: 'line', data: chartData.loginTrend.fail, smooth: true, itemStyle: { color: '#ef4444' }, areaStyle: { color: 'rgba(239,68,68,0.08)' } }
    ]
  })
}

function renderOperChart() {
  if (!operChartRef.value || !chartData.operModules || chartData.operModules.length === 0) return
  operChart = echarts.init(operChartRef.value)
  const names = chartData.operModules.map(m => m.name)
  const values = chartData.operModules.map(m => m.value)
  const reversedNames = [...names].reverse()
  const reversedValues = [...values].reverse()
  operChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 16, left: 80, right: 16, bottom: 8 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: reversedNames, axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', data: reversedValues, barWidth: 16,
      itemStyle: { color: '#2563eb', borderRadius: [0, 4, 4, 0] }
    }]
  })
}

function handleResize() {
  loginChart?.resize()
  operChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  loginChart?.dispose()
  operChart?.dispose()
})

// Spring Boot 版本由后端运行时下发，避免硬编码漂移（历史：前端写死 4.0.2，实际 4.1.0）
const sysInfo = computed(() => [
  { label: '系统名称', value: 'HAN Cloud' },
  { label: '系统版本', value: 'v1.0.0' },
  { label: 'Spring Boot', value: stats.springBootVersion || '-' },
  { label: 'Spring Cloud', value: '2025.1.2' },
  { label: 'Vue', value: '3.5.x' },
  { label: 'Element Plus', value: '2.9.x' },
])

onMounted(async () => {
  try {
    const res = await get<any>('/system/dashboard/stats')
    if ((res as any)?.data) Object.assign(stats, (res as any).data)
  } catch { /* 接口不可用保持默认 */ }

  // 加载图表数据
  try {
    // 图表数据属于增强信息，接口缺失时首页应静默降级而不是打断用户。
    const chartRes = await get<any>('/system/dashboard/charts', undefined, { silentError: true })
    const d = (chartRes as any)?.data
    if (d?.loginTrend) chartData.loginTrend = d.loginTrend
    if (d?.operModules) chartData.operModules = d.operModules
    await nextTick()
    renderLoginChart()
    renderOperChart()
    window.addEventListener('resize', handleResize)
  } catch { /* 图表数据不可用 */ }
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 24px;
  max-width: 1400px;
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
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
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
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.15s ease;
  &:hover {
    box-shadow: 0 4px 12px rgb(0 0 0 / 0.05);
    transform: translateY(-1px);
  }
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

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.chart-container {
  width: 100%;
  height: 280px;
}

.middle-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
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
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
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
  text-align: center;
}

@media (max-width: 1024px) {
  .chart-grid,
  .middle-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: 16px;
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

// Dark mode
html.dark {
  .welcome-title { color: #f9fafb; }
  .welcome-desc { color: #6b7280; }
  .stat-card {
    background: #1f2937; border-color: #374151;
    &:hover { box-shadow: 0 4px 12px rgb(0 0 0 / 0.3); }
  }
  .stat-value { color: #f9fafb; }
  .stat-label { color: #6b7280; }
  .info-card { background: #1f2937; border-color: #374151; }
  .card-title { color: #f9fafb; }
  .info-row { border-bottom-color: #374151; }
  .info-label { color: #9ca3af; }
  .info-value { color: #f9fafb; }
  .shortcut-item:hover { background: #374151; }
  .shortcut-label { color: #e5e7eb; }
}
</style>
