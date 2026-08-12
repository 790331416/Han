/**
 * 打包切分策略，`vite.config.ts` 与 `vite.build.config.ts` 共用一份，避免两处漂移。
 *
 * 原来只拆了 vue 与 element-plus，echarts、@vue-flow/*、@wangeditor/editor、marked、
 * highlight.js 这些重库会各自并进对应的懒加载视图 chunk（dashboard、ai/workflow/designer、
 * system/notice、ai/chat），单个页面首屏包偏大。
 */
export const MANUAL_CHUNKS: Record<string, string[]> = {
  vue: ['vue', 'vue-router', 'pinia'],
  elementPlus: ['element-plus', '@element-plus/icons-vue'],
  charts: ['echarts'],
  editor: ['@wangeditor/editor', '@wangeditor/editor-for-vue'],
  flow: ['@vue-flow/core', '@vue-flow/background', '@vue-flow/controls', '@vue-flow/minimap'],
  markdown: ['marked', 'highlight.js']
}

/**
 * 体积告警阈值。原值 2000 KB 把 Vite 默认的 500 KB 抬高了四倍，等于关掉了预警；
 * 800 KB 是拆分后仍然留有余量、又能真正告警的量级。
 */
export const CHUNK_SIZE_WARNING_LIMIT = 800
