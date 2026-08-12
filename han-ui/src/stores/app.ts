import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRuntimeCapabilities } from '@/api/system/runtime'
import { getToken } from '@/utils/auth'
import type { RuntimeCapability } from '@/types'

type DeployTier = 'small' | 'medium' | 'full'

const FALLBACK_TIER = (import.meta.env.VITE_DEPLOY_TIER || 'full') as DeployTier

/**
 * 运行时能力接口的等待上限。
 *
 * 请求层的全局超时是 30 秒，对这个「决定菜单能不能渲染」的接口来说太长：
 * 后端一慢，路由守卫就会把整次导航挂住。超过这个时间先用兜底值放行，
 * 真实结果到达后再覆盖，菜单会自动补齐。
 */
const CAPABILITY_LOAD_TIMEOUT_MS = 5000

export const useAppStore = defineStore('app', () => {
  const sidebar = ref({ opened: true, withoutAnimation: false })
  const device = ref<'desktop' | 'mobile'>('desktop')
  const size = ref<'default' | 'small' | 'large'>('default')
  const shortcutClicks = ref<Record<string, number>>({})

  const deployTier = ref<DeployTier>(FALLBACK_TIER)
  const enabledModules = ref<string[]>([])
  const optionalServices = ref<Record<string, boolean>>({})
  const featureFlags = ref<Record<string, boolean>>({})
  const capabilitiesLoaded = ref(false)
  /** 能力接口失败或超时后进入降级态：功能开关一律放行，避免静默丢掉大半菜单。 */
  const capabilitiesDegraded = ref(false)

  /** 并发调用共享同一个请求，避免 main.ts、路由守卫、Sidebar 各发一次。 */
  let pendingCapabilityLoad: Promise<void> | null = null

  const runtimeCapabilities = computed<RuntimeCapability>(() => ({
    tier: deployTier.value,
    enabledModules: enabledModules.value,
    optionalServices: optionalServices.value,
    featureFlags: featureFlags.value
  }))

  function toggleSidebar() {
    sidebar.value.opened = !sidebar.value.opened
    sidebar.value.withoutAnimation = false
  }

  function closeSidebar(withoutAnimation: boolean) {
    sidebar.value.opened = false
    sidebar.value.withoutAnimation = withoutAnimation
  }

  function toggleDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  function setSize(s: 'default' | 'small' | 'large') {
    size.value = s
  }

  function recordShortcutClick(path: string) {
    shortcutClicks.value[path] = (shortcutClicks.value[path] || 0) + 1
  }

  function getShortcutClickCount(path: string): number {
    return shortcutClicks.value[path] || 0
  }

  /**
   * 能力接口不可用时进入降级态。
   *
   * 只回退 `deployTier` 是不够的：`featureFlags` 保持空对象、`capabilitiesLoaded`
   * 又被置成 true，会让所有带 feature 的菜单（工作流、租户、开放平台、代码生成、AI、OSS）
   * 被判为关闭而整片消失，现象是「菜单莫名少了一半」且没有任何提示。
   */
  function markCapabilitiesDegraded(reason: string) {
    deployTier.value = FALLBACK_TIER
    capabilitiesDegraded.value = true
    console.warn(`[runtime-capabilities] ${reason}，已回退到 ${FALLBACK_TIER} 档并放行全部功能开关`)
    if (getToken()) {
      ElMessage.warning('运行时能力接口不可用，菜单已按默认配置展示')
    }
  }

  async function fetchRuntimeCapabilities(): Promise<void> {
    try {
      const res = await getRuntimeCapabilities()
      if (res.data) {
        deployTier.value = (res.data.tier || FALLBACK_TIER) as DeployTier
        enabledModules.value = res.data.enabledModules || []
        optionalServices.value = res.data.optionalServices || {}
        featureFlags.value = res.data.featureFlags || {}
        capabilitiesDegraded.value = false
      } else {
        markCapabilitiesDegraded('接口返回空数据')
      }
    } catch {
      markCapabilitiesDegraded('接口请求失败')
    } finally {
      capabilitiesLoaded.value = true
    }
  }

  async function loadRuntimeCapabilities(force = false) {
    if (capabilitiesLoaded.value && !force) return runtimeCapabilities.value

    if (!pendingCapabilityLoad) {
      pendingCapabilityLoad = fetchRuntimeCapabilities().finally(() => {
        pendingCapabilityLoad = null
      })
    }

    await Promise.race([
      pendingCapabilityLoad,
      new Promise<void>((resolve) => window.setTimeout(resolve, CAPABILITY_LOAD_TIMEOUT_MS))
    ])

    // 超时先放行，后台请求仍在继续，返回后会自动覆盖这里的兜底值。
    if (!capabilitiesLoaded.value) {
      markCapabilitiesDegraded(`接口 ${CAPABILITY_LOAD_TIMEOUT_MS} 毫秒内未返回`)
      capabilitiesLoaded.value = true
    }

    return runtimeCapabilities.value
  }

  function isFeatureEnabled(flag: string): boolean {
    if (capabilitiesDegraded.value) {
      return true
    }
    return !!featureFlags.value[flag]
  }

  /** 登出时清掉与账号相关的本地偏好，避免下一个账号沿用上一个人的快捷入口热度。 */
  function resetUserPreferences() {
    shortcutClicks.value = {}
  }

  return {
    sidebar, device, size, shortcutClicks,
    deployTier, enabledModules, optionalServices, featureFlags,
    capabilitiesLoaded, capabilitiesDegraded, runtimeCapabilities,
    toggleSidebar, closeSidebar, toggleDevice, setSize,
    recordShortcutClick, getShortcutClickCount, resetUserPreferences,
    loadRuntimeCapabilities, isFeatureEnabled
  }
}, {
  persist: {
    key: 'HAN-app',
    pick: ['sidebar.opened', 'size', 'shortcutClicks']
  }
})
