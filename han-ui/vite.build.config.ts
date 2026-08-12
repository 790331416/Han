import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import path from 'path'
import { CHUNK_SIZE_WARNING_LIMIT, MANUAL_CHUNKS } from './build/chunks'

/**
 * 生产构建专用配置。
 *
 * 说明：
 * - 保持与 vite.config.ts 一致的打包行为
 * - 关闭 AutoImport / Components 的 d.ts 输出，避免构建阶段写入生成文件
 * - 不影响本地开发态的类型提示生成
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    base: env.VITE_PUBLIC_PATH || '/',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    plugins: [
      vue(),
      UnoCSS(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
        resolvers: [ElementPlusResolver()],
        dts: false,
        vueTemplate: true
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: false
      }),
      createSvgIconsPlugin({
        iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
        symbolId: 'icon-[dir]-[name]'
      })
    ],
    /**
     * 只 drop debugger，刻意保留 console。
     *
     * console 泄露 token 的问题已经在 `utils/request.ts` 里按字段脱敏解决；
     * 而权限指令降级、运行时能力回退、buildTree 挂载失败这几处 `console.warn`
     * 是生产环境的诊断依据，清掉会让线上问题彻底失去线索。
     */
    esbuild: {
      drop: ['debugger'] as const
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: CHUNK_SIZE_WARNING_LIMIT,
      rollupOptions: {
        output: {
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
          manualChunks: MANUAL_CHUNKS
        }
      }
    }
  }
})
