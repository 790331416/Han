<template>
  <div class="app-layout" :class="{ 'sidebar-collapsed': !appStore.sidebar.opened }">
    <Sidebar class="sidebar-container" />
    <div class="main-container">
      <Navbar />
      <TagsView />
      <div class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive :include="[...tagsViewStore.cachedViews]">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useTagsViewStore } from '@/stores/tagsView'
import { useWatermark } from '@/utils/watermark'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import TagsView from './components/TagsView.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const tagsViewStore = useTagsViewStore()
const { set: setWatermark, clear: clearWatermark } = useWatermark()

onMounted(() => {
  const name = userStore.nickname || userStore.username || 'user'
  const date = new Date().toLocaleDateString('zh-CN')
  setWatermark(`${name} ${date}`)
})

onBeforeUnmount(() => {
  clearWatermark()
})
</script>

<style lang="scss" scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  background: #f9fafb;
}

html.dark .app-layout {
  background: #0f172a;
}

.sidebar-container {
  width: 240px;
  height: 100%;
  background: #ffffff;
  border-right: 1px solid #f3f4f6;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

html.dark .sidebar-container {
  background: #111827;
  border-right-color: #1f2937;
}

.sidebar-collapsed .sidebar-container {
  width: 64px;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.app-main {
  flex: 1;
  overflow: auto;
  background: #f9fafb;
  padding: 0;
}

html.dark .app-main {
  background: #0f172a;
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.2s ease;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
