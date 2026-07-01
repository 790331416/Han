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

  app.config.globalProperties.$formatDate = formatDate

  const appStore = useAppStore(pinia)
  await appStore.loadRuntimeCapabilities()

  app.use(router)
  app.mount('#app')
}

bootstrap()
