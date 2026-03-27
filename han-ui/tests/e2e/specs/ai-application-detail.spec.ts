import { test, expect } from '../fixtures/test'

test('ai application detail should open from home and expose overview settings and debug tabs', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/application')
  await page.waitForLoadState('networkidle')

  await expect(page.getByTestId('ai-application-card').first()).toBeVisible()
  await expect(page.getByTestId('ai-application-detail-link').first()).toBeVisible()

  await page.getByTestId('ai-application-detail-link').first().click()

  await page.waitForURL(/\/ai\/application\/(agent|workflow)\/.+/, {
    timeout: 30000,
    waitUntil: 'domcontentloaded'
  })
  await expect(page.getByTestId('ai-application-detail-page')).toBeVisible()
  await expect(page.getByTestId('ai-application-detail-title')).toBeVisible()
  await expect(page.getByTestId('ai-application-overview-panel')).toBeVisible()

  await page.getByRole('tab', { name: '设置' }).click()
  await expect(page.getByTestId('ai-application-settings-panel')).toBeVisible()

  await page.getByRole('tab', { name: '调试' }).click()
  await expect(page.getByTestId('ai-application-debug-panel')).toBeVisible()
  await expect(page.getByTestId('ai-application-publish-panel')).toBeVisible()
  await expect(page.getByTestId('ai-application-access-panel')).toBeVisible()
  await expect(page.getByTestId('ai-application-log-panel')).toBeVisible()
})
