import { test, expect } from '../fixtures/test'
import {
  expectMessageTextContains,
  expectRenderedMessageContent,
  latestAssistantMessage,
  latestUserMessage,
  openAiChatPage,
  sendChatMessage,
  waitForAssistantMessageCount
} from '../utils/ai-chat'

test('ai chat should stop streaming and allow sending another message', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const longPrompt = [
    `停止生成边界验证-${Date.now()}`,
    '请输出 120 行编号内容。',
    '每一行至少包含 12 个汉字。',
    '不要总结，也不要压缩答案。'
  ].join(' ')
  const followUpPrompt = `停止后续聊验证-${Date.now()}`

  await openAiChatPage(page, undefined, { fresh: true })

  await sendChatMessage(page, longPrompt, { waitForAssistant: false })
  const stopButton = page.getByTestId('ai-chat-stop-button')
  const stopButtonAppeared = await stopButton.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)

  if (stopButtonAppeared) {
    await expect(page.getByTestId('ai-chat-streaming')).toBeVisible()
    await stopButton.click()
    await expect(page.getByTestId('ai-chat-streaming')).toBeHidden()
  } else {
    await waitForAssistantMessageCount(page, 1, 10000)
    await expectRenderedMessageContent(latestAssistantMessage(page), 10000)
  }

  await sendChatMessage(page, followUpPrompt, { timeout: 45000 })
  await expectMessageTextContains(latestUserMessage(page), followUpPrompt)
  await waitForAssistantMessageCount(page)
  await expectRenderedMessageContent(latestAssistantMessage(page))
})

test('ai chat should restore current conversation after reload', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const prompt = `刷新后恢复会话-${Date.now()}`

  await openAiChatPage(page, undefined, { fresh: true })

  await sendChatMessage(page, prompt)
  await expectMessageTextContains(latestUserMessage(page), prompt)
  await expect.poll(async () => {
    return page.getByTestId('ai-chat-conversation-item').count()
  }, { timeout: 30000 }).toBeGreaterThan(0)

  await page.reload()
  await expect(page.getByTestId('ai-chat-page')).toBeVisible()
  await expect(page.getByTestId('ai-chat-input')).toBeVisible()
  await expectMessageTextContains(latestUserMessage(page), prompt)
  await expectRenderedMessageContent(latestAssistantMessage(page))
})
