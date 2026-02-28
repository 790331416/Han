<template>
  <div class="app-container">
    <!-- 时间范围选择 -->
    <el-card shadow="never" class="search-form">
      <el-form :inline="true">
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts" @change="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ summary.totalCalls }}</div>
          <div class="stat-label">总调用次数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ formatNumber(summary.totalTokens) }}</div>
          <div class="stat-label">总Token数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ formatNumber(summary.promptTokens) }}</div>
          <div class="stat-label">提示词Token</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ formatNumber(summary.completionTokens) }}</div>
          <div class="stat-label">回复Token</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 按模型统计 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>按模型统计</span></template>
          <el-table :data="modelStats" v-loading="loading" size="small">
            <el-table-column label="模型" prop="model_name" min-width="120" show-overflow-tooltip />
            <el-table-column label="调用次数" prop="call_count" width="90" align="right" />
            <el-table-column label="提示词Token" prop="prompt_tokens" width="110" align="right">
              <template #default="{ row }">{{ formatNumber(row.prompt_tokens) }}</template>
            </el-table-column>
            <el-table-column label="回复Token" prop="completion_tokens" width="100" align="right">
              <template #default="{ row }">{{ formatNumber(row.completion_tokens) }}</template>
            </el-table-column>
            <el-table-column label="总Token" prop="total_tokens" width="100" align="right">
              <template #default="{ row }">
                <span class="token-total">{{ formatNumber(row.total_tokens) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 按用户统计 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>按用户统计（Top 20）</span></template>
          <el-table :data="userStats" v-loading="loading" size="small">
            <el-table-column label="用户ID" prop="user_id" width="100" />
            <el-table-column label="调用次数" prop="call_count" width="100" align="right" />
            <el-table-column label="总Token" prop="total_tokens" min-width="120" align="right">
              <template #default="{ row }">
                <span class="token-total">{{ formatNumber(row.total_tokens) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="占比" width="150">
              <template #default="{ row }">
                <el-progress :percentage="getPercentage(row.total_tokens)" :stroke-width="12" :show-text="true" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 每日趋势 -->
    <el-card shadow="never" class="daily-card">
      <template #header><span>每日Token用量趋势</span></template>
      <el-table :data="dailyStats" v-loading="loading" size="small">
        <el-table-column label="日期" prop="date" width="130" />
        <el-table-column label="调用次数" prop="call_count" width="100" align="right" />
        <el-table-column label="总Token" prop="total_tokens" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.total_tokens) }}</template>
        </el-table-column>
        <el-table-column label="用量" min-width="300">
          <template #default="{ row }">
            <el-progress :percentage="getDailyPercentage(row.total_tokens)" :stroke-width="16"
              :color="'#409eff'" :show-text="false" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { tokenStatsByModel, tokenStatsByUser, tokenStatsByDay } from '@/api/ai'

const loading = ref(false)
const modelStats = ref<any[]>([])
const userStats = ref<any[]>([])
const dailyStats = ref<any[]>([])

const today = new Date()
const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000)
const formatDate = (d: Date) => d.toISOString().split('T')[0]

const dateRange = ref([formatDate(thirtyDaysAgo), formatDate(today)])

const dateShortcuts = [
  { text: '最近7天', value: () => { const e = new Date(); const s = new Date(e.getTime() - 7*86400000); return [s, e] } },
  { text: '最近30天', value: () => { const e = new Date(); const s = new Date(e.getTime() - 30*86400000); return [s, e] } },
  { text: '最近90天', value: () => { const e = new Date(); const s = new Date(e.getTime() - 90*86400000); return [s, e] } }
]

const summary = computed(() => {
  let totalCalls = 0, totalTokens = 0, promptTokens = 0, completionTokens = 0
  modelStats.value.forEach(row => {
    totalCalls += Number(row.call_count || 0)
    totalTokens += Number(row.total_tokens || 0)
    promptTokens += Number(row.prompt_tokens || 0)
    completionTokens += Number(row.completion_tokens || 0)
  })
  return { totalCalls, totalTokens, promptTokens, completionTokens }
})

const formatNumber = (n: number | string) => {
  const num = Number(n || 0)
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const getPercentage = (tokens: number) => {
  if (!summary.value.totalTokens) return 0
  return Math.round(Number(tokens) / summary.value.totalTokens * 100)
}

const maxDailyTokens = computed(() => {
  return Math.max(...dailyStats.value.map(r => Number(r.total_tokens || 0)), 1)
})

const getDailyPercentage = (tokens: number) => {
  return Math.round(Number(tokens) / maxDailyTokens.value * 100)
}

const handleQuery = async () => {
  if (!dateRange.value || dateRange.value.length < 2) return
  const [start, end] = dateRange.value
  const startTime = start + ' 00:00:00'
  const endTime = end + ' 23:59:59'
  loading.value = true
  try {
    const [modelRes, userRes, dailyRes] = await Promise.all([
      tokenStatsByModel(startTime, endTime),
      tokenStatsByUser(startTime, endTime),
      tokenStatsByDay(startTime, endTime)
    ])
    modelStats.value = modelRes.data || []
    userStats.value = userRes.data || []
    dailyStats.value = dailyRes.data || []
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(() => handleQuery())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.stats-row { margin-bottom: 16px; }
.stat-card {
  text-align: center;
  .stat-value { font-size: 28px; font-weight: bold; color: #303133; }
  .stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
}
.daily-card { margin-top: 16px; }
.token-total { font-weight: bold; color: #409eff; }
</style>
