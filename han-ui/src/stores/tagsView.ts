import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TagView {
  path: string
  fullPath: string
  name?: string | symbol | null
  title: string
  meta?: Record<string, any>
  query?: Record<string, any>
}

function toTag(route: RouteLocationNormalized): TagView {
  return {
    path: route.path,
    fullPath: route.fullPath,
    name: route.name,
    title: (route.meta?.title as string) || '未命名',
    meta: { ...route.meta },
    query: { ...route.query }
  }
}

/**
 * 页签与 keep-alive 缓存的数量上限。
 *
 * `<keep-alive :include>` 直接吃 `cachedViews`，两者原来都没有上限：
 * 用户一天开几十个页签，几十个组件实例（含 ECharts 实例、大表格数据、@vue-flow 画布）
 * 会全部常驻内存不释放。超出后淘汰最久未访问且非固定的页签。
 */
const MAX_VISITED_VIEWS = 20

export const useTagsViewStore = defineStore('tagsView', () => {
  const visitedViews = ref<TagView[]>([])
  const cachedViews = ref<Set<string>>(new Set())

  /** 超出上限时从最旧的非 affix 页签开始淘汰，并同步摘掉它的 keep-alive 缓存。 */
  function evictOverflowViews() {
    while (visitedViews.value.length > MAX_VISITED_VIEWS) {
      const victimIndex = visitedViews.value.findIndex(v => !v.meta?.affix)
      if (victimIndex < 0) {
        break
      }
      const [victim] = visitedViews.value.splice(victimIndex, 1)
      if (victim?.name && typeof victim.name === 'string') {
        cachedViews.value.delete(victim.name)
      }
    }
  }

  function addView(route: RouteLocationNormalized) {
    if (route.meta?.noTagsView) return
    if (route.path === '/login' || route.path === '/404') return
    const exists = visitedViews.value.some(v => v.path === route.path)
    if (!exists) {
      visitedViews.value.push(toTag(route))
    }
    if (route.name && typeof route.name === 'string') {
      cachedViews.value.add(route.name)
    }
    evictOverflowViews()
  }

  /** 手动摘掉某个页面的 keep-alive 缓存，供「刷新页面」强制组件重建。 */
  function removeCachedView(name?: string | symbol | null) {
    if (name && typeof name === 'string') {
      cachedViews.value.delete(name)
    }
  }

  /** 登出 / 换账号时彻底重置，连 affix 页签一起清掉。 */
  function resetViews() {
    visitedViews.value = []
    cachedViews.value.clear()
  }

  function removeView(tag: TagView) {
    const idx = visitedViews.value.findIndex(v => v.path === tag.path)
    if (idx > -1) {
      visitedViews.value.splice(idx, 1)
    }
    if (tag.name && typeof tag.name === 'string') {
      cachedViews.value.delete(tag.name)
    }
  }

  function removeOtherViews(tag: TagView) {
    visitedViews.value = visitedViews.value.filter(v => v.meta?.affix || v.path === tag.path)
    cachedViews.value.clear()
    visitedViews.value.forEach(v => {
      if (v.name && typeof v.name === 'string') cachedViews.value.add(v.name)
    })
  }

  function removeAllViews() {
    visitedViews.value = visitedViews.value.filter(v => v.meta?.affix)
    cachedViews.value.clear()
    visitedViews.value.forEach(v => {
      if (v.name && typeof v.name === 'string') cachedViews.value.add(v.name)
    })
  }

  function removeRightViews(tag: TagView) {
    const idx = visitedViews.value.findIndex(v => v.path === tag.path)
    if (idx > -1) {
      const kept = visitedViews.value.filter((v, i) => i <= idx || v.meta?.affix)
      const removedNames = visitedViews.value
        .filter((v, i) => i > idx && !v.meta?.affix)
        .map(v => v.name)
      visitedViews.value = kept
      removedNames.forEach(n => {
        if (n && typeof n === 'string') cachedViews.value.delete(n)
      })
    }
  }

  function removeLeftViews(tag: TagView) {
    const idx = visitedViews.value.findIndex(v => v.path === tag.path)
    if (idx > -1) {
      const kept = visitedViews.value.filter((v, i) => i >= idx || v.meta?.affix)
      const removedNames = visitedViews.value
        .filter((v, i) => i < idx && !v.meta?.affix)
        .map(v => v.name)
      visitedViews.value = kept
      removedNames.forEach(n => {
        if (n && typeof n === 'string') cachedViews.value.delete(n)
      })
    }
  }

  return {
    visitedViews, cachedViews,
    addView, removeView, removeOtherViews, removeAllViews,
    removeRightViews, removeLeftViews, removeCachedView, resetViews
  }
})
