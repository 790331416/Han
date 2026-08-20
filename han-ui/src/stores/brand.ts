import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { BrandDisplayMode, SystemBrand } from '@/api/system/brand'

const DEFAULT_BRAND: SystemBrand = {
  fullName: 'HAN Cloud',
  shortName: 'HAN',
  displayMode: 'FULL_NAME',
  displayName: 'HAN Cloud',
  loginSubtitle: '企业级多租户微服务平台',
  logoUrl: ''
}

function normalizeBrand(value: Partial<SystemBrand> | null | undefined): SystemBrand {
  const fullName = cleanText(value?.fullName, DEFAULT_BRAND.fullName, 64)
  const shortName = cleanText(value?.shortName, DEFAULT_BRAND.shortName, 32)
  const displayMode: BrandDisplayMode = value?.displayMode === 'SHORT_NAME' ? 'SHORT_NAME' : 'FULL_NAME'
  return {
    fullName,
    shortName,
    displayMode,
    displayName: displayMode === 'SHORT_NAME' ? shortName : fullName,
    loginSubtitle: cleanText(value?.loginSubtitle, '', 128),
    logoUrl: typeof value?.logoUrl === 'string' ? value.logoUrl : ''
  }
}

function cleanText(value: unknown, fallback: string, maxLength: number): string {
  if (typeof value !== 'string') return fallback
  const normalized = value.trim()
  return normalized && normalized.length <= maxLength && !/[\u0000-\u001F\u007F]/.test(normalized)
    ? normalized
    : fallback
}

/** 所有页面共用的运行时品牌信息；不持久化，刷新后始终以服务端配置为准。 */
export const useBrandStore = defineStore('brand', () => {
  const brand = ref<SystemBrand>(DEFAULT_BRAND)
  const loaded = ref(false)

  const fullName = computed(() => brand.value.fullName)
  const shortName = computed(() => brand.value.shortName)
  const displayName = computed(() => brand.value.displayName)
  const loginSubtitle = computed(() => brand.value.loginSubtitle)
  const logoUrl = computed(() => brand.value.logoUrl)

  function apply(value: Partial<SystemBrand> | null | undefined) {
    brand.value = normalizeBrand(value)
    document.title = brand.value.displayName
    if (brand.value.logoUrl) {
      let favicon = document.querySelector<HTMLLinkElement>('link[rel~="icon"]')
      if (!favicon) {
        favicon = document.createElement('link')
        favicon.rel = 'icon'
        document.head.appendChild(favicon)
      }
      favicon.href = `${brand.value.logoUrl}?v=${Date.now()}`
    }
  }

  /**
   * 登录前读取公开品牌信息。使用原生 fetch，接口暂不可用时保持默认值，
   * 不触发通用请求器的 401 清会话逻辑。
   */
  async function loadPublicBrand() {
    try {
      const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
      const response = await fetch(`${baseUrl}/system/public/brand`, {
        cache: 'no-store',
        signal: AbortSignal.timeout(3000)
      })
      if (!response.ok) return
      const payload = await response.json()
      if (payload?.code === 200) apply(payload.data)
    } catch {
      // 公开品牌接口不可用时使用编译内默认值，不能阻塞登录页面。
    } finally {
      loaded.value = true
    }
  }

  return {
    brand, loaded, fullName, shortName, displayName, loginSubtitle, logoUrl,
    apply, loadPublicBrand
  }
})
