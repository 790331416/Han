import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PW_BASE_URL || 'http://127.0.0.1:3000'
const shouldUseRemoteBase = Boolean(process.env.PW_BASE_URL)
const defaultWorkers = Number(process.env.PW_WORKERS || '1')
const workers = Number.isNaN(defaultWorkers) || defaultWorkers < 1 ? 1 : defaultWorkers

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
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    testIdAttribute: 'data-testid'
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome']
      }
    }
  ],
  webServer: shouldUseRemoteBase
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 3000',
        url: 'http://127.0.0.1:3000',
        reuseExistingServer: true,
        env: {
          VITE_OPEN_BROWSER: 'false'
        },
        timeout: 120_000
      }
})
