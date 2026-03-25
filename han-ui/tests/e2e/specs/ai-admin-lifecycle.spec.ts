import path from 'node:path'
import type { Page } from '@playwright/test'
import { test, expect } from '../fixtures/test'
import { e2eRuntime } from '../fixtures/test'
import {
  createKnowledgeBase,
  createMcpServer,
  createPromptTemplate,
  deleteKnowledgeBase,
  deleteMcpServer,
  deletePromptTemplate,
  fetchKnowledgeDocuments,
  findKnowledgeBaseByName,
  findMcpServerByName,
  findPromptTemplateByName
} from '../utils/ai-admin'

const KNOWLEDGE_FILE_PATH = path.resolve(process.cwd(), 'tests/e2e/fixtures/files/ai-knowledge-upload.txt')
const KNOWLEDGE_EXTRA_FILE_PATH = path.resolve(process.cwd(), 'tests/e2e/fixtures/files/ai-knowledge-upload-extra.txt')
const KNOWLEDGE_FILE_NAME = 'ai-knowledge-upload.txt'
const KNOWLEDGE_EXTRA_FILE_NAME = 'ai-knowledge-upload-extra.txt'

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

async function uploadKnowledgeDocument(page: Page, kbId: string | number, filePath: string) {
  const uploadResponse = waitForPostResponse(page, `/ai/kb/${kbId}/document/upload`)
  await page.getByTestId('ai-knowledge-upload').locator('input[type="file"]').setInputFiles(filePath)
  await uploadResponse
}

test('ai knowledge page should support multi-document lifecycle and stats rollback', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const kbName = buildUniqueName('playwright-kb-life')
  const createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    kbName,
    kbType: 'general',
    description: 'Playwright lifecycle knowledge base'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/kb/list')
    await page.goto('/ai/knowledge')
    await listResponse
    await page.waitForLoadState('networkidle')

    const card = page.locator('[data-testid="ai-knowledge-card"]', { hasText: kbName }).first()
    await expect(card).toBeVisible()

    const docListResponse = waitForGetResponse(page, `/ai/kb/${createdKb.kbId}/document/list`)
    await card.click()
    await docListResponse
    await expect(page.getByTestId('ai-knowledge-doc-dialog')).toBeVisible()

    await uploadKnowledgeDocument(page, createdKb.kbId, KNOWLEDGE_FILE_PATH)
    await uploadKnowledgeDocument(page, createdKb.kbId, KNOWLEDGE_EXTRA_FILE_PATH)

    await expect.poll(async () => {
      const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
      return docs.filter((item) => [KNOWLEDGE_FILE_NAME, KNOWLEDGE_EXTRA_FILE_NAME].includes(item.docName)).length
    }, { timeout: 30000 }).toBe(2)

    const docTable = page.getByTestId('ai-knowledge-doc-table')
    await expect(docTable).toContainText(KNOWLEDGE_FILE_NAME)
    await expect(docTable).toContainText(KNOWLEDGE_EXTRA_FILE_NAME)
    await page.keyboard.press('Escape')

    await expect(card).toContainText('2 文档')

    const latestKb = await findKnowledgeBaseByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, kbName)
    expect(latestKb?.documentCount).toBe(2)

    const reopenDocListResponse = waitForGetResponse(page, `/ai/kb/${createdKb.kbId}/document/list`)
    await card.click()
    await reopenDocListResponse

    const extraRow = docTable.locator('.el-table__row', { hasText: KNOWLEDGE_EXTRA_FILE_NAME }).first()
    await expect(extraRow).toBeVisible()
    const deleteResponse = waitForPostResponse(page, '/ai/kb/document/remove/')
    await extraRow.getByTestId('ai-knowledge-delete-doc-button').click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
    await deleteResponse

    await expect.poll(async () => {
      const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
      return docs.filter((item) => [KNOWLEDGE_FILE_NAME, KNOWLEDGE_EXTRA_FILE_NAME].includes(item.docName)).length
    }, { timeout: 30000 }).toBe(1)

    await expect(docTable).toContainText(KNOWLEDGE_FILE_NAME)
    await expect(docTable).not.toContainText(KNOWLEDGE_EXTRA_FILE_NAME)
    await page.keyboard.press('Escape')
    await expect(card).toContainText('1 文档')

    const updatedKb = await findKnowledgeBaseByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, kbName)
    expect(updatedKb?.documentCount).toBe(1)
  } finally {
    await deleteKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId).catch(() => undefined)
  }
})

test('ai prompt page should support edit and keep preview rendering correct', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const templateName = buildUniqueName('playwright-prompt-edit')
  const createdTemplate = await createPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    templateName,
    category: 'user',
    content: 'Hello {{name}} from {{topic}}.',
    variables: '["name","topic"]'
  })

  const editedContent = 'Welcome {{name}} to {{topic}}.'

  try {
    const listResponse = waitForGetResponse(page, '/ai/prompt/list')
    await page.goto('/ai/prompt')
    await listResponse
    await page.waitForLoadState('networkidle')

    const row = page.getByTestId('ai-prompt-table').locator('.el-table__row', { hasText: templateName }).first()
    await expect(row).toBeVisible()
    await row.getByTestId('ai-prompt-edit-button').click()
    const promptForm = page.getByTestId('ai-prompt-form')
    await expect(promptForm).toBeVisible()

    await promptForm.locator('textarea').first().fill(editedContent)
    const editResponse = waitForPostResponse(page, '/ai/prompt/edit')
    await page.getByTestId('ai-prompt-submit-button').click()
    await editResponse

    await expect.poll(async () => {
      const latestTemplate = await findPromptTemplateByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, templateName)
      return latestTemplate?.content || ''
    }, { timeout: 15000 }).toBe(editedContent)

    const refreshedRow = page.getByTestId('ai-prompt-table').locator('.el-table__row', { hasText: templateName }).first()
    await refreshedRow.getByTestId('ai-prompt-preview-button').click()
    await expect(page.getByTestId('ai-prompt-preview-panel')).toContainText(editedContent)

    await page.getByTestId('ai-prompt-var-input-name').fill('NiuMa')
    await page.getByTestId('ai-prompt-var-input-topic').fill('AI Regression')
    const renderResponse = waitForPostResponse(page, `/ai/prompt/render/${createdTemplate.templateId}`)
    await page.getByTestId('ai-prompt-render-button').click()
    await renderResponse
    await expect(page.getByTestId('ai-prompt-rendered-content')).toContainText('Welcome NiuMa to AI Regression.')
  } finally {
    await deletePromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdTemplate.templateId).catch(() => undefined)
  }
})

test('ai mcp page should distinguish sse and stdio tool metadata after refresh', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const sseServerName = buildUniqueName('playwright-mcp-sse')
  const stdioServerName = buildUniqueName('playwright-mcp-stdio')
  const createdSseServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName: sseServerName,
    transportType: 'sse'
  })
  const createdStdioServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName: stdioServerName,
    transportType: 'stdio',
    command: 'npx',
    args: '["-y","@modelcontextprotocol/server-filesystem"]'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/mcp/list')
    await page.goto('/ai/mcp')
    await listResponse
    await page.waitForLoadState('networkidle')

    const sseRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: sseServerName }).first()
    await expect(sseRow).toBeVisible()
    await expect(sseRow).toContainText('SSE')

    const sseRefreshResponse = waitForPostResponse(page, `/ai/mcp/refresh/${createdSseServer.mcpId}`)
    await sseRow.getByTestId('ai-mcp-refresh-button').click()
    await sseRefreshResponse

    await expect.poll(async () => {
      const server = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, sseServerName)
      return server?.tools || ''
    }, { timeout: 15000 }).toContain('sse_subscribe')

    const sseRefreshedRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: sseServerName }).first()
    await sseRefreshedRow.getByTestId('ai-mcp-view-tools-button').click()
    await expect(page.getByTestId('ai-mcp-tools-table')).toContainText('sse_subscribe')
    await page.keyboard.press('Escape')

    const stdioRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: stdioServerName }).first()
    await expect(stdioRow).toBeVisible()
    await expect(stdioRow).toContainText(/Stdio/i)
    await expect(stdioRow).toContainText('npx')

    const stdioRefreshResponse = waitForPostResponse(page, `/ai/mcp/refresh/${createdStdioServer.mcpId}`)
    await stdioRow.getByTestId('ai-mcp-refresh-button').click()
    await stdioRefreshResponse

    await expect.poll(async () => {
      const server = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, stdioServerName)
      return server?.tools || ''
    }, { timeout: 15000 }).toContain('stdio_exec')

    const stdioRefreshedRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: stdioServerName }).first()
    await stdioRefreshedRow.getByTestId('ai-mcp-view-tools-button').click()
    await expect(page.getByTestId('ai-mcp-tools-table')).toContainText('stdio_exec')
    await expect(page.getByTestId('ai-mcp-tools-table')).not.toContainText('sse_subscribe')
  } finally {
    await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdSseServer.mcpId).catch(() => undefined)
    await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdStdioServer.mcpId).catch(() => undefined)
  }
})
