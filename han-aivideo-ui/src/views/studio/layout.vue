<template>
  <div class="studio-shell">
    <header class="studio-header">
      <button class="brand-button" type="button" @click="router.push('/studio/projects')">
        <el-icon><VideoCamera /></el-icon>
        <span>AI短剧工作台</span>
      </button>
      <nav class="studio-nav">
        <el-button :icon="FolderOpened" text @click="router.push('/studio/projects')">项目</el-button>
        <el-button :icon="Plus" type="primary" @click="router.push('/studio/projects/create')">新建</el-button>
      </nav>
    </header>
    <main class="studio-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { FolderOpened, Plus, VideoCamera } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<style lang="scss" scoped>
.studio-shell {
  min-height: 100vh;
  background: #f5f7fb;
}

.studio-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.brand-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.studio-nav {
  display: flex;
  align-items: center;
  gap: 10px;
}

.studio-main {
  min-height: calc(100vh - 56px);
}
</style>
