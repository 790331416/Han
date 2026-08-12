import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PW_BASE_URL || 'http://127.0.0.1:3000'
const shouldUseRemoteBase = Boolean(process.env.PW_BASE_URL)
const defaultWorkers = Number(process.env.PW_WORKERS || '1')
const workers = Number.isNaN(defaultWorkers) || defaultWorkers < 1 ? 1 : defaultWorkers
const defaultRetries = Number(process.env.PW_RETRIES || (shouldUseRemoteBase ? '1' : '0'))
const retries = Number.isNaN(defaultRetries) || defaultRetries < 0 ? 0 : defaultRetries
const traceMode = process.env.PW_TRACE_MODE || (retries > 0 ? 'on-first-retry' : 'retain-on-failure')

/**
 * 纯逻辑用例（不需要真实页面）可以用 PW_SKIP_WEBSERVER=true 跳过拉起 Vite。
 * 原来只要不设 PW_BASE_URL 就必然启动 dev server，这类用例没法独立运行。
 */
const shouldSkipWebServer = shouldUseRemoteBase || process.env.PW_SKIP_WEBSERVER === 'true'

/**
 * Han UI Playwright 配置
 *
 * 默认模式：
 * - 本地启动 Vite，并复用 95 环境的后端网关
 *
 * 远端联调模式：
 * - 通过设置 PW_BASE_URL / PW_API_URL 直连 95 服务器页面与网关
 */
export default defineConfig({
  testDir: './tests/e2e/specs',
  fullyParallel: false,
  timeout: 60_000,
  retries,
  expect: {
    timeout: 10_000
  },
  workers,
  outputDir: './output/playwright/test-results',
  reporter: [
    ['list'],
    ['html', { outputFolder: './output/playwright/report', open: 'never' }],
    ['json', { outputFile: './output/playwright/report/results.json' }]
  ],
  use: {
    baseURL,
    trace: traceMode as 'on-first-retry' | 'retain-on-failure' | 'off' | 'on' | 'on-all-retries',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    testIdAttribute: 'data-testid',
    // 复制类交互（如开放平台应用密钥复制）需要剪贴板权限，否则用例无法覆盖
    permissions: ['clipboard-read', 'clipboard-write']
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome']
      }
    }
  ],
  webServer: shouldSkipWebServer
    ? undefined
    : {
        // 仓库统一用 pnpm（有 pnpm-lock.yaml / pnpm-workspace.yaml，Dockerfile 与 docs/02 也是 pnpm），
        // 只装了 pnpm 的 CI 环境用 npm run dev 会直接起不来
        command: 'pnpm dev --host 127.0.0.1 --port 3000',
        url: 'http://127.0.0.1:3000',
        reuseExistingServer: true,
        env: {
          VITE_OPEN_BROWSER: 'false'
        },
        timeout: 120_000
      }
})
