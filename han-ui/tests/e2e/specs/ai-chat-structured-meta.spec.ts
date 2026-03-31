import { test, expect, e2eRuntime } from '../fixtures/test'
import {
  createKnowledgeBase,
  createMcpServer,
  createWorkflow,
  deleteKnowledgeBase,
  deleteMcpServer,
  deleteWorkflow,
  hitTestKnowledgeBase,
  listAvailableAiModels,
  publishWorkflow,
  refreshMcpServerTools,
  uploadKnowledgeDocument
} from '../utils/ai-admin'
import {
  expectRenderedMessageContent,
  latestAssistantMessage,
  openAiChatPage,
  sendChatMessage,
  waitForAssistantMessageCount
} from '../utils/ai-chat'

const EXPECTED_TOOL_NAME = 'http_stream_call'

function buildUniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

test.describe('ai chat structured metadata', () => {
  test('workflow chat should render structured knowledge and tool metadata', async ({ authenticatedPage, request, authSession }) => {
    const page = authenticatedPage
    const uniqueSuffix = Date.now()
    const knowledgePhrase = `Structured meta knowledge phrase ${uniqueSuffix}`
    const kbName = buildUniqueName('playwright-structured-kb')
    const workflowName = buildUniqueName('playwright-structured-wf')
    const serverName = buildUniqueName('playwright-structured-mcp')
    const documentName = `structured-meta-${uniqueSuffix}.txt`
    const documentContent = [
      `${knowledgePhrase} is the unique retrieval anchor for this Playwright test.`,
      'This paragraph verifies that structured knowledge excerpts are returned separately from message content.',
      'The workflow should also expose MCP tool metadata in the tool trace panel.'
    ].join('\n')

    let createdKb: { kbId: string | number } | null = null
    let createdServer: { mcpId: string | number } | null = null
    let createdWorkflow: { workflowId: string | number } | null = null

    try {
      const llmModels = await listAvailableAiModels(request, e2eRuntime.apiBaseUrl, authSession.accessToken, 'LLM')
      const targetModel = llmModels[0]
      expect(targetModel?.modelId).toBeTruthy()

      createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
        kbName,
        kbType: 'general',
        description: 'Playwright structured metadata knowledge base'
      })

      await uploadKnowledgeDocument(
        request,
        e2eRuntime.apiBaseUrl,
        authSession.accessToken,
        createdKb.kbId,
        documentName,
        documentContent
      )

      await expect.poll(async () => {
        const results = await hitTestKnowledgeBase(
          request,
          e2eRuntime.apiBaseUrl,
          authSession.accessToken,
          createdKb!.kbId,
          knowledgePhrase
        )
        return results.length
      }, { timeout: 60000 }).toBeGreaterThan(0)

      createdServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
        serverName,
        transportType: 'streamable_http',
        url: 'http://127.0.0.1:65535/mcp'
      })
      await refreshMcpServerTools(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdServer.mcpId)

      createdWorkflow = await createWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
        workflowName,
        description: 'Playwright structured metadata workflow',
        workflowType: 'simple',
        modelId: targetModel.modelId,
        knowledgeBaseIds: JSON.stringify([createdKb.kbId]),
        mcpServerIds: JSON.stringify([createdServer.mcpId]),
        systemPrompt: 'Use retrieved knowledge when it is relevant and keep the answer concise.',
        prologue: 'Structured metadata test workflow',
        status: '0'
      })
      await publishWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdWorkflow.workflowId)

      const prompt = `Please answer using the phrase "${knowledgePhrase}" and include tool trace details.`

      await openAiChatPage(page, `workflowId=${createdWorkflow.workflowId}`)
      await sendChatMessage(page, prompt)
      await waitForAssistantMessageCount(page)
      await expectRenderedMessageContent(latestAssistantMessage(page))

      await expect(page.getByTestId('ai-chat-source-insight-panel')).toBeVisible()
      await expect(page.getByTestId('ai-chat-tool-trace-panel')).toBeVisible()
      await expect(page.getByTestId('ai-chat-source-insight-list')).toContainText(knowledgePhrase, { timeout: 60000 })
      await expect(page.getByTestId('ai-chat-tool-trace-list')).toContainText(EXPECTED_TOOL_NAME, { timeout: 60000 })
      await expect(page.getByTestId('ai-chat-tool-trace-list')).toContainText(serverName, { timeout: 60000 })

      await expect.poll(() => {
        return new URL(page.url()).searchParams.get('conversationId') || ''
      }, { timeout: 30000 }).toMatch(/\d+/)

      const conversationId = new URL(page.url()).searchParams.get('conversationId')!

      await page.reload()
      await expect(page.getByTestId('ai-chat-page')).toBeVisible()
      await expect(page.getByTestId('ai-chat-input')).toBeVisible()

      const headers: Record<string, string> = {
        Authorization: `Bearer ${authSession.accessToken}`
      }
      if (authSession.userInfo?.userId !== undefined && authSession.userInfo?.userId !== null) {
        headers['X-User-Id'] = String(authSession.userInfo.userId)
      }
      if (e2eRuntime.tenantId) {
        headers['X-Tenant-Id'] = String(e2eRuntime.tenantId)
      }

      const messagesResponse = await request.get(`${e2eRuntime.apiBaseUrl}/ai/chat/messages/${conversationId}`, {
        headers
      })
      expect(messagesResponse.ok()).toBeTruthy()

      const payload = await messagesResponse.json()
      const rawMessages = Array.isArray(payload) ? payload : payload?.data ?? payload?.rows ?? []
      const messages = Array.isArray(rawMessages) ? rawMessages : []
      const assistantMessage = [...messages].reverse().find((item) => item?.role === 'assistant')

      expect(assistantMessage).toBeTruthy()
      expect(Array.isArray(assistantMessage?.knowledgeSources)).toBeTruthy()
      expect(Array.isArray(assistantMessage?.toolExecutions)).toBeTruthy()
      expect(assistantMessage?.knowledgeSources?.length ?? 0).toBeGreaterThan(0)
      expect(assistantMessage?.toolExecutions?.length ?? 0).toBeGreaterThan(0)
      expect(
        assistantMessage?.knowledgeSources?.some((item: { excerpt?: string }) => item?.excerpt?.includes(knowledgePhrase))
      ).toBeTruthy()
      expect(
        assistantMessage?.toolExecutions?.some((item: { toolNames?: string[] }) => item?.toolNames?.includes(EXPECTED_TOOL_NAME))
      ).toBeTruthy()

      await expect(page.getByTestId('ai-chat-source-insight-list')).toContainText(knowledgePhrase)
      await expect(page.getByTestId('ai-chat-tool-trace-list')).toContainText(EXPECTED_TOOL_NAME)
    } finally {
      if (createdWorkflow?.workflowId) {
        await deleteWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdWorkflow.workflowId).catch(() => undefined)
      }
      if (createdServer?.mcpId) {
        await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdServer.mcpId).catch(() => undefined)
      }
      if (createdKb?.kbId) {
        await deleteKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId).catch(() => undefined)
      }
    }
  })
})
