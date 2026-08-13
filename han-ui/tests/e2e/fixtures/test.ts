import { test as base, expect, request as playwrightRequest, type Page } from '@playwright/test'
import { applyAuthSession, loginByApi, type AuthSession } from '../utils/auth'

export const e2eRuntime = {
  apiBaseUrl: process.env.PW_API_URL || 'http://10.18.35.95:9090',
  username: process.env.PW_USERNAME || 'admin',
  password: process.env.PW_PASSWORD || 'admin123',
  tenantId: process.env.PW_TENANT_ID || ''
}

interface E2EFixtures {
  authSession: AuthSession
  isolatedAuthSession: AuthSession
  authenticatedPage: Page
  isolatedAuthenticatedPage: Page
}

async function createAuthSession(): Promise<AuthSession> {
  const apiRequest = await playwrightRequest.newContext()
  try {
    return await loginByApi(apiRequest, {
      apiBaseUrl: e2eRuntime.apiBaseUrl,
      username: e2eRuntime.username,
      password: e2eRuntime.password,
      tenantId: e2eRuntime.tenantId || undefined
    })
  } finally {
    await apiRequest.dispose()
  }
}

/**
 * Han UI 共用的 Playwright 夹具。
 *
 * 提供四项能力：
 * - worker 级的接口登录，保证同一 worker 内的会话稳定复用
 * - 把登录态写进 localStorage，供前端读取
 * - 已登录的 page，且不附带任何隐式跳转副作用
 * - test 级的独立会话，供退出登录这类会销毁会话的用例使用
 */
export const test = base.extend<E2EFixtures>({
  authSession: [async ({}, use) => {
    const session = await createAuthSession()
    await use(session)
  }, { scope: 'worker' }],

  isolatedAuthSession: async ({}, use) => {
    const session = await createAuthSession()
    await use(session)
  },

  authenticatedPage: async ({ page, authSession }, use) => {
    await applyAuthSession(page, authSession, e2eRuntime.tenantId || undefined)
    await use(page)
  },

  isolatedAuthenticatedPage: async ({ page, isolatedAuthSession }, use) => {
    await applyAuthSession(page, isolatedAuthSession, e2eRuntime.tenantId || undefined)
    await use(page)
  }
})

export { expect }
