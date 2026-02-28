<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background: #409eff"><User /></el-icon>
            <div class="stat-info">
              <span class="stat-value">{{ stats.userCount }}</span>
              <span class="stat-label">用户总数</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background: #67c23a"><OfficeBuilding /></el-icon>
            <div class="stat-info">
              <span class="stat-value">{{ stats.deptCount }}</span>
              <span class="stat-label">部门数量</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background: #e6a23c"><UserFilled /></el-icon>
            <div class="stat-info">
              <span class="stat-value">{{ stats.onlineCount }}</span>
              <span class="stat-label">在线用户</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background: #f56c6c"><Document /></el-icon>
            <div class="stat-info">
              <span class="stat-value">{{ stats.postCount }}</span>
              <span class="stat-label">岗位数量</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>系统信息</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="系统名称">HAN Cloud</el-descriptions-item>
            <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
            <el-descriptions-item label="Spring Boot">4.0.0</el-descriptions-item>
            <el-descriptions-item label="Spring Cloud">2024.0.0</el-descriptions-item>
            <el-descriptions-item label="Vue">3.5.x</el-descriptions-item>
            <el-descriptions-item label="Element Plus">2.9.x</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" :icon="User" @click="router.push('/system/user')">用户管理</el-button>
            <el-button type="success" :icon="OfficeBuilding" @click="router.push('/system/dept')">部门管理</el-button>
            <el-button type="warning" :icon="Setting" @click="router.push('/system/role')">角色管理</el-button>
            <el-button type="info" :icon="Document" @click="router.push('/system/dict')">字典管理</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, OfficeBuilding, UserFilled, Document, Setting } from '@element-plus/icons-vue'
import { get } from '@/utils/request'

const router = useRouter()

const stats = reactive({
  userCount: 0,
  deptCount: 0,
  roleCount: 0,
  postCount: 0,
  onlineCount: 0
})

onMounted(async () => {
  try {
    const res = await get<any>('/system/dashboard/stats')
    const data = (res as any).data
    if (data) {
      Object.assign(stats, data)
    }
  } catch {
    // 接口不可用时保持默认值
  }
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  padding: 20px;
}

.stat-card {
  .stat-item {
    display: flex;
    align-items: center;
    gap: 15px;
  }
  
  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    color: #fff;
  }
  
  .stat-info {
    display: flex;
    flex-direction: column;
  }
  
  .stat-value {
    font-size: 24px;
    font-weight: bold;
    color: #333;
  }
  
  .stat-label {
    font-size: 14px;
    color: #999;
  }
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  
  .el-button {
    width: 100%;
  }
}
</style>
