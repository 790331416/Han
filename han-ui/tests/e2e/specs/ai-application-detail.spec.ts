import { test, expect } from '../fixtures/test'

test('ai application detail should expose workspace panels and support workflow log jump', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/application')
  await page.waitForLoadState('networkidle')

  const workflowCard = page.locator('[data-testid="ai-application-card"][data-application-type="workflow"]').first()
  const fallbackCard = page.getByTestId('ai-application-card').first()
  const targetCard = await workflowCard.count() > 0 ? workflowCard : fallbackCard

  await expect(targetCard).toBeVisible()
  await expect(targetCard.getByTestId('ai-application-detail-link')).toBeVisible()
  await targetCard.getByTestId('ai-application-detail-link').click()

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
  await expect(page.getByTestId('ai-application-publish-toggle')).toBeVisible()
  await expect(page.getByTestId('ai-application-publish-readiness')).toBeVisible()
  await expect(page.getByTestId('ai-application-copy-detail-link')).toBeVisible()
  await expect(page.getByTestId('ai-application-copy-management-link')).toBeVisible()
  await expect(page.getByTestId('ai-application-access-entry-list')).toBeVisible()
  await expect(page.getByTestId('ai-application-access-item')).toHaveCount(3)

  const logItems = page.getByTestId('ai-application-log-item')
  if (await logItems.count() > 0) {
    await logItems.first().click()
    await expect(page.getByTestId('ai-application-log-drawer')).toBeVisible()
    await expect(page.getByTestId('ai-application-log-drawer-body')).toBeVisible()
    await expect(page.getByTestId('ai-application-log-source-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-log-execution-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-log-drawer-open-button')).toBeVisible()
    await page.getByTestId('ai-application-log-drawer-open-button').click()
    await page.waitForURL(/\/ai\/chat\?conversationId=/, {
      timeout: 30000,
      waitUntil: 'domcontentloaded'
    })
    await expect(page.getByTestId('ai-chat-page')).toBeVisible()
    await expect(page.getByTestId('ai-chat-conversation-list')).toBeVisible()
  }
})
