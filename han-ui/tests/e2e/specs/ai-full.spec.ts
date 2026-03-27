import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'

async function sendChatMessage(page: Page, prompt: string) {
  const input = page.locator('.chat-input-area textarea')
  const sendButton = page.locator('.chat-input-area .send-btn')

  await expect(input).toBeVisible()
  await input.fill(prompt)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()

  await expect.poll(async () => {
    return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)
}

test('ai agent and workflow pages should load on full tier', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/agent')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-agent-page')).toBeVisible()
  await expect(page.getByTestId('ai-agent-create-button')).toBeVisible()
  await expect(page.getByTestId('ai-agent-list')).toBeVisible()

  await page.goto('/ai/workflow')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-workflow-page')).toBeVisible()
  await expect(page.getByTestId('ai-workflow-create-button')).toBeVisible()
  await expect(page.getByTestId('ai-workflow-list')).toBeVisible()
})

test('ai chat page should send a message and render assistant reply', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const prompt = `Playwright AI smoke ${Date.now()}`

  await page.goto('/ai/chat')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()
  await sendChatMessage(page, prompt)

  const lastAssistantMessage = page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()
  await expect(lastAssistantMessage).toContainText('当前模型')
  await expect(page.getByTestId('ai-chat-conversation-list')).toBeVisible()
  await expect(page.getByTestId('ai-chat-inspector')).toBeVisible()
  await expect(page.getByTestId('ai-chat-context-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-source-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-execution-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-execution-stage')).toHaveCount(5)
})

test('ai chat page should support regenerate and edit-regenerate', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const originalPrompt = `Playwright regenerate ${Date.now()}`
  const editedPrompt = `${originalPrompt} edited`

  await page.goto('/ai/chat')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()

  await sendChatMessage(page, originalPrompt)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()).toContainText('当前模型')

  const regenerateResponsePromise = page.waitForResponse((response) => {
    return response.url().includes('/ai/chat/regenerate/') && response.request().method() === 'POST'
  })
  const lastAssistantMessage = page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()
  await lastAssistantMessage.hover()
  await lastAssistantMessage.getByRole('button', { name: /重新生成/ }).click()
  await regenerateResponsePromise
  await expect.poll(async () => {
    return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()).toBeVisible()

  const latestUserMessage = page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()
  await latestUserMessage.hover()
  await latestUserMessage.getByRole('button', { name: /编辑/ }).click()

  const editInput = latestUserMessage.locator('textarea')
  await expect(editInput).toBeVisible()
  await editInput.fill(editedPrompt)

  const editRegenerateResponsePromise = page.waitForResponse((response) => {
    return response.url().includes('/ai/chat/edit-regenerate') && response.request().method() === 'POST'
  })
  await latestUserMessage.getByRole('button', { name: /发送/ }).click()
  await editRegenerateResponsePromise

  await expect(page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()).toContainText(editedPrompt)
  await expect.poll(async () => {
    return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)
  await expect(page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()).toBeVisible()
})
