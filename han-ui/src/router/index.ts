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
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', affix: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true }
      }
    ]
  },
  {
    path: '/job',
    name: 'Job',
    component: () => import('@/layout/index.vue'),
    redirect: '/job/list',
    meta: { title: '任务调度', icon: 'Timer', tier: 'small', module: 'job' },
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
    path: '/workflow',
    name: 'Workflow',
    component: () => import('@/layout/index.vue'),
    redirect: '/workflow/definition',
    meta: { title: '工作流', icon: 'Connection', tier: 'medium', module: 'workflow', feature: 'workflow' },
    children: [
      {
        path: 'definition',
        name: 'ProcessDefinition',
        component: () => import('@/views/workflow/definition/index.vue'),
        meta: { title: '流程定义', icon: 'Document', permission: 'workflow:definition:list' }
      },
      {
        path: 'instance',
        name: 'ProcessInstance',
        component: () => import('@/views/workflow/instance/index.vue'),
        meta: { title: '流程实例', icon: 'Histogram', permission: 'workflow:instance:list' }
      },
      {
        path: 'todo',
        name: 'TodoTask',
        component: () => import('@/views/workflow/task/index.vue'),
        meta: { title: '待办任务', icon: 'Bell', permission: 'workflow:task:todo' }
      },
      {
        path: 'done',
        name: 'DoneTask',
        component: () => import('@/views/workflow/task/done.vue'),
        meta: { title: '已办任务', icon: 'Finished', permission: 'workflow:task:done' }
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
        path: 'role/authUser',
        name: 'AuthUser',
        component: () => import('@/views/system/role/authUser.vue'),
        meta: { title: '分配用户', hidden: true, activeMenu: '/system/role' }
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
      },
      {
        path: 'post',
        name: 'Post',
        component: () => import('@/views/system/post/index.vue'),
        meta: { title: '岗位管理', icon: 'Postcard', permission: 'system:post:list' }
      },
      {
        path: 'dict',
        name: 'Dict',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '字典管理', icon: 'Notebook', permission: 'system:dict:list' }
      },
      {
        path: 'dict-data',
        name: 'DictData',
        component: () => import('@/views/system/dict/data.vue'),
        meta: { title: '字典数据', icon: 'Notebook', hidden: true, permission: 'system:dict:list' }
      },
      {
        path: 'tenant',
        name: 'Tenant',
        component: () => import('@/views/system/tenant/index.vue'),
        meta: { title: '租户管理', icon: 'Coin', permission: 'tenant:list', tier: 'medium', module: 'tenant', feature: 'tenantSelect' }
      },
      {
        path: 'tenant-package',
        name: 'TenantPackage',
        component: () => import('@/views/system/tenant/package.vue'),
        meta: { title: '租户套餐', icon: 'ShoppingBag', permission: 'tenant:package:list', tier: 'medium', module: 'tenant', feature: 'tenantSelect' }
      },
      {
        path: 'tenant-quota',
        name: 'TenantQuota',
        component: () => import('@/views/system/tenant/quota.vue'),
        meta: { title: '资源配额', icon: 'PieChart', permission: 'tenant:quota:query', tier: 'medium', module: 'tenant', feature: 'tenantSelect' }
      },
      {
        path: 'config',
        name: 'Config',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '参数配置', icon: 'Tools', permission: 'system:config:list' }
      },
      {
        path: 'notice',
        name: 'Notice',
        component: () => import('@/views/system/notice/index.vue'),
        meta: { title: '通知公告', icon: 'Bell', permission: 'system:notice:list' }
      },
      {
        path: 'operlog',
        name: 'OperLog',
        component: () => import('@/views/system/operlog/index.vue'),
        meta: { title: '操作日志', icon: 'Document', permission: 'system:operlog:list' }
      },
      {
        path: 'loginlog',
        name: 'LoginLog',
        component: () => import('@/views/system/loginlog/index.vue'),
        meta: { title: '登录日志', icon: 'Tickets', permission: 'system:loginlog:list' }
      },
      {
        path: 'online',
        name: 'Online',
        component: () => import('@/views/system/online/index.vue'),
        meta: { title: '在线用户', icon: 'Connection', permission: 'monitor:online:list' }
      },
      {
        path: 'server',
        name: 'Server',
        component: () => import('@/views/system/server/index.vue'),
        meta: { title: '服务监控', icon: 'Monitor', permission: 'system:monitor:server' }
      },
      {
        path: 'cache-monitor',
        name: 'CacheMonitor',
        component: () => import('@/views/system/cache-monitor/index.vue'),
        meta: { title: '缓存监控', icon: 'Coin', permission: 'system:monitor:cache' }
      },
      {
        path: 'oss-config',
        name: 'OssConfig',
        component: () => import('@/views/system/oss-config/index.vue'),
        meta: { title: 'OSS配置', icon: 'Upload', permission: 'system:oss:list', tier: 'medium', feature: 'ossConfig' }
      }
    ]
  },
  {
    path: '/ai',
    name: 'AI',
    component: () => import('@/layout/index.vue'),
    redirect: '/ai/model',
    meta: { title: 'AI智能', icon: 'MagicStick', tier: 'full', module: 'ai', feature: 'ai' },
    children: [
      {
        path: 'model',
        name: 'AiModel',
        component: () => import('@/views/ai/model/index.vue'),
        meta: { title: 'AI模型管理', icon: 'Cpu', permission: 'ai:model:list' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/ai/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'Collection', permission: 'ai:kb:list' }
      },
      {
        path: 'mcp',
        name: 'McpServer',
        component: () => import('@/views/ai/mcp/index.vue'),
        meta: { title: 'MCP管理', icon: 'Link', permission: 'ai:mcp:list' }
      },
      {
        path: 'agent',
        name: 'AiAgent',
        component: () => import('@/views/ai/agent/index.vue'),
        meta: { title: '智能体', icon: 'UserFilled', permission: 'ai:agent:list' }
      },
      {
        path: 'workflow',
        name: 'AiWorkflow',
        component: () => import('@/views/ai/workflow/index.vue'),
        meta: { title: 'AI工作流', icon: 'ChatDotRound', permission: 'ai:workflow:list' }
      },
      {
        path: 'workflow/designer/:workflowId',
        name: 'AiWorkflowDesigner',
        component: () => import('@/views/ai/workflow/designer.vue'),
        meta: { title: '流程设计器', icon: 'SetUp', permission: 'ai:workflow:edit', hidden: true }
      },
      {
        path: 'prompt',
        name: 'AiPrompt',
        component: () => import('@/views/ai/prompt/index.vue'),
        meta: { title: 'Prompt模板', icon: 'Document', permission: 'ai:prompt:list' }
      },
      {
        path: 'token',
        name: 'AiToken',
        component: () => import('@/views/ai/token/index.vue'),
        meta: { title: 'Token统计', icon: 'DataAnalysis', permission: 'ai:token:stats' }
      },
      // {
      //   path: 'graph/:kbId',
      //   name: 'AiGraph',
      //   component: () => import('@/views/ai/graph/index.vue'),
      //   meta: { title: '知识图谱', icon: 'Share', permission: 'ai:graph:query', hidden: true }
      // },
      {
        path: 'chat',
        name: 'AiChat',
        component: () => import('@/views/ai/chat/index.vue'),
        meta: { title: 'AI对话', icon: 'ChatLineSquare' }
      }
    ]
  },
  {
    path: '/open',
    name: 'Open',
    component: () => import('@/layout/index.vue'),
    redirect: '/open/app',
    meta: { title: '开放平台', icon: 'Platform', tier: 'medium', module: 'open', feature: 'openPlatform' },
    children: [
      {
        path: 'app',
        name: 'OpenApp',
        component: () => import('@/views/open/app/index.vue'),
        meta: { title: '应用管理', icon: 'Grid', permission: 'open:app:list' }
      }
    ]
  },
  // {
  //   path: '/embed/chat/:agentId',
  //   name: 'EmbedChat',
  //   component: () => import('@/views/ai/embed/chat.vue'),
  //   meta: { title: 'AI对话' }
  // }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

// 白名单
const whiteList = ['/login', '/404']

// 嵌入式对话路径前缀（免登录）
const isEmbedPath = (path: string) => path.startsWith('/embed/')

// 路由守卫
router.beforeEach(async (to, _from, next) => {
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
    if (whiteList.includes(to.path) || isEmbedPath(to.path)) {
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
