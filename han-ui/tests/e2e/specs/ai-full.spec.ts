import { test, expect } from '../fixtures/test'

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

  const input = page.locator('.chat-input-area textarea')
  const sendButton = page.locator('.chat-input-area .send-btn')

  await expect(input).toBeVisible()
  await input.fill(prompt)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()

  await expect.poll(async () => {
    return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)

  const lastAssistantMessage = page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last()
  await expect(lastAssistantMessage).toContainText('当前模型')
  await expect(page.getByTestId('ai-chat-conversation-list')).toBeVisible()
})
