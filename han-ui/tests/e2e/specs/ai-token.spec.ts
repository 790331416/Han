import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'

function waitForStatsResponse(page: Page, pathFragment: string) {
  return page.waitForResponse((response) => {
    return response.url().includes(pathFragment)
      && response.request().method() === 'GET'
      && response.status() === 200
  })
}

test('ai token page smoke should load model, user, and daily stats', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const modelResponse = waitForStatsResponse(page, '/ai/token/stats/model')
  const userResponse = waitForStatsResponse(page, '/ai/token/stats/user')
  const dailyResponse = waitForStatsResponse(page, '/ai/token/stats/daily')

  await page.goto('/ai/token')
  await Promise.all([modelResponse, userResponse, dailyResponse])
  await page.waitForLoadState('networkidle')

  await expect(page.locator('.app-container').first()).toBeVisible()
  await expect(page.locator('.stat-card').first()).toBeVisible()
  await expect(page.locator('.daily-card')).toBeVisible()
})
