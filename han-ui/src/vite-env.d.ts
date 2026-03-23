/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_PORT: string
  readonly VITE_PUBLIC_PATH: string
  readonly VITE_API_URL: string
  readonly VITE_APP_BASE_API: string
  readonly VITE_DEPLOY_TIER: 'small' | 'medium' | 'full'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue'
  export const Editor: DefineComponent<any, any, any>
  export const Toolbar: DefineComponent<any, any, any>
}
