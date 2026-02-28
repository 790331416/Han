<template>
  <div class="sidebar">
    <div class="logo">
      <img src="@/assets/logo.svg" alt="logo" class="logo-img" v-if="appStore.sidebar.opened" />
      <span class="logo-text" v-if="appStore.sidebar.opened">HAN Cloud</span>
      <span class="logo-text-small" v-else>XM</span>
    </div>
    
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="!appStore.sidebar.opened"
        :unique-opened="true"
        :collapse-transition="false"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        mode="vertical"
        router
      >
        <template v-for="route in routes" :key="route.path">
          <template v-if="!route.meta?.hidden">
            <!-- 无子菜单或只有一个可见子菜单：显示为单级菜单 -->
            <el-menu-item
              v-if="visibleChildren(route).length <= 1"
              :index="resolvePath(route.path, visibleChildren(route)[0]?.path)"
            >
              <el-icon v-if="menuMeta(route).icon"><component :is="menuMeta(route).icon" /></el-icon>
              <template #title>{{ menuMeta(route).title }}</template>
            </el-menu-item>
            
            <!-- 多级菜单 -->
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
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #2b3a4a;
  
  .logo-img {
    width: 32px;
    height: 32px;
  }
  
  .logo-text {
    color: #fff;
    font-size: 16px;
    font-weight: bold;
  }
  
  .logo-text-small {
    color: #fff;
    font-size: 18px;
    font-weight: bold;
  }
}

.el-menu {
  border-right: none;
}
</style>
