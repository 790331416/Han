import { test, expect } from '../fixtures/test'

test.describe('auth logout', () => {
  test('logout should return to login and block home access', async ({ isolatedAuthenticatedPage }) => {
    await isolatedAuthenticatedPage.goto('/')
    await isolatedAuthenticatedPage.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
    await expect(isolatedAuthenticatedPage.getByTestId('navbar-user-menu')).toBeVisible()

    await isolatedAuthenticatedPage.getByTestId('navbar-user-menu').click()
    await isolatedAuthenticatedPage.getByTestId('navbar-logout').click()
    await isolatedAuthenticatedPage.locator('.el-message-box__btns .el-button--primary').click()

    await expect(isolatedAuthenticatedPage).toHaveURL(/\/login/)
    await expect(isolatedAuthenticatedPage.getByTestId('login-page')).toBeVisible()

    await isolatedAuthenticatedPage.goto('/')
    await expect(isolatedAuthenticatedPage).toHaveURL(/\/login/)
  })
})
