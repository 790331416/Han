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
  findPromptTemplateByName,
  tryCreateMcpServer
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
  const kbName = buildUniqueName('知识库生命周期')
  const createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    kbName,
    kbType: 'general',
    description: '知识库生命周期回归样本'
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
  const templateName = buildUniqueName('提示词编辑')
  const createdTemplate = await createPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    templateName,
    category: 'user',
    content: '你好，{{name}}，这里是{{topic}}。',
    variables: '["name","topic"]'
  })

  const editedContent = '欢迎{{name}}来到{{topic}}。'

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

    await page.getByTestId('ai-prompt-var-input-name').fill('牛马')
    await page.getByTestId('ai-prompt-var-input-topic').fill('结构化回归')
    const renderResponse = waitForPostResponse(page, `/ai/prompt/render/${createdTemplate.templateId}`)
    await page.getByTestId('ai-prompt-render-button').click()
    await renderResponse
    await expect(page.getByTestId('ai-prompt-rendered-content')).toContainText('欢迎牛马来到结构化回归。')
  } finally {
    await deletePromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdTemplate.templateId).catch(() => undefined)
  }
})

test('ai mcp page should reject stdio transport and surface real refresh failure', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const sseServerName = buildUniqueName('流式工具服务')
  const stdioServerName = buildUniqueName('命令工具服务')

  // G1-5 stdio 处置（决策 D4）：保存层前后端双拦截，创建 stdio 记录应被明确拒绝
  const stdioRejected = await tryCreateMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName: stdioServerName,
    transportType: 'stdio',
    command: 'npx',
    args: '["-y","@modelcontextprotocol/server-filesystem"]'
  })
  expect(stdioRejected.code).not.toBe(200)
  expect(stdioRejected.msg || '').toContain('stdio 传输暂不可用')
  const stdioServer = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, stdioServerName)
  expect(stdioServer).toBeNull()

  const createdSseServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName: sseServerName,
    transportType: 'sse'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/mcp/list')
    await page.goto('/ai/mcp')
    await listResponse
    await page.waitForLoadState('networkidle')

    const sseRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: sseServerName }).first()
    await expect(sseRow).toBeVisible()
    await expect(sseRow).toContainText('SSE')

    // 创建表单中 stdio 选项应处于禁用状态（G1-5 前端拦截）
    await page.getByTestId('ai-mcp-create-button').click()
    const mcpForm = page.getByTestId('ai-mcp-form')
    await expect(mcpForm).toBeVisible()
    const stdioOption = mcpForm.locator('.el-radio-button', { hasText: 'Stdio' }).first()
    await expect(stdioOption).toHaveClass(/is-disabled/)
    await page.keyboard.press('Escape')

    // G1-5 后刷新工具为真连 MCP server：占位地址不可达应返回可诊断失败，工具列表保持为空
    const sseRefreshResponse = waitForPostResponse(page, `/ai/mcp/refresh/${createdSseServer.mcpId}`)
    await sseRow.getByTestId('ai-mcp-refresh-button').click()
    const refreshBody = (await (await sseRefreshResponse).json()) as { code: number; msg?: string }
    expect(refreshBody.code).not.toBe(200)
    expect(refreshBody.msg || '').toContain('MCP 服务连接失败')

    const latestSseServer = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, sseServerName)
    expect(JSON.parse(latestSseServer?.tools || '[]')).toEqual([])

    const sseRefreshedRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: sseServerName }).first()
    await sseRefreshedRow.getByTestId('ai-mcp-view-tools-button').click()
    await expect(page.getByTestId('ai-mcp-tools-dialog')).toBeVisible()
    await expect(page.getByTestId('ai-mcp-tools-dialog')).toContainText('暂无工具，请先刷新')
  } finally {
    await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdSseServer.mcpId).catch(() => undefined)
  }
})
