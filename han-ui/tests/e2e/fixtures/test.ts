import { test as base, expect, type Page } from '@playwright/test'
import { applyAuthSession, loginByApi, type AuthSession } from '../utils/auth'

export const e2eRuntime = {
  apiBaseUrl: process.env.PW_API_URL || 'http://10.18.35.95:9090',
  username: process.env.PW_USERNAME || 'admin',
  password: process.env.PW_PASSWORD || 'admin123',
  tenantId: process.env.PW_TENANT_ID || ''
}

interface E2EFixtures {
  authSession: AuthSession
  authenticatedPage: Page
}

/**
 * Han UI Playwright 基础夹具。
 *
 * 提供能力：
 * - 复用 API 登录建立稳定测试会话
 * - 统一注入前端 localStorage
 * - 统一进入首页并等待仪表盘完成首屏加载
 */
export const test = base.extend<E2EFixtures>({
  authSession: async ({ request }, use) => {
    const session = await loginByApi(request, {
      apiBaseUrl: e2eRuntime.apiBaseUrl,
      username: e2eRuntime.username,
      password: e2eRuntime.password,
      tenantId: e2eRuntime.tenantId || undefined
    })
    await use(session)
  },

  authenticatedPage: async ({ page, authSession }, use) => {
    await applyAuthSession(page, authSession, e2eRuntime.tenantId || undefined)
    await expect.poll(async () => {
      return page.evaluate(() => ({
        token: localStorage.getItem('Admin-Token'),
        refreshToken: localStorage.getItem('Admin-Refresh-Token'),
        userStore: localStorage.getItem('HAN-user')
      }))
    }).toMatchObject({
      token: authSession.accessToken,
      refreshToken: authSession.refreshToken
    })

    await page.goto('/')
    await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
    await page.waitForLoadState('networkidle')
    await expect(page.getByTestId('dashboard-page')).toBeVisible()
    await use(page)
  }
})

export { expect }
