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
 * Shared Playwright fixtures for Han UI.
 *
 * Provides:
 * - worker-scoped API login for stable authenticated sessions
 * - localStorage bootstrap for frontend auth state
 * - an authenticated page without implicit navigation side effects
 * - test-scoped isolated sessions for destructive auth flows such as logout
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
