import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

/**
 * 路由可达性判定收口。
 *
 * 背景：`meta.tier` / `meta.module` / `meta.feature` / `meta.permission` 原来只在
 * `Sidebar.filterRoutes()` 里生效，只能过滤菜单渲染，直接输 URL 仍然能进入任意页面。
 * 这里把判定抽出来，让路由守卫和侧边栏共用同一份规则，避免两处逻辑漂移。
 *
 * 统一的降级原则是「状态未知一律放行」：运行时能力还没加载完、后端没下发权限清单时
 * 不做拦截，否则后端一抖动就会把用户锁在门外。
 */
export const TIER_LEVEL: Record<string, number> = { small: 0, medium: 1, full: 2 }

/** 判定失败的原因，供守卫决定提示文案。 */
export type RouteDenyReason = 'tier' | 'module' | 'feature' | 'permission'

/** 路由访问控制相关的 meta 子集。 */
export interface RouteAccessMeta {
  tier?: string
  module?: string
  feature?: string
  permission?: string
}

function currentTierLevel(): number {
  const appStore = useAppStore()
  const tier = appStore.deployTier || import.meta.env.VITE_DEPLOY_TIER || 'full'
  return TIER_LEVEL[tier] ?? 2
}

/**
 * 部署档位是否覆盖该页面。缺省按 `small` 处理，即所有档位都可见。
 */
export function isTierAvailable(tier?: string): boolean {
  return (TIER_LEVEL[tier || 'small'] ?? 0) <= currentTierLevel()
}

/**
 * 模块是否启用。运行时能力未加载或后端未下发模块清单时放行。
 */
export function isModuleEnabled(moduleName?: string): boolean {
  if (!moduleName) {
    return true
  }
  const appStore = useAppStore()
  if (!appStore.capabilitiesLoaded || appStore.enabledModules.length === 0) {
    return true
  }
  return appStore.enabledModules.includes(moduleName)
}

/**
 * 功能开关是否打开。运行时能力未加载时放行。
 */
export function isFeatureAvailable(featureName?: string): boolean {
  if (!featureName) {
    return true
  }
  const appStore = useAppStore()
  if (!appStore.capabilitiesLoaded) {
    return true
  }
  return appStore.isFeatureEnabled(featureName)
}

/**
 * 权限标识是否命中。
 *
 * 用户资料还没拉回来时放行——那个窗口里权限清单必然是空的，
 * 此时拦截会把用户整站锁在 403。资料已加载则严格以后端下发的权限清单为准，
 * 与侧边栏原有的过滤强度保持一致。
 */
export function hasRoutePermission(permission?: string): boolean {
  if (!permission) {
    return true
  }
  const userStore = useUserStore()
  if (!userStore.userInfo) {
    return true
  }
  return userStore.hasPermission(permission)
}

/**
 * 对单条 meta 做完整判定，返回第一个不通过的维度。
 */
export function resolveMetaDenyReason(meta?: RouteAccessMeta): RouteDenyReason | null {
  if (!meta) {
    return null
  }
  if (!isTierAvailable(meta.tier)) {
    return 'tier'
  }
  if (!isModuleEnabled(meta.module)) {
    return 'module'
  }
  if (!isFeatureAvailable(meta.feature)) {
    return 'feature'
  }
  if (!hasRoutePermission(meta.permission)) {
    return 'permission'
  }
  return null
}

/**
 * 侧边栏用：判断一条静态路由记录是否可见。
 */
export function isRouteRecordAccessible(route: RouteRecordRaw): boolean {
  return resolveMetaDenyReason(route.meta as RouteAccessMeta | undefined) === null
}

/**
 * 路由守卫用：目标路由的整条 matched 链都要通过。
 *
 * 逐级校验是必要的——`/ai/model` 自身没有 `tier`，但父级 `/ai` 标了 `tier: 'full'`，
 * 只看叶子节点会让 small 档照样打开 full 档专属页。
 */
export function resolveRouteDenyReason(to: RouteLocationNormalized): RouteDenyReason | null {
  for (const record of to.matched) {
    const reason = resolveMetaDenyReason(record.meta as RouteAccessMeta | undefined)
    if (reason) {
      return reason
    }
  }
  return null
}

/** 拒绝原因对应的用户提示文案。 */
export const DENY_REASON_MESSAGE: Record<RouteDenyReason, string> = {
  tier: '当前部署档位不包含该功能',
  module: '该功能模块未启用',
  feature: '该功能未开启',
  permission: '没有访问该页面的权限'
}
