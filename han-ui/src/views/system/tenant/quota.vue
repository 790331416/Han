<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>租户资源配额管理</span>
        </div>
      </template>

      <!-- 租户选择 -->
      <el-form :inline="true" class="mb-16">
        <el-form-item label="选择租户">
          <el-select v-model="selectedTenantId" placeholder="请选择租户" filterable @change="loadQuota" style="width: 280px;">
            <el-option v-for="t in tenantList" :key="t.tenantId" :label="t.tenantName" :value="t.tenantId" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 配额概览 -->
      <div v-if="quota" v-loading="loading">
        <el-row :gutter="20" class="quota-cards">
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="quota-card">
              <div class="quota-icon" style="background: #409eff;">👤</div>
              <div class="quota-info">
                <div class="quota-title">用户数量</div>
                <div class="quota-value">
                  <span class="used">{{ quota.userUsed }}</span>
                  <span class="sep">/</span>
                  <span class="limit">{{ quota.userLimit < 0 ? '不限' : quota.userLimit }}</span>
                </div>
                <el-progress
                  :percentage="quota.userLimit < 0 ? 0 : calcPercent(quota.userUsed, quota.userLimit)"
                  :color="getColor(quota.userUsed, quota.userLimit)"
                  :stroke-width="8"
                />
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="quota-card">
              <div class="quota-icon" style="background: #e6a23c;">💾</div>
              <div class="quota-info">
                <div class="quota-title">存储空间</div>
                <div class="quota-value">
                  <span class="used">{{ formatBytes(quota.storageUsed) }}</span>
                  <span class="sep">/</span>
                  <span class="limit">{{ quota.storageLimit < 0 ? '不限' : formatBytes(quota.storageLimit) }}</span>
                </div>
                <el-progress
                  :percentage="quota.storageLimit < 0 ? 0 : calcPercent(quota.storageUsed, quota.storageLimit)"
                  :color="getColor(quota.storageUsed, quota.storageLimit)"
                  :stroke-width="8"
                />
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="quota-card">
              <div class="quota-icon" style="background: #67c23a;">🔗</div>
              <div class="quota-info">
                <div class="quota-title">API调用</div>
                <div class="quota-value">
                  <span class="used">{{ quota.apiUsed }}</span>
                  <span class="sep">/</span>
                  <span class="limit">{{ quota.apiLimit < 0 ? '不限' : quota.apiLimit }}</span>
                </div>
                <el-progress
                  :percentage="quota.apiLimit < 0 ? 0 : calcPercent(quota.apiUsed, quota.apiLimit)"
                  :color="getColor(quota.apiUsed, quota.apiLimit)"
                  :stroke-width="8"
                />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-divider />

        <!-- 配额设置 -->
        <el-card shadow="never">
          <template #header><span>配额设置</span></template>
          <el-form ref="formRef" :model="editForm" label-width="120px" style="max-width: 600px;">
            <el-form-item label="用户数限制">
              <el-input-number v-model="editForm.userLimit" :min="-1" :max="100000" />
              <span class="form-tip">-1 表示不限制</span>
            </el-form-item>
            <el-form-item label="存储限制(GB)">
              <el-input-number v-model="storageLimitGB" :min="-1" :max="10240" :precision="1" />
              <span class="form-tip">-1 表示不限制</span>
            </el-form-item>
            <el-form-item label="API调用限制">
              <el-input-number v-model="editForm.apiLimit" :min="-1" :max="10000000" :step="1000" />
              <span class="form-tip">-1 表示不限制，按重置周期计</span>
            </el-form-item>
            <el-form-item label="重置周期">
              <el-select v-model="editForm.resetCycle" placeholder="请选择重置周期" style="width: 200px;">
                <el-option label="每月" value="monthly" />
                <el-option label="每年" value="yearly" />
                <el-option label="不重置" value="never" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">保存配额</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>

      <el-empty v-if="!selectedTenantId" description="请先选择一个租户" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post } from '@/utils/request'

const tenantList = ref<any[]>([])
const selectedTenantId = ref<number | null>(null)
const quota = ref<any>(null)
const loading = ref(false)
const saving = ref(false)

const editForm = ref<any>({ userLimit: -1, storageLimit: -1, apiLimit: -1, resetCycle: 'monthly' })

const storageLimitGB = computed({
  get: () => {
    const v = editForm.value.storageLimit
    return v < 0 ? -1 : Number((v / (1024 * 1024 * 1024)).toFixed(1))
  },
  set: (val: number) => {
    editForm.value.storageLimit = val < 0 ? -1 : Math.round(val * 1024 * 1024 * 1024)
  }
})

const loadTenants = async () => {
  try {
    const res = await get<any[]>('/tenant/listAllValid')
    tenantList.value = res.data || []
  } catch { /* ignore */ }
}

const loadQuota = async () => {
  if (!selectedTenantId.value) return
  loading.value = true
  try {
    const res = await get<any>(`/tenant/quota/${selectedTenantId.value}`)
    quota.value = res.data
    editForm.value = {
      tenantId: selectedTenantId.value,
      userLimit: res.data.userLimit ?? -1,
      storageLimit: res.data.storageLimit ?? -1,
      apiLimit: res.data.apiLimit ?? -1,
      resetCycle: res.data.resetCycle || 'monthly'
    }
  } catch { /* ignore */ } finally { loading.value = false }
}

const handleSave = async () => {
  saving.value = true
  try {
    await post('/tenant/quota/edit', editForm.value)
    ElMessage.success('配额更新成功')
    loadQuota()
  } catch { /* ignore */ } finally { saving.value = false }
}

const calcPercent = (used: number, limit: number) => {
  if (limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

const getColor = (used: number, limit: number) => {
  if (limit <= 0) return '#409eff'
  const p = (used / limit) * 100
  if (p >= 90) return '#f56c6c'
  if (p >= 70) return '#e6a23c'
  return '#67c23a'
}

const formatBytes = (bytes: number) => {
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

onMounted(() => loadTenants())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.mb-16 { margin-bottom: 16px; }

.quota-cards { margin-bottom: 20px; }
.quota-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px;
}
.quota-icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; flex-shrink: 0;
}
.quota-info { flex: 1; }
.quota-title { font-size: 13px; color: #909399; margin-bottom: 4px; }
.quota-value { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.quota-value .used { color: #303133; }
.quota-value .sep { color: #c0c4cc; margin: 0 4px; }
.quota-value .limit { color: #909399; font-size: 14px; }
.form-tip { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
