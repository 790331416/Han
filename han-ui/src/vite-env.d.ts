/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/**
 * 与 `.env.*` 实际生效的变量保持一致。
 *
 * 注意：`vite/client` 自带的 `ImportMetaEnv` 有 `[key: string]: any` 索引签名，
 * 接口声明合并后索引签名仍然存在，所以拼错变量名不会在 `vue-tsc` 阶段报错。
 * 这里能做的是把清单维护准确，新增变量务必同步登记。
 */
interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_PORT: string
  readonly VITE_PUBLIC_PATH: string
  readonly VITE_APP_BASE_API: string
  readonly VITE_DEPLOY_TIER: 'small' | 'medium' | 'full'
  readonly VITE_DEV_PROXY_TARGET: string
  readonly VITE_OPEN_BROWSER: string
  readonly VITE_ENABLE_DEBUG_IDENTITY_HEADER: string
  readonly VITE_PERMISSION_ENFORCE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue'
  export const Editor: DefineComponent<any, any, any>
  export const Toolbar: DefineComponent<any, any, any>
}
