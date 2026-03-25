import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'

async function sendChatMessage(page: Page, prompt: string) {
  const input = page.locator('.chat-input-area textarea')
  const sendButton = page.locator('.chat-input-area .send-btn')

  await expect(input).toBeVisible()
  await input.fill(prompt)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()
}

test('ai chat should stop streaming and allow sending another message', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const longPrompt = `Playwright stop boundary ${Date.now()} ${'segment '.repeat(240)}`
  const followUpPrompt = `Playwright after stop ${Date.now()}`

  await page.goto('/ai/chat')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()

  await sendChatMessage(page, longPrompt)
  await expect(page.getByTestId('ai-chat-stop-button')).toBeVisible({ timeout: 10000 })
  await expect(page.getByTestId('ai-chat-streaming')).toBeVisible()
  await page.getByTestId('ai-chat-stop-button').click()

  await expect(page.getByTestId('ai-chat-streaming')).toBeHidden()

  await sendChatMessage(page, followUpPrompt)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()).toContainText(followUpPrompt)
  await expect.poll(async () => {
    return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()).toBeVisible()
})

test('ai chat should restore current conversation after reload', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const prompt = `Playwright restore conversation ${Date.now()}`

  await page.goto('/ai/chat')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()

  await sendChatMessage(page, prompt)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()).toContainText(prompt)
  await expect.poll(async () => {
    return page.getByTestId('ai-chat-conversation-item').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)

  await page.reload()
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()).toContainText(prompt)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()).toBeVisible()
})
