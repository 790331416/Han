<template>
  <div class="error-page" data-testid="error-404-page">
    <div class="error-content">
      <h1 class="error-code">404</h1>
      <p class="error-desc">抱歉，您访问的页面不存在</p>
      <el-button type="primary" size="large" @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
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
  background: linear-gradient(135deg, #2563eb, #3b82f6, #93c5fd);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  line-height: 1;
}

.error-desc {
  font-size: 16px;
  color: #6b7280;
  margin-bottom: 32px;
  font-weight: 400;
}
</style>
