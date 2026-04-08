import { expect, type ConsoleMessage, type Page, type Request, type Response } from '@playwright/test'

export type RuntimeTier = 'small' | 'medium' | 'full'

export interface PageHealthTarget {
  path: string
  minTier: RuntimeTier
  testId?: string
  rootSelector?: string
}

interface PageIssue {
  kind: 'http' | 'business' | 'console' | 'pageerror' | 'requestfailed'
  url?: string
  message: string
  status?: number
}

const trackedResourceTypes = new Set(['document', 'xhr', 'fetch', 'script', 'stylesheet'])
const ignoredConsolePatterns = [
  'EventSource',
  'text/event-stream',
  '[el-switch] [API] active-value',
  '[el-radio] [API] label act as value'
]

const tierOrder: Record<RuntimeTier, number> = { small: 0, medium: 1, full: 2 }

export const pageHealthTargets: PageHealthTarget[] = [
  { path: '/dashboard', minTier: 'small', testId: 'dashboard-page' },
  { path: '/system/user', minTier: 'small' },
  { path: '/system/role', minTier: 'small' },
  { path: '/system/menu', minTier: 'small' },
  { path: '/system/dept', minTier: 'small' },
  { path: '/system/post', minTier: 'small' },
  { path: '/system/dict', minTier: 'small' },
  { path: '/system/config', minTier: 'small' },
  { path: '/system/notice', minTier: 'small' },
  { path: '/system/operlog', minTier: 'small' },
  { path: '/system/loginlog', minTier: 'small' },
  { path: '/system/online', minTier: 'small' },
  { path: '/system/server', minTier: 'small' },
  { path: '/system/cache-monitor', minTier: 'small' },
  { path: '/job/list', minTier: 'small' },
  { path: '/job/log', minTier: 'small' },
  { path: '/system/tenant', minTier: 'medium', testId: 'tenant-page' },
  { path: '/system/tenant-package', minTier: 'medium', testId: 'tenant-package-page' },
  { path: '/system/tenant-quota', minTier: 'medium', testId: 'tenant-quota-page' },
  { path: '/workflow/definition', minTier: 'medium' },
  { path: '/workflow/instance', minTier: 'medium' },
  { path: '/workflow/todo', minTier: 'medium' },
  { path: '/workflow/done', minTier: 'medium' },
  { path: '/open/app', minTier: 'medium', testId: 'open-app-page' },
  { path: '/system/oss-config', minTier: 'medium', testId: 'oss-config-page' },
  { path: '/tool/gen', minTier: 'full' },
  { path: '/ai/application', minTier: 'full', testId: 'ai-application-page' },
  { path: '/ai/model', minTier: 'full' },
  { path: '/ai/knowledge', minTier: 'full' },
  { path: '/ai/mcp', minTier: 'full' },
  { path: '/ai/agent', minTier: 'full', testId: 'ai-agent-page' },
  { path: '/ai/workflow', minTier: 'full' },
  { path: '/ai/prompt', minTier: 'full' },
  { path: '/ai/token', minTier: 'full' },
  { path: '/ai/chat', minTier: 'full' }
]

export function shouldRunForTier(target: PageHealthTarget, tier: RuntimeTier): boolean {
  return tierOrder[tier] >= tierOrder[target.minTier]
}

export async function assertPageHealthy(page: Page, apiBaseUrl: string, target: PageHealthTarget): Promise<void> {
  const issues = new Map<string, PageIssue>()
  const allowedOrigins = new Set<string>([new URL(apiBaseUrl).origin])

  const addIssue = (issue: PageIssue) => {
    const key = `${issue.kind}|${issue.status || ''}|${issue.url || ''}|${issue.message}`
    issues.set(key, issue)
  }

  const responseHandler = async (response: Response) => {
    if (!shouldTrackUrl(response.url(), allowedOrigins)) {
      return
    }
    const resourceType = response.request().resourceType()
    if (!trackedResourceTypes.has(resourceType)) {
      return
    }

    const status = response.status()
    if (status >= 400) {
      addIssue({
        kind: 'http',
        url: response.url(),
        status,
        message: `${resourceType} returned HTTP ${status}`
      })
      return
    }

    if (resourceType !== 'xhr' && resourceType !== 'fetch') {
      return
    }

    const contentType = response.headers()['content-type'] || ''
    if (!contentType.includes('application/json')) {
      return
    }

    try {
      const body = await response.json()
      if (typeof body?.code === 'number' && body.code !== 200) {
        addIssue({
          kind: 'business',
          url: response.url(),
          status,
          message: `business code ${body.code}: ${String(body?.msg || 'unknown error')}`
        })
      }
    } catch {
      // ignore malformed JSON payloads here; request failures are covered separately
    }
  }

  const requestFailedHandler = (request: Request) => {
    if (!shouldTrackUrl(request.url(), allowedOrigins) || !trackedResourceTypes.has(request.resourceType())) {
      return
    }
    addIssue({
      kind: 'requestfailed',
      url: request.url(),
      message: request.failure()?.errorText || 'request failed'
    })
  }

  const consoleHandler = (message: ConsoleMessage) => {
    if (message.type() !== 'error') {
      return
    }
    const text = message.text()
    if (ignoredConsolePatterns.some((pattern) => text.includes(pattern))) {
      return
    }
    addIssue({
      kind: 'console',
      message: text
    })
  }

  const pageErrorHandler = (error: Error) => {
    addIssue({
      kind: 'pageerror',
      message: error.message
    })
  }

  page.on('response', responseHandler)
  page.on('requestfailed', requestFailedHandler)
  page.on('console', consoleHandler)
  page.on('pageerror', pageErrorHandler)

  try {
    await page.goto(target.path, { waitUntil: 'domcontentloaded' })
    allowedOrigins.add(new URL(page.url()).origin)

    await expect
      .poll(() => new URL(page.url()).pathname, { timeout: 15000 })
      .toBe(target.path)

    await expect(resolvePageRoot(page, target)).toBeVisible()
    await page.waitForLoadState('networkidle', { timeout: 3000 }).catch(() => undefined)
    await page.waitForTimeout(1200)
  } finally {
    page.off('response', responseHandler)
    page.off('requestfailed', requestFailedHandler)
    page.off('console', consoleHandler)
    page.off('pageerror', pageErrorHandler)
  }

  const issueList = [...issues.values()]
  const message = issueList.length === 0
    ? ''
    : issueList
      .map((issue) => {
        const prefix = issue.status ? `[${issue.status}] ` : ''
        const url = issue.url ? ` ${issue.url}` : ''
        return `- ${issue.kind}: ${prefix}${issue.message}${url}`
      })
      .join('\n')

  expect(issueList, `page health failed for ${target.path}\n${message}`).toEqual([])
}

function resolvePageRoot(page: Page, target: PageHealthTarget) {
  if (target.testId) {
    return page.getByTestId(target.testId)
  }
  if (target.rootSelector) {
    return page.locator(target.rootSelector).first()
  }
  return page.locator('[data-testid$="-page"], .workflow-designer, .dashboard, .app-container').first()
}

function shouldTrackUrl(url: string, allowedOrigins: Set<string>): boolean {
  try {
    const parsed = new URL(url)
    return allowedOrigins.has(parsed.origin)
  } catch {
    return false
  }
}
