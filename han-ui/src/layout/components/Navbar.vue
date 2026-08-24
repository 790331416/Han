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
      <el-tooltip :content="isDark ? '浅色模式' : '深色模式'" placement="bottom">
        <el-icon class="nav-icon" @click="toggleDark()">
          <Moon v-if="!isDark" />
          <Sunny v-else />
        </el-icon>
      </el-tooltip>

      <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
        <el-icon class="nav-icon" :class="{ 'is-fullscreen': isFullscreen }" @click="toggle">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleLocaleChange">
        <el-icon class="nav-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
          </svg>
        </el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN" :disabled="currentLocale === 'zh-CN'">简体中文</el-dropdown-item>
            <el-dropdown-item command="en-US" :disabled="currentLocale === 'en-US'">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <NotifyBell />

      <div
        v-if="userStore.tenantName"
        class="tenant-switcher"
        data-testid="tenant-switcher"
        @click="openTenantDialog"
      >
        <el-icon><OfficeBuilding /></el-icon>
        <span class="tenant-name">{{ userStore.tenantName || '默认租户' }}</span>
        <el-icon><ArrowDown /></el-icon>
      </div>

      <div
        v-if="userStore.identityDisplayName"
        class="identity-switcher"
        :class="{ clickable: hasMultipleIdentities }"
        data-testid="identity-switcher"
        @click="hasMultipleIdentities && openIdentityDialog()"
      >
        <el-icon><School /></el-icon>
        <span class="identity-text">
          <span class="identity-school">{{ userStore.schoolName }}</span>
          <span class="identity-sep">/</span>
          <span class="identity-person">{{ userStore.identityDisplayName }}</span>
          <span v-if="userStore.dutyName" class="identity-duty">{{ userStore.dutyName }}</span>
        </span>
        <el-icon v-if="hasMultipleIdentities"><ArrowDown /></el-icon>
      </div>

      <el-dropdown trigger="click">
        <div class="avatar-wrapper" data-testid="navbar-user-menu">
          <el-avatar :size="32" :src="userStore.avatar || defaultAvatar" />
          <span class="username">{{ userStore.nickname || userStore.username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item data-testid="navbar-home" @click="router.push('/')">
              <el-icon><HomeFilled /></el-icon>首页
            </el-dropdown-item>
            <el-dropdown-item data-testid="navbar-profile" @click="handleProfile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item v-if="canViewSystemBrand" data-testid="navbar-system-brand" @click="router.push('/system/brand')">
              <el-icon><Setting /></el-icon>系统设置
            </el-dropdown-item>
            <el-dropdown-item divided data-testid="navbar-logout" @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-dialog v-model="tenantDialogVisible" title="切换租户" width="400px" destroy-on-close>
      <div v-loading="tenantLoading">
        <el-radio-group v-model="selectedTenantId" class="tenant-radio-group">
          <el-radio
            v-for="tenant in tenantList"
            :key="tenant.tenantId"
            :value="tenant.tenantId"
            :disabled="tenant.status !== 0"
            class="tenant-radio-item"
          >
            {{ tenant.tenantName }}
            <el-tag v-if="tenant.current" type="primary" size="small" class="ml-2">当前</el-tag>
            <el-tag v-if="tenant.status !== 0" type="danger" size="small" class="ml-2">停用</el-tag>
          </el-radio>
        </el-radio-group>
        <el-empty v-if="!tenantLoading && tenantList.length === 0" description="暂无可切换的租户" />
      </div>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="switchLoading"
          :disabled="!selectedTenantId"
          @click="handleSwitchTenant"
        >
          确认切换
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="identityDialogVisible" title="切换学校身份" width="460px" destroy-on-close>
      <div v-loading="identityLoading">
        <el-radio-group v-model="selectedIdentityId" class="identity-radio-group">
          <el-radio
            v-for="item in userStore.identityList"
            :key="item.identityId"
            :value="item.identityId"
            :disabled="!userStore.identityManagementAvailable(item)"
            class="identity-radio-item"
          >
            <span class="identity-option">
              <span class="identity-main">{{ item.schoolName }} / {{ item.identityDisplayName }}</span>
              <span class="identity-sub">{{ item.dutyName || '—' }}</span>
              <el-tag v-if="item.current" type="primary" size="small" class="identity-tag">当前</el-tag>
              <el-tag v-if="!userStore.identityManagementAvailable(item)" type="info" size="small" class="identity-tag">{{ userStore.identityManagementUnavailableReason(item) }}</el-tag>
            </span>
          </el-radio>
        </el-radio-group>
        <el-empty v-if="!identityLoading && userStore.identityList.length === 0" description="暂无可切换的学校身份" />
      </div>
      <template #footer>
        <el-button @click="identityDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="identitySwitching"
          :disabled="!selectedIdentityId"
          @click="handleSwitchIdentity"
        >
          确认切换
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Expand,
  Fold,
  FullScreen,
  HomeFilled,
  Moon,
  OfficeBuilding,
  School,
  Setting,
  Sunny,
  SwitchButton,
  User
} from '@element-plus/icons-vue'
import { useDark, useFullscreen, useToggle } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { getMyTenants, switchTenant, switchIdentity, type TenantSimple } from '@/api/auth'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import NotifyBell from './NotifyBell.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const canViewSystemBrand = computed(() => userStore.hasPermission('system:brand:query'))

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

const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleProfile = () => {
  router.push('/profile')
}

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
    const currentTenant = tenantList.value.find(item => item.current)
    if (currentTenant) {
      selectedTenantId.value = currentTenant.tenantId
    }
  } catch {
    tenantList.value = []
  } finally {
    tenantLoading.value = false
  }
}

const handleSwitchTenant = async () => {
  if (!selectedTenantId.value) return

  const currentTenant = tenantList.value.find(item => item.current)
  if (currentTenant && currentTenant.tenantId === selectedTenantId.value) {
    tenantDialogVisible.value = false
    return
  }

  switchLoading.value = true
  try {
    const res = await switchTenant(selectedTenantId.value)
    const data = (res as any).data

    if (data?.accessToken) {
      /**
       * 租户切换会签发全新的登录会话。
       * 必须统一走 Store 收口，确保 token、refreshToken 和运行时 userId 同步更新，
       * 避免后续请求继续携带旧身份锚点，造成误判未授权后跳回登录页。
       */
      userStore.applySession(
        data.accessToken,
        data.refreshToken,
        data.userInfo?.userId ?? null
      )
      tenantDialogVisible.value = false
      ElMessage.success('租户切换成功，正在刷新...')
      setTimeout(() => window.location.reload(), 500)
    }
  } catch {
    // 错误提示统一由请求层处理。
  } finally {
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

// ==================== 学校身份切换 ====================
const hasMultipleIdentities = computed(() => userStore.identityList.length > 1)
const identityDialogVisible = ref(false)
const identityLoading = ref(false)
const identitySwitching = ref(false)
const selectedIdentityId = ref<string | number>()

const openIdentityDialog = async () => {
  identityDialogVisible.value = true
  identityLoading.value = true
  selectedIdentityId.value = undefined

  try {
    // 打开时刷新一次身份列表，current 标记与有效性以服务端为准。
    const list = await userStore.loadIdentities()
    const current = list.find((item) => item.current)
    if (current) {
      selectedIdentityId.value = current.identityId
    }
  } catch {
    // 身份列表不可用时保留原列表，错误提示统一由请求层处理。
  } finally {
    identityLoading.value = false
  }
}

const handleSwitchIdentity = async () => {
  if (!selectedIdentityId.value) return

  const current = userStore.identityList.find((item) => item.current)
  if (current && String(current.identityId) === String(selectedIdentityId.value)) {
    identityDialogVisible.value = false
    return
  }

  identitySwitching.value = true
  try {
    const res = await switchIdentity(selectedIdentityId.value)
    const data = (res as any).data

    if (data?.accessToken) {
      const chosen = userStore.identityList.find((item) => String(item.identityId) === String(selectedIdentityId.value))
      /**
       * 身份切换会签发全新登录会话，且作废旧身份课堂凭证。
       * 复用租户切换的收口方式：applySession 全量清理会话 + location.reload()
       * 清空路由 / 页签 / KeepAlive，避免旧身份的菜单与数据残留。
       */
      userStore.applySession(
        data.accessToken,
        data.refreshToken,
        data.userInfo?.userId ?? null
      )
      userStore.applyIdentity(chosen)
      identityDialogVisible.value = false
      ElMessage.success('身份切换成功，正在刷新...')
      setTimeout(() => window.location.reload(), 500)
    }
  } catch {
    // 错误提示统一由请求层处理。
  } finally {
    identitySwitching.value = false
  }
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

.identity-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: default;
  color: #374151;
  font-size: 13px;
  border: 1px solid #e5e7eb;

  &.clickable {
    cursor: pointer;

    &:hover {
      background: #eff6ff;
      border-color: #2563eb;
      color: #2563eb;
    }
  }

  .identity-text {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    max-width: 220px;
    overflow: hidden;
    white-space: nowrap;
  }

  .identity-school {
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  .identity-sep {
    color: #9ca3af;
  }

  .identity-person {
    font-weight: 500;
  }

  .identity-duty {
    padding: 0 6px;
    font-size: 11px;
    color: #6b7280;
    background: #f3f4f6;
    border-radius: 4px;
  }
}

.identity-radio-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.identity-radio-item {
  height: auto;
  padding: 8px 0;
  white-space: normal;
}

.identity-option {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  line-height: 1.4;
}

.identity-main {
  font-weight: 500;
}

.identity-sub {
  font-size: 12px;
  color: #9ca3af;
}

.identity-tag {
  margin-left: 2px;
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
  .hamburger {
    color: #9ca3af;

    &:hover {
      color: #3b82f6;
      background: #1f2937;
    }
  }

  .nav-icon {
    color: #9ca3af;

    &:hover {
      color: #3b82f6;
      background: #1f2937;
    }

    &.is-fullscreen {
      color: #3b82f6;
    }
  }

  .avatar-wrapper {
    &:hover {
      background: #1f2937;
    }

    .username {
      color: #e5e7eb;
    }
  }

  .tenant-switcher {
    color: #e5e7eb;
    border-color: #374151;

    &:hover {
      background: #172554;
      border-color: #3b82f6;
      color: #3b82f6;
    }
  }

  .identity-switcher {
    color: #e5e7eb;
    border-color: #374151;

    &.clickable:hover {
      background: #172554;
      border-color: #3b82f6;
      color: #3b82f6;
    }

    .identity-duty {
      color: #d1d5db;
      background: #1f2937;
    }
  }

  :deep(.el-breadcrumb) {
    .el-breadcrumb__item .el-breadcrumb__inner {
      color: #9ca3af;
    }

    .el-breadcrumb__item:last-child .el-breadcrumb__inner {
      color: #f9fafb;
    }
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
