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
  const title = [...route.matched]
    .reverse()
    .map((record) => record.meta?.title)
    .find((value): value is string => typeof value === 'string' && value.trim().length > 0)

  return {
    path: route.path,
    fullPath: route.fullPath,
    name: route.name,
    title: title || '首页',
    meta: { ...route.meta },
    query: { ...route.query }
  }
}

export const useTagsViewStore = defineStore('tagsView', () => {
  const visitedViews = ref<TagView[]>([])
  const cachedViews = ref<Set<string>>(new Set())

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
    removeRightViews, removeLeftViews
  }
})
