<template>
  <div class="sidebar">
    <div class="logo">
      <img v-if="brandStore.logoUrl" class="logo-icon logo-image" :src="brandStore.logoUrl" alt="" />
      <div v-else class="logo-icon">{{ brandStore.shortName.slice(0, 1) || 'H' }}</div>
      <span v-if="appStore.sidebar.opened" class="logo-text">{{ brandStore.displayName }}</span>
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
        <SidebarMenuNode
          v-for="route in routes"
          :key="route.path"
          :route="route"
          parent-path=""
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, type RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useBrandStore } from '@/stores/brand'
import { dynamicMenuRoutes } from '@/router'
import SidebarMenuNode from './SidebarMenuNode.vue'

const route = useRoute()
const appStore = useAppStore()
const brandStore = useBrandStore()

onMounted(() => {
  if (!appStore.capabilitiesLoaded) {
    appStore.loadRuntimeCapabilities()
  }
})

function filterRoutes(routes: RouteRecordRaw[]): RouteRecordRaw[] {
  return routes
    .filter((r) => {
      return !r.meta?.hidden
    })
    .map((r) => {
      if (r.children) {
        return { ...r, children: r.children.filter((c) => !c.meta?.hidden) }
      }
      return r
    })
    .filter((r) => !r.children || r.children.length > 0)
}

const routes = computed(() => filterRoutes(dynamicMenuRoutes.value))
const activeMenu = computed(() => route.path)

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

  .logo-image {
    object-fit: contain;
    background: transparent;
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

html.dark {
  .logo {
    border-bottom-color: #1f2937;

    .logo-text {
      color: #f9fafb;
    }
  }

  :deep(.el-menu) {
    .el-menu-item,
    .el-sub-menu__title {
      color: #9ca3af !important;

      &:hover {
        background-color: #1f2937 !important;
        color: #f9fafb !important;
      }
    }

    .el-menu-item.is-active {
      background-color: #172554 !important;
      color: #3b82f6 !important;
    }
  }
}
</style>
