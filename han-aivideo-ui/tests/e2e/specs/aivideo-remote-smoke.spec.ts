import { expect, test, type ConsoleMessage, type Page, type Request, type Response } from '@playwright/test'

const remoteEnabled = process.env.PW_AIVIDEO_REMOTE === '1'
const inspectAdminPages = process.env.PW_AIVIDEO_ADMIN !== '0'

type Issue = { kind: 'http' | 'business' | 'console' | 'pageerror' | 'requestfailed'; message: string; url?: string }

function requireEnvironment(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`真实 AIVideo 回归缺少环境变量 ${name}`)
  return value
}

function installHealthMonitor(page: Page, origins: Set<string>) {
  const issues = new Map<string, Issue>()
  const add = (issue: Issue) => issues.set(`${issue.kind}|${issue.url || ''}|${issue.message}`, issue)
  const shouldTrack = (url: string) => {
    try { return origins.has(new URL(url).origin) } catch { return false }
  }
  const onResponse = async (response: Response) => {
    if (!shouldTrack(response.url())) return
    const kind = response.request().resourceType()
    if (!['document', 'xhr', 'fetch', 'script', 'stylesheet', 'image', 'media'].includes(kind)) return
    if (response.status() >= 400) {
      add({ kind: 'http', url: response.url(), message: `HTTP ${response.status()}` })
      return
    }
    if (!['xhr', 'fetch'].includes(kind) || !(response.headers()['content-type'] || '').includes('application/json')) return
    try {
      const body = await response.json()
      if (typeof body?.code === 'number' && body.code !== 200) {
        add({ kind: 'business', url: response.url(), message: `code=${body.code} ${String(body.msg || '')}` })
      }
    } catch {
      // Status and request-failure checks cover malformed payloads.
    }
  }
  const onFailed = (request: Request) => {
    if (shouldTrack(request.url())) add({ kind: 'requestfailed', url: request.url(), message: request.failure()?.errorText || 'request failed' })
  }
  const onConsole = (message: ConsoleMessage) => {
    if (message.type() === 'error' && !message.text().includes('EventSource')) add({ kind: 'console', message: message.text() })
  }
  const onPageError = (error: Error) => add({ kind: 'pageerror', message: error.message })
  page.on('response', onResponse)
  page.on('requestfailed', onFailed)
  page.on('console', onConsole)
  page.on('pageerror', onPageError)
  return {
    issues,
    stop() {
      page.off('response', onResponse)
      page.off('requestfailed', onFailed)
      page.off('console', onConsole)
      page.off('pageerror', onPageError)
    }
  }
}

async function injectSession(page: Page) {
  const payload = {
    accessToken: requireEnvironment('PW_ACCESS_TOKEN'),
    refreshToken: process.env.PW_REFRESH_TOKEN || '',
    tenantId: process.env.PW_TENANT_ID || null,
    userId: process.env.PW_USER_ID || null
  }
  await page.evaluate(({ accessToken, refreshToken, tenantId, userId }) => {
    localStorage.setItem('Admin-Token', accessToken)
    if (refreshToken) localStorage.setItem('Admin-Refresh-Token', refreshToken)
    localStorage.setItem('HAN-user', JSON.stringify({ token: accessToken, tenantId, _userId: userId }))
  }, payload)
}

async function expectPath(page: Page, path: string) {
  await expect.poll(() => new URL(page.url()).pathname, { timeout: 15_000 }).toBe(path)
}

async function visitWorkbench(page: Page, projectId: string) {
  const path = `/studio/projects/${projectId}/workbench`
  await page.goto(path, { waitUntil: 'domcontentloaded' })
  await expectPath(page, path)
  await expect(page.locator('.workbench-page')).toBeVisible()
  for (const label of ['原文', '润色', '剧本', '资产', '任务']) {
    const button = page.locator('.flow-item').filter({ hasText: label })
    await expect(button).toBeVisible()
    await button.click()
  }
  await page.locator('.flow-item').filter({ hasText: '资产' }).click()
  for (const label of ['角色', '场景', '道具', '分镜', '剪辑', '后期语音']) {
    const tab = page.getByRole('tab', { name: label })
    await expect(tab).toBeVisible()
    await tab.click()
  }
}

test.describe('95 AIVideo 真实同会话回归', () => {
  test.skip(!remoteEnabled, '设置 PW_AIVIDEO_REMOTE=1 后才执行真实环境回归')

  test('登录页控件、项目、工作台和管理页没有未处理运行错误', async ({ page }) => {
    const baseUrl = requireEnvironment('PW_BASE_URL')
    const projectId = requireEnvironment('PW_AIVIDEO_PROJECT_ID')
    const origins = new Set([new URL(baseUrl).origin])
    if (process.env.PW_API_URL) origins.add(new URL(process.env.PW_API_URL).origin)

    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('login-username')).toBeVisible()
    await expect(page.getByTestId('login-password')).toBeVisible()
    await expect(page.getByTestId('login-submit')).toBeVisible()
    await injectSession(page)

    const monitor = installHealthMonitor(page, origins)
    try {
      await page.goto('/studio/projects', { waitUntil: 'domcontentloaded' })
      await expectPath(page, '/studio/projects')
      await expect(page.getByRole('button', { name: '新建项目' })).toBeVisible()
      await expect(page.getByPlaceholder('请输入项目名称')).toBeVisible()
      await page.getByRole('button', { name: '新建项目' }).click()
      await expectPath(page, '/studio/projects/create')
      await expect(page.getByRole('button', { name: '保存并进入工作台' })).toBeVisible()
      await page.waitForLoadState('networkidle', { timeout: 10_000 })

      await visitWorkbench(page, projectId)
      if (inspectAdminPages) {
        await page.goto('/ai/aivideo/tasks', { waitUntil: 'domcontentloaded' })
        await expectPath(page, '/ai/aivideo/tasks')
        await expect(page.locator('.app-container').getByText('短剧生成任务')).toBeVisible()
        await page.goto('/ai/aivideo/settings', { waitUntil: 'domcontentloaded' })
        await expectPath(page, '/ai/aivideo/settings')
        await expect(page.locator('.app-container .card-header').getByText('短剧基础配置')).toBeVisible()
      }
      await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => undefined)
      await page.waitForTimeout(1_000)
    } finally {
      monitor.stop()
    }

    const issueList = [...monitor.issues.values()]
    const details = issueList.map((item) => `- ${item.kind}: ${item.message}${item.url ? ` ${item.url}` : ''}`).join('\n')
    expect(issueList, `AIVideo 真实页面健康检查失败\n${details}`).toEqual([])
  })
})