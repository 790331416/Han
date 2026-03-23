<template>
  <div class="tags-view-container">
    <div class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="{ path: tag.path, query: tag.query }"
        class="tags-view-item"
        :class="{ active: isActive(tag) }"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        <span class="tag-title">{{ tag.title }}</span>
        <el-icon v-if="!tag.meta?.affix" class="tag-close" @click.prevent.stop="closeTag(tag)">
          <Close />
        </el-icon>
      </router-link>
    </div>

    <!-- 右键菜单 -->
    <ul v-show="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <li @click="refreshPage">刷新页面</li>
      <li @click="closeTag(contextMenu.tag!)">关闭当前</li>
      <li @click="closeOtherTags">关闭其他</li>
      <li @click="closeLeftTags">关闭左侧</li>
      <li @click="closeRightTags">关闭右侧</li>
      <li @click="closeAllTags">关闭所有</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useTagsViewStore, type TagView } from '@/stores/tagsView'

const route = useRoute()
const router = useRouter()
const tagsViewStore = useTagsViewStore()

const visitedViews = computed(() => tagsViewStore.visitedViews)

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  tag: null as TagView | null
})

function isActive(tag: TagView) {
  return tag.path === route.path
}

function addTag() {
  tagsViewStore.addView(route)
}

function closeTag(tag: TagView) {
  tagsViewStore.removeView(tag)
  if (isActive(tag)) {
    toLastView()
  }
}

function closeOtherTags() {
  if (contextMenu.tag) {
    tagsViewStore.removeOtherViews(contextMenu.tag)
    if (!isActive(contextMenu.tag)) {
      router.push(contextMenu.tag.fullPath)
    }
  }
}

function closeLeftTags() {
  if (contextMenu.tag) {
    tagsViewStore.removeLeftViews(contextMenu.tag)
    if (!visitedViews.value.some(v => v.path === route.path)) {
      toLastView()
    }
  }
}

function closeRightTags() {
  if (contextMenu.tag) {
    tagsViewStore.removeRightViews(contextMenu.tag)
    if (!visitedViews.value.some(v => v.path === route.path)) {
      toLastView()
    }
  }
}

function closeAllTags() {
  tagsViewStore.removeAllViews()
  toLastView()
}

function refreshPage() {
  // Force re-render by navigating to a redirect then back
  const { fullPath } = route
  router.replace('/redirect' + fullPath)
}

function toLastView() {
  const last = visitedViews.value[visitedViews.value.length - 1]
  if (last) {
    router.push(last.fullPath)
  } else {
    router.push('/')
  }
}

function openMenu(tag: TagView, e: MouseEvent) {
  contextMenu.tag = tag
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.visible = true
}

function closeMenu() {
  contextMenu.visible = false
}

watch(route, () => {
  addTag()
})

watch(() => contextMenu.visible, (val) => {
  if (val) {
    document.addEventListener('click', closeMenu, { once: true })
  }
})

onMounted(() => {
  addTag()
})
</script>

<style lang="scss" scoped>
.tags-view-container {
  height: 34px;
  background: #ffffff;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  align-items: center;
  padding: 0 12px;
  position: relative;
  user-select: none;
}

.tags-view-wrapper {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.tags-view-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 12px;
  color: #6b7280;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    color: #2563eb;
    border-color: #bfdbfe;
    background: #eff6ff;
  }

  &.active {
    color: #ffffff;
    background: #2563eb;
    border-color: #2563eb;

    .tag-close {
      color: rgba(255, 255, 255, 0.8);

      &:hover {
        color: #ffffff;
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}

.tag-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 1px;
  transition: all 0.15s ease;

  &:hover {
    color: #ef4444;
    background: #fee2e2;
  }
}

.context-menu {
  position: fixed;
  z-index: 3000;
  list-style: none;
  margin: 0;
  padding: 6px 0;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 4px 16px rgb(0 0 0 / 0.1);
  min-width: 120px;

  li {
    padding: 6px 16px;
    font-size: 13px;
    color: #374151;
    cursor: pointer;
    transition: all 0.1s ease;

    &:hover {
      background: #eff6ff;
      color: #2563eb;
    }
  }
}

// Dark mode
html.dark {
  .tags-view-container {
    background: #111827;
    border-bottom-color: #1f2937;
  }
  .tags-view-item {
    color: #9ca3af;
    background: #1f2937;
    border-color: #374151;
    &:hover { color: #3b82f6; border-color: #1d4ed8; background: #172554; }
    &.active { color: #fff; background: #2563eb; border-color: #2563eb; }
  }
  .tag-close:hover { color: #f87171; background: rgba(239, 68, 68, 0.15); }
  .context-menu {
    background: #1f2937; border-color: #374151;
    box-shadow: 0 4px 16px rgb(0 0 0 / 0.4);
    li { color: #e5e7eb; &:hover { background: #172554; color: #3b82f6; } }
  }
}
</style>
