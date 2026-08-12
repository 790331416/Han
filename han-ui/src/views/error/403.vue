<template>
  <div class="error-page" data-testid="error-403-page">
    <div class="error-content">
      <h1 class="error-code">403</h1>
      <p class="error-desc">抱歉，您没有访问该页面的权限</p>
      <p class="error-tip">如果确认需要该权限，请联系系统管理员为您的角色分配对应菜单权限。</p>
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
  background: linear-gradient(135deg, #d97706, #f59e0b, #fcd34d);
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
</style>
