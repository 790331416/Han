import { test, expect } from '../fixtures/test'

test('ai application home should load and keep app-first entry available', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/application')
  await page.waitForLoadState('networkidle')

  await expect(page.getByTestId('ai-application-page')).toBeVisible()
  await expect(page.getByTestId('ai-application-stat-total')).toBeVisible()
  await expect(page.getByTestId('ai-application-list')).toBeVisible()
  await expect(page.getByTestId('ai-application-create-agent')).toBeVisible()
  await expect(page.getByTestId('ai-application-create-workflow')).toBeVisible()
})

test('ai application home should route create actions into existing management pages', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/application')
  await page.waitForLoadState('networkidle')

  await page.getByTestId('ai-application-create-agent').click()
  await page.waitForURL((url) => url.pathname === '/ai/agent', { timeout: 15000 })
  await expect(page.getByTestId('ai-agent-page')).toBeVisible()
  await expect(page.getByTestId('ai-agent-form')).toBeVisible()
  await expect(page).not.toHaveURL(/action=create/)

  await page.goto('/ai/application')
  await page.waitForLoadState('networkidle')

  await page.getByTestId('ai-application-create-workflow').click()
  await page.waitForURL((url) => url.pathname === '/ai/workflow', { timeout: 15000 })
  await expect(page.getByTestId('ai-workflow-page')).toBeVisible()
  await expect(page.getByTestId('ai-workflow-form')).toBeVisible()
  await expect(page).not.toHaveURL(/action=create/)
})
