import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import i18n from './locales'
import { setupDirectives } from '@/directive'
import { formatDate } from '@/utils/request'
import { useAppStore } from '@/stores/app'

import 'virtual:uno.css'
import 'element-plus/dist/index.css'
import '@/assets/styles/index.scss'

async function bootstrap() {
  const app = createApp(App)

  const pinia = createPinia()
  pinia.use(piniaPluginPersistedstate)
  app.use(pinia)

  const elLocale = i18n.global.locale.value === 'en-US' ? en : zhCn
  app.use(ElementPlus, { locale: elLocale })
  app.use(i18n)

  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  setupDirectives(app)

  app.config.globalProperties.$formatDate = formatDate

  app.use(router)
  app.mount('#app')

  /**
   * 运行时能力必须在挂载之后再拉。
   *
   * 之前是 `await` 在 `app.mount()` 前面，而请求层全局超时是 30 秒、`#app` 又是空 div，
   * 后端一慢用户就盯着纯白页面最长 30 秒。现在先把界面渲染出来，能力接口在后台加载，
   * 路由守卫和侧边栏都会等它落地后再决定菜单与页面可达性。
   */
  void useAppStore(pinia).loadRuntimeCapabilities()
}

/**
 * 骨架屏写在 `index.html` 的 `#app` 里，`app.mount()` 会自然把它替换掉。
 * 只有启动失败这条路径需要手动接管，否则用户会一直盯着一个转不完的圈。
 */
function renderBootFailure(error: unknown) {
  console.error('应用启动失败:', error)
  const splash = document.getElementById('app-boot-splash')
  if (splash) {
    splash.innerHTML =
      '<p class="boot-splash-text">页面加载失败，请刷新重试；若持续失败请联系管理员。</p>'
  }
}

bootstrap().catch(renderBootFailure)
