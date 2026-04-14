import { test, expect, e2eRuntime } from '../fixtures/test'
import {
  createWorkflow,
  deleteWorkflow,
  listAvailableAiModels,
  publishWorkflow
} from '../utils/ai-admin'

function buildUniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

test('ai application detail should expose workspace panels and support workflow log jump', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const workflowName = buildUniqueName('应用详情回归')
  let workflowId: string | number | null = null

  try {
    const llmModels = await listAvailableAiModels(request, e2eRuntime.apiBaseUrl, authSession.accessToken, 'LLM')
    const targetModel = llmModels[0]
    expect(targetModel?.modelId).toBeTruthy()

    const workflow = await createWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
      workflowName,
      description: '应用详情回归工作流',
      workflowType: 'simple',
      modelId: targetModel.modelId,
      systemPrompt: '请用简洁中文回答。',
      prologue: '应用详情回归工作流',
      status: '0'
    })
    workflowId = workflow.workflowId
    await publishWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, workflowId)

    await page.goto(`/ai/application/workflow/${workflowId}`)
    await page.waitForURL(/\/ai\/application\/(agent|workflow)\/.+/, {
      timeout: 30000,
      waitUntil: 'domcontentloaded'
    })
    await expect(page.getByTestId('ai-application-detail-page')).toBeVisible()
    await expect(page.getByTestId('ai-application-detail-title')).toContainText(workflowName)
    await expect(page.getByTestId('ai-application-overview-panel')).toBeVisible()

    await page.getByRole('tab', { name: '设置' }).click()
    await expect(page.getByTestId('ai-application-settings-panel')).toBeVisible()

    await page.getByRole('tab', { name: '调试' }).click()
    await expect(page.getByTestId('ai-application-debug-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-publish-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-access-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-log-panel')).toBeVisible()
    await expect(page.getByTestId('ai-application-publish-toggle')).toBeVisible()
    await expect(page.getByTestId('ai-application-publish-readiness')).toBeVisible()
    await expect(page.getByTestId('ai-application-copy-detail-link')).toBeVisible()
    await expect(page.getByTestId('ai-application-copy-management-link')).toBeVisible()
    await expect(page.getByTestId('ai-application-access-entry-list')).toBeVisible()
    await expect(page.getByTestId('ai-application-access-item')).toHaveCount(3)

    const logItems = page.getByTestId('ai-application-log-item')
    if (await logItems.count() > 0) {
      await logItems.first().click()
      await expect(page.getByTestId('ai-application-log-drawer')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-drawer-body')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-source-panel')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-execution-panel')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-source-card-list')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-execution-stage-list')).toBeVisible()
      await expect(page.getByTestId('ai-application-log-execution-stage')).toHaveCount(5)
      await expect(page.getByTestId('ai-application-log-drawer-open-button')).toBeVisible()
      await page.getByTestId('ai-application-log-drawer-open-button').click()
      await page.waitForURL(/\/ai\/chat\?conversationId=/, {
        timeout: 30000,
        waitUntil: 'domcontentloaded'
      })
      await expect(page.getByTestId('ai-chat-page')).toBeVisible()
      await expect(page.getByTestId('ai-chat-conversation-list')).toBeVisible()
    }
  } finally {
    if (workflowId) {
      await deleteWorkflow(request, e2eRuntime.apiBaseUrl, authSession.accessToken, workflowId).catch(() => undefined)
    }
  }
})
