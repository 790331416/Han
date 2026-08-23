import { createRouter, createWebHistory, RouterView, type RouteRecordRaw } from 'vue-router'
import { ref } from 'vue'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getRouters } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { RouteMenu } from '@/types'
import type { RequestRuntimeError } from '@/utils/request'

NProgress.configure({ showSpinner: false })

// 公共路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/open/vendor-apply',
    name: 'VendorApply',
    component: () => import('@/views/open/vendor-apply/index.vue'),
    meta: { title: '厂商入驻申请', hidden: true }
  },
  {
    path: '/social/callback',
    name: 'SocialCallback',
    component: () => import('@/views/login/social-callback.vue'),
    meta: { title: '第三方登录', hidden: true }
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/redirect',
    component: () => import('@/layout/index.vue'),
    meta: { hidden: true },
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue'),
        meta: { noTagsView: true }
      }
    ]
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true }
      }
    ]
  },
  {
    path: '/chat/share/:shareKey',
    name: 'ShareChat',
    component: () => import('@/views/share/chat.vue'),
    meta: { title: 'AI对话', hidden: true }
  },
]

const viewModules = import.meta.glob('/src/views/**/*.vue')
const componentAliases: Record<string, string> = {
  'monitor/online/index': 'system/online/index',
  'monitor/operlog/index': 'system/operlog/index',
  'monitor/loginlog/index': 'system/loginlog/index',
  'monitor/cache/index': 'system/cache-monitor/index',
  'monitor/server/index': 'system/server/index',
  'tenant/list/index': 'system/tenant/index',
  'tenant/package/index': 'system/tenant/package',
  'tenant/quota/index': 'system/tenant/quota',
  'education/region/index': 'education/EducationRegionTreePage',
  'education/promotion/index': 'education/EducationPromotionPage'
}
const iconAliases: Record<string, string> = {
  system: 'Setting',
  peoples: 'UserFilled',
  'tree-table': 'Grid',
  tree: 'Share',
  post: 'Postcard',
  dict: 'Notebook',
  edit: 'Edit',
  message: 'Message',
  client: 'Platform',
  tool: 'Tools',
  list: 'List',
  component: 'Grid',
  'office-building': 'OfficeBuilding',
  collection: 'Collection',
  user: 'User',
  tickets: 'Tickets',
  clock: 'Clock',
  logininfor: 'Tickets',
  online: 'Connection',
  form: 'Document',
  redis: 'Coin',
  server: 'Monitor',
  code: 'Tools',
  swagger: 'Document',
  school: 'School',
  notebook: 'Notebook',
  monitor: 'Monitor',
  calendar: 'Calendar',
  house: 'House',
  lock: 'Lock',
  top: 'Top',
  'document-checked': 'DocumentChecked',
  'shopping-cart': 'ShoppingCart'
}

function menuIcon(icon?: string | null) {
  if (!icon || icon === '#') return 'Menu'
  return iconAliases[icon] || icon.replace(/(^|-)([a-z])/g, (_, __, letter) => letter.toUpperCase())
}

function cleanPath(path?: string | null) {
  return String(path || '')
    .trim()
    .replace(/\\/g, '/')
    .replace(/[?#].*$/, '')
    .replace(/^\/+|\/+$/g, '')
}

/**
 * 菜单表里的子路径历史上既有相对值，也有带父级前缀的绝对值。
 * 路由注册时统一转换成当前父路由下的相对段，避免 /system/system/user。
 */
export function normalizeMenuPath(path: string | null | undefined, parentPath = '') {
  const value = cleanPath(path)
  const parent = cleanPath(parentPath)
  if (!parent) return value || '/'
  if (value === parent) return ''
  if (value.startsWith(`${parent}/`)) return value.slice(parent.length + 1)
  return value
}

export function resolveMenuPath(parentPath: string, childPath?: string) {
  const parent = cleanPath(parentPath)
  const child = cleanPath(childPath)
  if (!child) return `/${parent}`.replace(/\/+/g, '/') || '/'
  if (!parent || child === parent || child.startsWith(`${parent}/`)) {
    return `/${child}`.replace(/\/+/g, '/')
  }
  return `/${parent}/${child}`.replace(/\/+/g, '/')
}

function viewComponent(component?: string | null) {
  if (!component || component === 'Layout' || component === 'ParentView') return RouterView
  const normalizedComponent = component
    .trim()
    .replace(/\\/g, '/')
    .replace(/^@\/views\//, '')
    .replace(/^\/?src\/views\//, '')
    .replace(/^\/+|\/+$/g, '')
    .replace(/\.vue$/i, '')
  const normalized = componentAliases[normalizedComponent] || normalizedComponent
  const loader = viewModules[`/src/views/${normalized}.vue`]
  // DB 中不存在的组件安全落到 404，不能因为组件路径异常暴露静态页面。
  return loader || (() => import('@/views/error/404.vue'))
}

function toDynamicRoute(menu: RouteMenu, parentPath = ''): RouteRecordRaw {
  const path = normalizeMenuPath(menu.path, parentPath)
  const fullPath = resolveMenuPath(parentPath, path).replace(/^\//, '')
  const children = (menu.children || []).map((child) => toDynamicRoute(child, fullPath))
  return {
    path,
    name: menu.name || `menu-${menu.id}`,
    component: viewComponent(menu.component),
    meta: {
      title: menu.meta?.title || menu.menuName,
      icon: menuIcon(menu.meta?.icon || menu.icon),
      hidden: Boolean(menu.hidden) || menu.visible === 1,
      noCache: Boolean(menu.meta?.noCache)
    },
    ...(children.length ? { children } : {}),
    ...(menu.redirect && menu.redirect !== 'noRedirect' ? { redirect: menu.redirect } : {})
  } as RouteRecordRaw
}

// 仅保留公共路由和应用外壳，业务路由在登录后从菜单树注册。
const initialRoutes = constantRoutes.filter((route) =>
  ['Login', 'VendorApply', 'SocialCallback', '404', 'Layout', 'ShareChat'].includes(String(route.name)) || route.path === '/redirect'
)
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: initialRoutes,
  scrollBehavior: () => ({ top: 0 })
})

const notFoundRoute: RouteRecordRaw = { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/404', meta: { hidden: true } }
export const dynamicMenuRoutes = ref<RouteRecordRaw[]>([])
let dynamicRouteNames: string[] = []
let notFoundAdded = false

export function clearDynamicRoutes() {
  dynamicRouteNames.forEach((name) => router.hasRoute(name) && router.removeRoute(name))
  dynamicRouteNames = []
  dynamicMenuRoutes.value = []
  if (notFoundAdded) {
    router.removeRoute(String(notFoundRoute.name))
    notFoundAdded = false
  }
}

export async function loadDynamicRoutes() {
  clearDynamicRoutes()
  const response = await getRouters()
  const routes = (response.data || []).flatMap((menu) => {
    const route = toDynamicRoute(menu)
    // 顶级菜单由后端包装成 path="/" + 单个实际页面；展开后首页仍由菜单表控制，
    // 同时避免在 Layout 下再嵌套一个空的根路由。
    if (route.path === '/' && route.children?.length === 1) {
      return route.children
    }
    return [route]
  })
  routes.forEach((route) => {
    router.addRoute('Layout', route)
    if (route.name) dynamicRouteNames.push(String(route.name))
  })
  dynamicMenuRoutes.value = routes
  router.addRoute(notFoundRoute)
  notFoundAdded = true
  return routes
}

// 白名单
const whiteList = ['/login', '/open/vendor-apply', '/social/callback', '/404']

// 嵌入式/公开分享对话路径前缀（免登录）
const isEmbedPath = (path: string) => path.startsWith('/embed/') || path.startsWith('/chat/share/')
let routesLoaded = false

function firstMenuPath(routes: RouteRecordRaw[]): string | null {
  for (const route of routes) {
    if (route.meta?.hidden) continue
    const child = route.children?.length ? firstMenuPath(route.children) : null
    if (child) return `${route.path === '/' ? '' : route.path}/${child}`.replace(/\/+/g, '/')
    if (!route.children?.length) return `/${String(route.path).replace(/^\/+/, '')}`
  }
  return null
}

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  
  const userStore = useUserStore()
  
  if (!userStore.token) {
    if (whiteList.includes(to.path) || isEmbedPath(to.path)) next()
    else next(`/login?redirect=${to.path}`)
    NProgress.done()
    return
  }
  if (to.path === '/login') {
    next({ path: '/' })
    NProgress.done()
    return
  }
  if (to.path === '/404') {
    next({ path: '/', replace: true })
    NProgress.done()
    return
  }
  try {
    if (!userStore.userInfo) {
      clearDynamicRoutes()
      routesLoaded = false
      await userStore.getInfo()
    }
    if (!routesLoaded) {
      const routes = await loadDynamicRoutes()
      routesLoaded = true
      const firstPath = firstMenuPath(routes)
      next(to.path === '/' && firstPath ? { path: firstPath, replace: true } : { ...to, replace: true })
      return
    }
    next()
  } catch (error) {
    const requestError = error as RequestRuntimeError
    const isUnauthorized = requestError?.unauthorized === true || requestError?.httpStatus === 401 || requestError?.bizCode === 401
    if (isUnauthorized) {
      userStore.resetToken()
      routesLoaded = false
      clearDynamicRoutes()
      next(`/login?redirect=${to.path}`)
    } else {
      next()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

router.onError((error, to) => {
  NProgress.done()
  const message = String(error instanceof Error ? error.message : error)
  const chunkLoadFailed = /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk|ChunkLoadError|fetch.*module/i.test(message)
  if (!chunkLoadFailed) {
    console.error('路由加载失败:', error)
    return
  }
  const reloadKey = `HAN-route-reload:${to.fullPath}`
  if (!sessionStorage.getItem(reloadKey)) {
    sessionStorage.setItem(reloadKey, '1')
    window.location.reload()
    return
  }
  sessionStorage.removeItem(reloadKey)
  console.error('页面版本已更新，请刷新后重试:', error)
})

export default router
