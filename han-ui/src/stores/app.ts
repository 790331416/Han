import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getRuntimeCapabilities } from '@/api/system/runtime'
import type { RuntimeCapability } from '@/types'

type DeployTier = 'small' | 'medium' | 'full'

const FALLBACK_TIER = (import.meta.env.VITE_DEPLOY_TIER || 'full') as DeployTier

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

  async function loadRuntimeCapabilities(force = false) {
    if (capabilitiesLoaded.value && !force) return runtimeCapabilities.value
    try {
      const res = await getRuntimeCapabilities()
      if (res.data) {
        deployTier.value = (res.data.tier || FALLBACK_TIER) as DeployTier
        enabledModules.value = res.data.enabledModules || []
        optionalServices.value = res.data.optionalServices || {}
        featureFlags.value = res.data.featureFlags || {}
      }
    } catch {
      deployTier.value = FALLBACK_TIER
    } finally {
      capabilitiesLoaded.value = true
    }
    return runtimeCapabilities.value
  }

  function isFeatureEnabled(flag: string): boolean {
    return !!featureFlags.value[flag]
  }

  return {
    sidebar, device, size, shortcutClicks,
    deployTier, enabledModules, optionalServices, featureFlags, capabilitiesLoaded, runtimeCapabilities,
    toggleSidebar, closeSidebar, toggleDevice, setSize,
    recordShortcutClick, getShortcutClickCount,
    loadRuntimeCapabilities, isFeatureEnabled
  }
}, {
  persist: {
    key: 'HAN-app',
    pick: ['sidebar.opened', 'size', 'shortcutClicks']
  }
})
