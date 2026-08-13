import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import path from 'path'
import { CHUNK_SIZE_WARNING_LIMIT, MANUAL_CHUNKS } from './build/chunks'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const shouldOpenBrowser = env.VITE_OPEN_BROWSER !== 'false'
  
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
        dts: 'src/types/auto-imports.d.ts',
        vueTemplate: true
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/components.d.ts'
      }),
      createSvgIconsPlugin({
        iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
        symbolId: 'icon-[dir]-[name]'
      })
    ],
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 80,
      open: shouldOpenBrowser,
      proxy: {
        '/dev-api': {
          // 换开发环境时改 .env.development 的 VITE_DEV_PROXY_TARGET，不用改代码
          target: env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:9090',
          changeOrigin: true,
          rewrite: (p: string) => p.replace(/^\/dev-api/, '')
        }
      }
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
