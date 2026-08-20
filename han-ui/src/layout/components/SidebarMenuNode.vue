<template>
  <template v-if="!route.meta?.hidden">
    <el-menu-item
      v-if="children.length === 0"
      :index="resolvedPath"
      :data-testid="menuTestId(route)"
    >
      <el-icon v-if="route.meta?.icon"><component :is="route.meta.icon" /></el-icon>
      <template #title>{{ route.meta?.title }}</template>
    </el-menu-item>

    <el-sub-menu v-else :index="resolvedPath" :data-testid="menuTestId(route)">
      <template #title>
        <el-icon v-if="route.meta?.icon"><component :is="route.meta.icon" /></el-icon>
        <span>{{ route.meta?.title }}</span>
      </template>
      <SidebarMenuNode
        v-for="child in children"
        :key="child.path"
        :route="child"
        :parent-path="fullPath"
      />
    </el-sub-menu>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { resolveMenuPath } from '@/router'

const props = defineProps<{
  route: RouteRecordRaw
  parentPath: string
}>()

const route = computed(() => props.route)
const children = computed(() => (props.route.children || []).filter((child) => !child.meta?.hidden))
const resolvedPath = computed(() => resolveMenuPath(props.parentPath, props.route.path))
const fullPath = computed(() => resolvedPath.value)

function menuTestId(item: RouteRecordRaw) {
  const name = typeof item.name === 'string' ? item.name : resolveMenuPath(props.parentPath, item.path)
  return `sidebar-menu-${name.toLowerCase()}`
}
</script>
