import { test, expect } from '../fixtures/test'
import {
  expectMessageTextContains,
  expectRenderedMessageContent,
  latestAssistantMessage,
  latestUserMessage,
  openAiChatPage,
  sendChatMessage
} from '../utils/ai-chat'

test('ai agent and workflow pages should load on full tier', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await page.goto('/ai/agent')
  await expect(page.getByTestId('ai-agent-page')).toBeVisible()
  await expect(page.getByTestId('ai-agent-create-button')).toBeVisible()
  await expect(page.getByTestId('ai-agent-list')).toBeVisible()

  await page.goto('/ai/workflow')
  await expect(page.getByTestId('ai-workflow-page')).toBeVisible()
  await expect(page.getByTestId('ai-workflow-create-button')).toBeVisible()
  await expect(page.getByTestId('ai-workflow-list')).toBeVisible()
})

test('ai chat page should send a message and render assistant reply', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const prompt = `Playwright AI smoke ${Date.now()}`

  await openAiChatPage(page, undefined, { fresh: true })
  await sendChatMessage(page, prompt)

  await expectRenderedMessageContent(latestAssistantMessage(page))
  await expect(page.getByTestId('ai-chat-conversation-list')).toBeVisible()
  await expect(page.getByTestId('ai-chat-inspector')).toBeVisible()
  await expect(page.getByTestId('ai-chat-context-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-source-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-execution-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-source-insight-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-tool-trace-panel')).toBeVisible()
  await expect(page.getByTestId('ai-chat-execution-stage')).toHaveCount(5)
  await expect(page.getByTestId('ai-chat-source-insight-list')).toBeVisible()
  await expect(page.getByTestId('ai-chat-tool-trace-list')).toBeVisible()
})

test('ai chat page should support regenerate and edit-regenerate', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const originalPrompt = `Playwright regenerate ${Date.now()}`
  const editedPrompt = `${originalPrompt} edited`

  await openAiChatPage(page, undefined, { fresh: true })
  await sendChatMessage(page, originalPrompt)
  await expectRenderedMessageContent(latestAssistantMessage(page))

  const regenerateResponsePromise = page.waitForResponse((response) => {
    return response.url().includes('/ai/chat/regenerate/') && response.request().method() === 'POST'
  })
  const lastAssistant = latestAssistantMessage(page)
  await lastAssistant.hover()
  await lastAssistant.getByTestId('ai-chat-regenerate-button').click()
  await regenerateResponsePromise
  await expectRenderedMessageContent(latestAssistantMessage(page))

  const currentLatestUserMessage = latestUserMessage(page)
  await currentLatestUserMessage.hover()
  await currentLatestUserMessage.getByTestId('ai-chat-edit-button').click()

  const editInput = currentLatestUserMessage.getByTestId('ai-chat-edit-input')
  await expect(editInput).toBeVisible()
  await editInput.fill(editedPrompt)

  const editRegenerateResponsePromise = page.waitForResponse((response) => {
    return response.url().includes('/ai/chat/edit-regenerate') && response.request().method() === 'POST'
  })
  await currentLatestUserMessage.getByTestId('ai-chat-edit-submit-button').click()
  await editRegenerateResponsePromise

  await expectMessageTextContains(latestUserMessage(page), editedPrompt)
  await expectRenderedMessageContent(latestAssistantMessage(page))
})
