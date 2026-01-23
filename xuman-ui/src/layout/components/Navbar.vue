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
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 15px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.left-menu {
  display: flex;
  align-items: center;
  gap: 15px;
}

.hamburger {
  font-size: 20px;
  cursor: pointer;
  
  &:hover {
    color: #409eff;
  }
}

.right-menu {
  display: flex;
  align-items: center;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  
  .username {
    font-size: 14px;
    color: #333;
  }
}
</style>
