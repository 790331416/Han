import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'
import { e2eRuntime } from '../fixtures/test'
import {
  createKnowledgeBase,
  createMcpServer,
  deleteMcpServer,
  findBuiltInPromptTemplate,
  findKnowledgeBaseByName,
  findMcpServerByName,
  tryDeletePromptTemplate,
  tryEditPromptTemplate
} from '../utils/ai-admin'

function buildUniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

function waitForGetResponse(page: Page, pathFragment: string) {
  return page.waitForResponse((response) => {
    return response.request().method() === 'GET'
      && response.status() === 200
      && response.url().includes(pathFragment)
  })
}

function waitForPostResponse(page: Page, pathFragment: string) {
  return page.waitForResponse((response) => {
    return response.request().method() === 'POST'
      && response.status() === 200
      && response.url().includes(pathFragment)
  })
}

test('ai knowledge page should delete knowledge base from card actions and clear list state', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const kbName = buildUniqueName('playwright-kb-remove')
  const createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    kbName,
    kbType: 'general',
    description: 'Playwright knowledge base removal regression'
  })

  const listResponse = waitForGetResponse(page, '/ai/kb/list')
  await page.goto('/ai/knowledge')
  await listResponse
  await page.waitForLoadState('networkidle')

  const card = page.locator('[data-testid="ai-knowledge-card"]', { hasText: kbName }).first()
  await expect(card).toBeVisible()

  await card.getByTestId(`ai-knowledge-card-actions-${createdKb.kbId}`).click()
  const deleteResponse = waitForPostResponse(page, `/ai/kb/remove/${createdKb.kbId}`)
  const deleteMenuItem = page.locator('.el-popper').getByRole('menuitem', { name: '删除' }).last()
  await expect(deleteMenuItem).toBeVisible()
  await deleteMenuItem.click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await deleteResponse

  await expect.poll(async () => {
    const latestKb = await findKnowledgeBaseByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, kbName)
    return latestKb === null
  }, { timeout: 15000 }).toBe(true)

  await expect(card).not.toBeVisible()
})

test('ai prompt page should keep built-in templates protected from deletion', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const builtInTemplate = await findBuiltInPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken)
  expect(builtInTemplate).not.toBeNull()

  const listResponse = waitForGetResponse(page, '/ai/prompt/list')
  await page.goto('/ai/prompt')
  await listResponse
  await page.waitForLoadState('networkidle')

  const row = page.getByTestId('ai-prompt-table').locator('.el-table__row', { hasText: builtInTemplate!.templateName }).first()
  await expect(row).toBeVisible()
  await expect(row.getByTestId('ai-prompt-delete-button')).toBeDisabled()

  const deleteResult = await tryDeletePromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, builtInTemplate!.templateId)
  expect(deleteResult.code).not.toBe(200)
})

test('ai prompt page should keep built-in templates protected from editing', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const builtInTemplate = await findBuiltInPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken)
  expect(builtInTemplate).not.toBeNull()

  const listResponse = waitForGetResponse(page, '/ai/prompt/list')
  await page.goto('/ai/prompt')
  await listResponse
  await page.waitForLoadState('networkidle')

  const row = page.getByTestId('ai-prompt-table').locator('.el-table__row', { hasText: builtInTemplate!.templateName }).first()
  await expect(row).toBeVisible()
  await expect(row.getByTestId('ai-prompt-edit-button')).toBeDisabled()

  const editResult = await tryEditPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    templateId: builtInTemplate!.templateId,
    templateName: builtInTemplate!.templateName,
    category: builtInTemplate!.category,
    content: `${builtInTemplate!.content} edited`,
    variables: builtInTemplate!.variables || '[]',
    description: builtInTemplate!.description || '',
    status: builtInTemplate!.status || '0'
  })
  expect(editResult.code).not.toBe(200)
})

test('ai mcp page should support streamable_http tool metadata regression', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const serverName = buildUniqueName('playwright-mcp-http')
  const createdServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName,
    transportType: 'streamable_http',
    url: 'http://127.0.0.1:65535/mcp'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/mcp/list')
    await page.goto('/ai/mcp')
    await listResponse
    await page.waitForLoadState('networkidle')

    const row = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: serverName }).first()
    await expect(row).toBeVisible()
    await expect(row).toContainText('Streamable HTTP')
    await expect(row).toContainText('http://127.0.0.1:65535/mcp')

    const refreshResponse = waitForPostResponse(page, `/ai/mcp/refresh/${createdServer.mcpId}`)
    await row.getByTestId('ai-mcp-refresh-button').click()
    await refreshResponse

    await expect.poll(async () => {
      const server = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, serverName)
      return server?.tools || ''
    }, { timeout: 15000 }).toContain('http_stream_call')

    const refreshedRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: serverName }).first()
    await refreshedRow.getByTestId('ai-mcp-view-tools-button').click()
    await expect(page.getByTestId('ai-mcp-tools-table')).toContainText('http_stream_call')
    await expect(page.getByTestId('ai-mcp-tools-table')).not.toContainText('sse_subscribe')
    await expect(page.getByTestId('ai-mcp-tools-table')).not.toContainText('stdio_exec')
  } finally {
    await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdServer.mcpId).catch(() => undefined)
  }
})
