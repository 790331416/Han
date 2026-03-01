<template>
  <div class="navbar">
    <div class="left-menu">
      <el-icon class="hamburger" @click="toggleSidebar">
        <Fold v-if="appStore.sidebar.opened" />
        <Expand v-else />
      </el-icon>
      
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
          {{ item.meta?.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    
    <div class="right-menu">
      <el-dropdown trigger="click">
        <div class="avatar-wrapper">
          <el-avatar :size="32" :src="userStore.avatar || defaultAvatar" />
          <span class="username">{{ userStore.nickname || userStore.username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/')">
              <el-icon><HomeFilled /></el-icon>首页
            </el-dropdown-item>
            <el-dropdown-item @click="handleProfile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, ArrowDown, HomeFilled, User, SwitchButton } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const breadcrumbs = computed(() => {
  return route.matched.filter(item => item.meta?.title)
})

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleProfile = () => {
  router.push('/profile')
}

const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  
  await userStore.logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #f3f4f6;
}

.left-menu {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hamburger {
  font-size: 18px;
  color: #6b7280;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.15s ease;
  
  &:hover {
    color: #2563eb;
    background: #f3f4f6;
  }
}

.right-menu {
  display: flex;
  align-items: center;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s ease;
  
  &:hover {
    background: #f3f4f6;
  }
  
  .username {
    font-size: 14px;
    font-weight: 500;
    color: #374151;
  }
}

:deep(.el-breadcrumb) {
  .el-breadcrumb__item {
    .el-breadcrumb__inner {
      color: #6b7280;
      font-weight: 400;
    }
    &:last-child .el-breadcrumb__inner {
      color: #111827;
      font-weight: 500;
    }
  }
}
</style>
