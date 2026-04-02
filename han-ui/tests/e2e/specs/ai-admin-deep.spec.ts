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
  findMcpServerByName
} from '../utils/ai-admin'

const KNOWLEDGE_FILE_PATH = path.resolve(process.cwd(), 'tests/e2e/fixtures/files/ai-knowledge-upload.txt')
const KNOWLEDGE_FILE_NAME = 'ai-knowledge-upload.txt'
const KNOWLEDGE_HIT_PHRASE = '牛马知识命中短语样本'

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

test('ai knowledge page should upload document, reindex, and complete hit test', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const kbName = buildUniqueName('知识库回归')
  const createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    kbName,
    kbType: 'general',
    description: '知识库深度回归样本'
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

    const uploadResponse = waitForPostResponse(page, `/ai/kb/${createdKb.kbId}/document/upload`)
    await page.getByTestId('ai-knowledge-upload').locator('input[type="file"]').setInputFiles(KNOWLEDGE_FILE_PATH)
    await uploadResponse

    await expect.poll(async () => {
      const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
      const target = docs.find((item) => item.docName === KNOWLEDGE_FILE_NAME)
      return target?.indexStatus || ''
    }, { timeout: 30000 }).toBe('completed')

    const docRow = page.getByTestId('ai-knowledge-doc-table').locator('.el-table__row', { hasText: KNOWLEDGE_FILE_NAME }).first()
    await expect(docRow).toBeVisible()

    const reindexResponse = waitForPostResponse(page, '/ai/kb/document/reindex/')
    await docRow.getByTestId('ai-knowledge-reindex-button').click()
    await reindexResponse

    await expect.poll(async () => {
      const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
      const target = docs.find((item) => item.docName === KNOWLEDGE_FILE_NAME)
      return target?.indexStatus || ''
    }, { timeout: 30000 }).toBe('completed')

    await page.keyboard.press('Escape')

    await card.getByTestId(`ai-knowledge-card-actions-${createdKb.kbId}`).click()
    await page.locator('[data-testid="ai-knowledge-hit-test-command"]:visible').click()
    await expect(page.getByTestId('ai-knowledge-hit-test-dialog')).toBeVisible()

    await page.getByTestId('ai-knowledge-hit-test-input').fill(KNOWLEDGE_HIT_PHRASE)
    const hitTestResponse = waitForPostResponse(page, `/ai/kb/hit-test/${createdKb.kbId}`)
    await page.getByTestId('ai-knowledge-hit-test-submit').click()
    await hitTestResponse

    const hitResult = page.getByTestId('ai-knowledge-hit-test-result').first()
    await expect(hitResult).toBeVisible()
    await hitResult.click()
    await expect(page.getByTestId('ai-knowledge-hit-test-results')).toContainText(KNOWLEDGE_HIT_PHRASE)
  } finally {
    await deleteKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId).catch(() => undefined)
  }
})

test('ai prompt page should render preview variables with real template data', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const templateName = buildUniqueName('提示词回归')
  const createdTemplate = await createPromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    templateName,
    category: 'user',
    content: '你好，{{name}}，欢迎来到{{topic}}。',
    variables: '["name","topic"]'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/prompt/list')
    await page.goto('/ai/prompt')
    await listResponse
    await page.waitForLoadState('networkidle')

    const row = page.getByTestId('ai-prompt-table').locator('.el-table__row', { hasText: templateName }).first()
    await expect(row).toBeVisible()

    await row.getByTestId('ai-prompt-preview-button').click()
    await expect(page.getByTestId('ai-prompt-preview-panel')).toBeVisible()

    await page.getByTestId('ai-prompt-var-input-name').fill('小韩')
    await page.getByTestId('ai-prompt-var-input-topic').fill('结构化测试')

    const renderResponse = waitForPostResponse(page, `/ai/prompt/render/${createdTemplate.templateId}`)
    await page.getByTestId('ai-prompt-render-button').click()
    await renderResponse

    await expect(page.getByTestId('ai-prompt-rendered-content')).toContainText('你好，小韩，欢迎来到结构化测试。')
  } finally {
    await deletePromptTemplate(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdTemplate.templateId).catch(() => undefined)
  }
})

test('ai mcp page should refresh tools and show generated tool list', async ({ authenticatedPage, request, authSession }) => {
  const page = authenticatedPage
  const serverName = buildUniqueName('工具服务回归')
  const createdServer = await createMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    serverName,
    transportType: 'sse'
  })

  try {
    const listResponse = waitForGetResponse(page, '/ai/mcp/list')
    await page.goto('/ai/mcp')
    await listResponse
    await page.waitForLoadState('networkidle')

    const row = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: serverName }).first()
    await expect(row).toBeVisible()

    const refreshResponse = waitForPostResponse(page, `/ai/mcp/refresh/${createdServer.mcpId}`)
    const refreshListResponse = waitForGetResponse(page, '/ai/mcp/list')
    await row.getByTestId('ai-mcp-refresh-button').click()
    await refreshResponse
    await refreshListResponse

    await expect.poll(async () => {
      const server = await findMcpServerByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, serverName)
      return server?.tools || ''
    }, { timeout: 15000 }).toContain('health_check')

    const refreshedRow = page.getByTestId('ai-mcp-table').locator('.el-table__row', { hasText: serverName }).first()
    await refreshedRow.getByTestId('ai-mcp-view-tools-button').click()
    await expect(page.getByTestId('ai-mcp-tools-dialog')).toBeVisible()
    await expect(page.getByTestId('ai-mcp-tools-table')).toContainText('health_check')
    await expect(page.getByTestId('ai-mcp-tools-table')).toContainText('sse_subscribe')
  } finally {
    await deleteMcpServer(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdServer.mcpId).catch(() => undefined)
  }
})
