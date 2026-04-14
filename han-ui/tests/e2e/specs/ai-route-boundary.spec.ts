import { test, expect } from '../fixtures/test'

test('ai graph route should remain unavailable on full tier', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  await page.goto('/')
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
  await expect(page.getByTestId('dashboard-page')).toBeVisible()

  await page.goto('/ai/graph/1')

  await expect(page.getByTestId('error-404-page')).toBeVisible()
  await expect(page.locator('body')).toContainText('抱歉，您访问的页面不存在')
})

test('embed chat route should remain unavailable even though embed path is login-whitelisted', async ({ page }) => {
  await page.goto('/embed/chat/123')

  await expect(page.getByTestId('error-404-page')).toBeVisible()
  await expect(page.locator('body')).toContainText('抱歉，您访问的页面不存在')
})
