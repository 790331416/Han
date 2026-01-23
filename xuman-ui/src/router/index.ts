import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

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
    path: '/404',
    name: '404',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', affix: true }
      }
    ]
  },
  {
    path: '/job',
    name: 'Job',
    component: () => import('@/layout/index.vue'),
    redirect: '/job/list',
    meta: { title: '任务调度', icon: 'Timer' },
    children: [
      {
        path: 'list',
        name: 'JobList',
        component: () => import('@/views/job/index.vue'),
        meta: { title: '定时任务', icon: 'Clock', permission: 'job:list' }
      },
      {
        path: 'log',
        name: 'JobLog',
        component: () => import('@/views/job/log.vue'),
        meta: { title: '调度日志', icon: 'Document', permission: 'job:log:list' }
      }
    ]
  },
  {
    path: '/system',
    name: 'System',
    component: () => import('@/layout/index.vue'),
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', permission: 'system:user:list' }
      },
      {
        path: 'role',
        name: 'Role',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', permission: 'system:role:list' }
      },
      {
        path: 'menu',
        name: 'Menu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'Menu', permission: 'system:menu:list' }
      },
      {
        path: 'dept',
        name: 'Dept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '部门管理', icon: 'OfficeBuilding', permission: 'system:dept:list' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

// 白名单
const whiteList = ['/login', '/404']

// 路由守卫
router.beforeEach(async (to, from, next) => {
  NProgress.start()
  
  const userStore = useUserStore()
  
  if (userStore.token) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      if (!userStore.userInfo) {
        try {
          await userStore.getInfo()
          next({ ...to, replace: true })
        } catch (error) {
          userStore.resetToken()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
        }
      } else {
        next()
      }
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
