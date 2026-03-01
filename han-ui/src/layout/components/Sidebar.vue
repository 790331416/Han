<template>
  <div class="sidebar">
    <div class="logo">
      <div class="logo-icon">H</div>
      <span class="logo-text" v-if="appStore.sidebar.opened">HAN Cloud</span>
    </div>
    
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="!appStore.sidebar.opened"
        :unique-opened="true"
        :collapse-transition="false"
        background-color="transparent"
        text-color="#6b7280"
        active-text-color="#2563eb"
        mode="vertical"
        router
      >
        <template v-for="route in routes" :key="route.path">
          <template v-if="!route.meta?.hidden">
            <el-menu-item
              v-if="visibleChildren(route).length <= 1"
              :index="resolvePath(route.path, visibleChildren(route)[0]?.path)"
            >
              <el-icon v-if="menuMeta(route).icon"><component :is="menuMeta(route).icon" /></el-icon>
              <template #title>{{ menuMeta(route).title }}</template>
            </el-menu-item>
            
            <el-sub-menu v-else :index="route.path">
              <template #title>
                <el-icon v-if="route.meta?.icon"><component :is="route.meta.icon" /></el-icon>
                <span>{{ route.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in visibleChildren(route)"
                :key="child.path"
                :index="resolvePath(route.path, child.path)"
              >
                <el-icon v-if="child.meta?.icon"><component :is="child.meta.icon" /></el-icon>
                <template #title>{{ child.meta?.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, type RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { constantRoutes } from '@/router'

const route = useRoute()
const appStore = useAppStore()

const routes = computed(() => constantRoutes.filter(r => r.path !== '/login' && r.path !== '/404'))
const activeMenu = computed(() => route.path)

function visibleChildren(route: RouteRecordRaw) {
  return (route.children || []).filter(c => !c.meta?.hidden)
}

function menuMeta(route: RouteRecordRaw) {
  const children = visibleChildren(route)
  if (children.length === 1) {
    return children[0].meta || {}
  }
  return route.meta || {}
}

function resolvePath(parentPath: string, childPath?: string) {
  if (!childPath) return parentPath
  if (parentPath === '/') return '/' + childPath
  return parentPath + '/' + childPath
}
</script>

<style lang="scss" scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  border-bottom: 1px solid #f3f4f6;
  
  .logo-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #2563eb, #3b82f6);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 16px;
    flex-shrink: 0;
  }
  
  .logo-text {
    color: #111827;
    font-size: 15px;
    font-weight: 700;
    letter-spacing: -0.02em;
    white-space: nowrap;
  }
}

:deep(.el-menu) {
  border-right: none !important;
  padding: 8px;
  
  .el-menu-item,
  .el-sub-menu__title {
    height: 40px;
    line-height: 40px;
    border-radius: 8px;
    margin-bottom: 2px;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.15s ease;
    
    &:hover {
      background-color: #f3f4f6 !important;
      color: #111827 !important;
    }
  }
  
  .el-menu-item.is-active {
    background-color: #eff6ff !important;
    color: #2563eb !important;
    font-weight: 600;
  }
  
  .el-sub-menu .el-menu-item {
    padding-left: 48px !important;
    font-weight: 400;
  }
  
  .el-icon {
    font-size: 18px;
  }
}
</style>
