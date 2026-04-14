import { test, expect } from '../fixtures/test'

test.describe('auth login', () => {
  test('login page should show core controls', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByTestId('login-page')).toBeVisible()
    await expect(page.getByTestId('login-form')).toBeVisible()
    await expect(page.getByTestId('login-username')).toBeVisible()
    await expect(page.getByTestId('login-password')).toBeVisible()
    await expect(page.getByTestId('login-submit')).toBeVisible()
  })

  test('authenticated session should enter dashboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/')
    await authenticatedPage.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })

    await expect(authenticatedPage.getByTestId('dashboard-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('dashboard-shortcuts')).toBeVisible()
    await expect(authenticatedPage.getByTestId('navbar-user-menu')).toBeVisible()
  })

  test('dashboard should not show missing resource error after login', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/')
    await authenticatedPage.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
    await authenticatedPage.waitForResponse((response) => response.url().includes('/system/dashboard/charts'), { timeout: 15000 })

    await expect(authenticatedPage.getByTestId('dashboard-page')).toBeVisible()

    const resourceToast = authenticatedPage.locator('.el-message--error').filter({ hasText: '资源不存在' }).first()
    const toastAppeared = await resourceToast.waitFor({ state: 'visible', timeout: 2000 }).then(() => true).catch(() => false)

    expect(toastAppeared).toBeFalsy()
  })
})
