<template>
  <div class="error-page" data-testid="error-500-page">
    <div class="error-content">
      <h1 class="error-code">500</h1>
      <p class="error-desc">服务器开小差了，请稍后重试</p>
      <p class="error-tip">若持续出现，请联系系统管理员查看服务端日志。</p>
      <div class="error-actions">
        <el-button size="large" @click="handleReload">重新加载</el-button>
        <el-button type="primary" size="large" @click="router.push('/')">返回首页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const handleReload = () => {
  window.location.reload()
}
</script>

<style lang="scss" scoped>
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: #f9fafb;
}

.error-content {
  text-align: center;
}

.error-code {
  font-size: 140px;
  font-weight: 800;
  letter-spacing: -0.04em;
  background: linear-gradient(135deg, #dc2626, #ef4444, #fca5a5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  line-height: 1;
}

.error-desc {
  font-size: 16px;
  color: #6b7280;
  margin-bottom: 8px;
  font-weight: 400;
}

.error-tip {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 32px;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
