import { test, expect, e2eRuntime } from '../fixtures/test'
import { createNotice, markAllNoticesRead } from '../utils/notice'

test.describe('notice center', () => {
  test('should load list, mark one read, and mark all read', async ({ authenticatedPage, request, authSession }) => {
    const firstTitle = `E2E notice ${Date.now()}`
    const secondTitle = `${firstTitle} - 2`

    await markAllNoticesRead(request, e2eRuntime.apiBaseUrl, authSession.accessToken)
    await createNotice(request, e2eRuntime.apiBaseUrl, authSession.accessToken, firstTitle)
    await createNotice(request, e2eRuntime.apiBaseUrl, authSession.accessToken, secondTitle)

    await authenticatedPage.goto('/')
    await authenticatedPage.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })

    await authenticatedPage.getByTestId('notify-bell-trigger').click()
    await expect(authenticatedPage.getByTestId('notify-panel')).toBeVisible()

    const firstItem = authenticatedPage.locator(`[data-notice-title="${firstTitle}"]`)
    const secondItem = authenticatedPage.locator(`[data-notice-title="${secondTitle}"]`)

    await expect(firstItem).toBeVisible()
    await expect(secondItem).toBeVisible()
    await expect(firstItem).toHaveClass(/is-unread/)
    await expect(secondItem).toHaveClass(/is-unread/)

    await firstItem.click()
    await expect(authenticatedPage.getByTestId('notify-detail')).toBeVisible()
    await expect(authenticatedPage.getByTestId('notify-detail-title')).toContainText(firstTitle)

    await authenticatedPage.locator('.el-dialog__headerbtn').last().click()
    await authenticatedPage.getByTestId('notify-bell-trigger').click()
    await expect(firstItem).not.toHaveClass(/is-unread/)

    await authenticatedPage.getByTestId('notify-mark-all-read').click()
    await expect(firstItem).not.toHaveClass(/is-unread/)
    await expect(secondItem).not.toHaveClass(/is-unread/)
  })
})
