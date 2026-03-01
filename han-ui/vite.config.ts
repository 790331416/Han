import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import path from 'path'

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
      open: true,
      proxy: {
        '/dev-api/auth': {
          target: 'http://localhost:9200',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
        '/dev-api/system': {
          target: 'http://localhost:9201',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
        '/dev-api/tenant': {
          target: 'http://localhost:9202',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
        '/dev-api/job': {
          target: 'http://localhost:9204',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
        '/dev-api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        }
      }
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            elementPlus: ['element-plus', '@element-plus/icons-vue']
          }
        }
      }
    }
  }
})
