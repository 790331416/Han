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
      <!-- 暗色模式 -->
      <el-tooltip :content="isDark ? '浅色模式' : '暗色模式'" placement="bottom">
        <el-icon class="nav-icon" @click="toggleDark()">
          <Moon v-if="!isDark" />
          <Sunny v-else />
        </el-icon>
      </el-tooltip>

      <!-- 全屏切换 -->
      <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
        <el-icon class="nav-icon" :class="{ 'is-fullscreen': isFullscreen }" @click="toggle">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <!-- 语言切换 -->
      <el-dropdown trigger="click" @command="handleLocaleChange">
        <el-icon class="nav-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN" :disabled="currentLocale === 'zh-CN'">简体中文</el-dropdown-item>
            <el-dropdown-item command="en-US" :disabled="currentLocale === 'en-US'">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 消息通知 -->
      <NotifyBell />

      <!-- 租户切换 -->
      <div class="tenant-switcher" @click="openTenantDialog" v-if="userStore.tenantName">
        <el-icon><OfficeBuilding /></el-icon>
        <span class="tenant-name">{{ userStore.tenantName || '默认租户' }}</span>
        <el-icon><ArrowDown /></el-icon>
      </div>

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

    <!-- 租户切换弹窗 -->
    <el-dialog v-model="tenantDialogVisible" title="切换租户" width="400px" destroy-on-close>
      <div v-loading="tenantLoading">
        <el-radio-group v-model="selectedTenantId" class="tenant-radio-group">
          <el-radio v-for="t in tenantList" :key="t.tenantId" :value="t.tenantId" :disabled="t.status !== 0" class="tenant-radio-item">
            {{ t.tenantName }}
            <el-tag v-if="t.current" type="primary" size="small" class="ml-2">当前</el-tag>
            <el-tag v-if="t.status !== 0" type="danger" size="small" class="ml-2">停用</el-tag>
          </el-radio>
        </el-radio-group>
        <el-empty v-if="!tenantLoading && tenantList.length === 0" description="暂无可切换的租户" />
      </div>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="switchLoading" :disabled="!selectedTenantId" @click="handleSwitchTenant">确认切换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Fold, Expand, ArrowDown, HomeFilled, User, SwitchButton, OfficeBuilding, FullScreen, Moon, Sunny } from '@element-plus/icons-vue'
import { useFullscreen, useDark, useToggle } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import NotifyBell from './NotifyBell.vue'
import { getMyTenants, switchTenant, type TenantSimple } from '@/api/auth'
import { setToken, setRefreshToken } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
const { isFullscreen, toggle } = useFullscreen()
const isDark = useDark({ storageKey: 'han-dark-mode' })
const toggleDark = useToggle(isDark)
const { locale } = useI18n()
const currentLocale = computed(() => locale.value)

const handleLocaleChange = (lang: string) => {
  locale.value = lang
  localStorage.setItem('han-locale', lang)
  window.location.reload()
}

const breadcrumbs = computed(() => {
  return route.matched.filter(item => item.meta?.title)
})

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleProfile = () => {
  router.push('/profile')
}

// ==================== 租户切换 ====================
const tenantDialogVisible = ref(false)
const tenantLoading = ref(false)
const switchLoading = ref(false)
const tenantList = ref<TenantSimple[]>([])
const selectedTenantId = ref<string | number>()

const openTenantDialog = async () => {
  tenantDialogVisible.value = true
  tenantLoading.value = true
  selectedTenantId.value = undefined
  try {
    const res = await getMyTenants()
    tenantList.value = (res as any).data || []
    const current = tenantList.value.find(t => t.current)
    if (current) selectedTenantId.value = current.tenantId
  } catch { tenantList.value = [] } finally {
    tenantLoading.value = false
  }
}

const handleSwitchTenant = async () => {
  if (!selectedTenantId.value) return
  const current = tenantList.value.find(t => t.current)
  if (current && current.tenantId === selectedTenantId.value) {
    tenantDialogVisible.value = false
    return
  }
  switchLoading.value = true
  try {
    const res = await switchTenant(selectedTenantId.value)
    const data = (res as any).data
    if (data?.accessToken) {
      // 更新 token 并刷新页面
      setToken(data.accessToken)
      if (data.refreshToken) setRefreshToken(data.refreshToken)
      tenantDialogVisible.value = false
      ElMessage.success('租户切换成功，正在刷新...')
      setTimeout(() => window.location.reload(), 500)
    }
  } catch { /* error handled by request interceptor */ } finally {
    switchLoading.value = false
  }
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

html.dark .navbar {
  background: #111827;
  border-bottom-color: #1f2937;
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
  gap: 8px;
}

.nav-icon {
  font-size: 32px;
  color: #6b7280;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.15s ease;

  &:hover {
    color: #2563eb;
    background: #f3f4f6;
  }

  &.is-fullscreen {
    color: #2563eb;
  }
}

.tenant-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #374151;
  font-size: 13px;
  transition: all 0.15s ease;
  border: 1px solid #e5e7eb;

  &:hover {
    background: #eff6ff;
    border-color: #2563eb;
    color: #2563eb;
  }

  .tenant-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }
}

.tenant-radio-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.tenant-radio-item {
  height: auto;
  padding: 8px 0;
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

html.dark {
  .hamburger { color: #9ca3af; &:hover { color: #3b82f6; background: #1f2937; } }
  .nav-icon { color: #9ca3af; &:hover { color: #3b82f6; background: #1f2937; } &.is-fullscreen { color: #3b82f6; } }
  .avatar-wrapper { &:hover { background: #1f2937; } .username { color: #e5e7eb; } }
  .tenant-switcher { color: #e5e7eb; border-color: #374151; &:hover { background: #172554; border-color: #3b82f6; color: #3b82f6; } }
  :deep(.el-breadcrumb) { .el-breadcrumb__item .el-breadcrumb__inner { color: #9ca3af; } .el-breadcrumb__item:last-child .el-breadcrumb__inner { color: #f9fafb; } }
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
