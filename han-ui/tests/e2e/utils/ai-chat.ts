import { expect, type Locator, type Page } from '@playwright/test'

export async function openAiChatPage(
  page: Page,
  query?: string,
  options: {
    fresh?: boolean
  } = {}
): Promise<void> {
  const { fresh = false } = options
  const target = query
    ? `/ai/chat${query.startsWith('?') ? query : `?${query}`}`
    : '/ai/chat'

  await page.goto(target)
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()
  await expect(page.getByTestId('ai-chat-input')).toBeVisible()
  if (fresh) {
    await startNewAiChat(page)
  }
}

export function assistantMessages(page: Page): Locator {
  return page.locator('[data-testid="ai-chat-message"][data-role="assistant"]')
}

export function latestAssistantMessage(page: Page): Locator {
  return assistantMessages(page).last()
}

export function latestUserMessage(page: Page): Locator {
  return page.locator('[data-testid="ai-chat-message"][data-role="user"]').last()
}

export async function startNewAiChat(page: Page): Promise<void> {
  const newChatButton = page.getByTestId('ai-chat-new-button')
  await expect(newChatButton).toBeVisible()
  await newChatButton.click()
  await expect(page.locator('[data-testid="ai-chat-message"]')).toHaveCount(0)
}

export async function expectMessageTextContains(message: Locator, text: string, timeout = 10000): Promise<void> {
  await expect(message.locator('.message-text')).toContainText(text, { timeout })
}

export async function waitForAssistantMessageCount(page: Page, minimumCount = 1, timeout = 30000): Promise<void> {
  await expect.poll(async () => {
    return await assistantMessages(page).count()
  }, { timeout }).toBeGreaterThan(minimumCount - 1)
}

export async function expectRenderedMessageContent(message: Locator, timeout = 30000): Promise<void> {
  await expect.poll(async () => {
    const text = (await message.locator('.message-text').innerText()).replace(/\s+/g, ' ').trim()
    return text.length
  }, { timeout }).toBeGreaterThan(0)
}

export async function sendChatMessage(
  page: Page,
  prompt: string,
  options: {
    waitForAssistant?: boolean
    timeout?: number
  } = {}
): Promise<void> {
  const { waitForAssistant = true, timeout = 30000 } = options
  const input = page.getByTestId('ai-chat-input')
  const sendButton = page.getByTestId('ai-chat-send-button')

  await expect(input).toBeVisible()
  await input.fill(prompt)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()

  if (waitForAssistant) {
    await waitForAssistantMessageCount(page, 1, timeout)
    await expectRenderedMessageContent(latestAssistantMessage(page), timeout)
  }
}
