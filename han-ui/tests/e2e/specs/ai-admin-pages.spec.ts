import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'

function waitForListResponse(page: Page, pathFragment: string) {
  return page.waitForResponse((response) => {
    return response.url().includes(pathFragment)
      && response.request().method() === 'GET'
      && response.status() === 200
  })
}

test('ai knowledge page smoke should load and open create dialog', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const listResponse = waitForListResponse(page, '/ai/kb/list')

  await page.goto('/ai/knowledge')
  await listResponse
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-knowledge-page')).toBeVisible()
  await expect(page.getByTestId('ai-knowledge-list')).toBeVisible()
  await page.getByTestId('ai-knowledge-create-button').click()
  await expect(page.getByTestId('ai-knowledge-form')).toBeVisible()
})

test('ai mcp page smoke should load and open create dialog', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const listResponse = waitForListResponse(page, '/ai/mcp/list')

  await page.goto('/ai/mcp')
  await listResponse
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-mcp-page')).toBeVisible()
  await expect(page.getByTestId('ai-mcp-table')).toBeVisible()
  await page.getByTestId('ai-mcp-create-button').click()
  await expect(page.getByTestId('ai-mcp-form')).toBeVisible()
})

test('ai prompt page smoke should load and support create or preview entry', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const listResponse = waitForListResponse(page, '/ai/prompt/list')

  await page.goto('/ai/prompt')
  await listResponse
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-prompt-page')).toBeVisible()
  await expect(page.getByTestId('ai-prompt-table')).toBeVisible()
  await expect(page.getByTestId('ai-prompt-preview-button').first()).toBeVisible()
  await page.getByTestId('ai-prompt-create-button').click()
  await expect(page.getByTestId('ai-prompt-form')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.getByTestId('ai-prompt-preview-button').first().click()
  await expect(page.getByTestId('ai-prompt-preview-panel')).toBeVisible()
})
