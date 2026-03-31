import { test, expect } from '../fixtures/test'
import {
  expectRenderedMessageContent,
  latestAssistantMessage,
  latestUserMessage,
  openAiChatPage,
  sendChatMessage,
  waitForAssistantMessageCount
} from '../utils/ai-chat'

test('ai chat should stop streaming and allow sending another message', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const longPrompt = `Playwright stop boundary ${Date.now()} ${'segment '.repeat(240)}`
  const followUpPrompt = `Playwright after stop ${Date.now()}`

  await openAiChatPage(page)

  await sendChatMessage(page, longPrompt, { waitForAssistant: false })
  await expect(page.getByTestId('ai-chat-stop-button')).toBeVisible({ timeout: 10000 })
  await expect(page.getByTestId('ai-chat-streaming')).toBeVisible()
  await page.getByTestId('ai-chat-stop-button').click()

  await expect(page.getByTestId('ai-chat-streaming')).toBeHidden()

  await sendChatMessage(page, followUpPrompt)
  await expect(latestUserMessage(page)).toContainText(followUpPrompt)
  await waitForAssistantMessageCount(page)
  await expectRenderedMessageContent(latestAssistantMessage(page))
})

test('ai chat should restore current conversation after reload', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const prompt = `Playwright restore conversation ${Date.now()}`

  await openAiChatPage(page)

  await sendChatMessage(page, prompt)
  await expect(latestUserMessage(page)).toContainText(prompt)
  await expect.poll(async () => {
    return page.getByTestId('ai-chat-conversation-item').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)

  await page.reload()
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()
  await expect(page.getByTestId('ai-chat-input')).toBeVisible()
  await expect(latestUserMessage(page)).toContainText(prompt)
  await expectRenderedMessageContent(latestAssistantMessage(page))
})
