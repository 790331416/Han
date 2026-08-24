import { test, expect, type Page, type Route } from '@playwright/test'

type ApiEnvelope = { code: number; msg?: string; data?: unknown }

const app = {
  appId: 201,
  appName: 'E2E 服务端应用',
  vendorId: 301,
  appType: 'server',
  lifecycleStatus: 2,
  status: 0,
  scopes: ['edu.teacher.read']
}

const resource = {
  id: 401,
  resourceName: '教师目录',
  resourceCode: 'directory.teachers.read',
  category: '教育目录',
  httpMethod: 'GET',
  path: '/open/api/v1/directory/teachers',
  scopeCode: 'edu.teacher.read',
  status: 0,
  publishStatus: 2,
  allowApply: 1,
  allowTest: 1,
  currentVersion: {
    version: 'v1',
    openapiSchema: {
      paths: {
        '/open/api/v1/directory/teachers': {
          get: {
            parameters: [{ name: 'schoolId', in: 'query', required: false, description: '授权学校 ID', schema: { type: 'integer', example: 1 } }]
          }
        }
      }
    },
    requestExample: { pageNum: 1, pageSize: 5 },
    responseExamples: { code: 200, data: { rows: [], total: 0 } }
  }
}

function json(route: Route, body: ApiEnvelope, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installPortalShell(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'mock-portal-token')
    localStorage.setItem('Admin-Refresh-Token', 'mock-refresh-token')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'mock-portal-token', tenantId: 99, _userId: 42 }))
  })
  await page.route('**/system/public/brand', (route) => json(route, {
    code: 200,
    data: { fullName: 'E2E', shortName: 'E2E', displayMode: 'FULL_NAME', loginSubtitle: '', logoUrl: '' }
  }))
  await page.route('**/runtime/capabilities**', (route) => json(route, {
    code: 200,
    data: { tier: 'small', enabledModules: [], optionalServices: {}, featureFlags: {} }
  }))
  await page.route('**/system/notice/unreadCount', (route) => json(route, { code: 200, data: 0 }))
  await page.route('**/system/notice/latest**', (route) => json(route, { code: 200, data: [] }))
  await page.route('**/system/notice/sse', (route) => route.fulfill({
    status: 200,
    contentType: 'text/event-stream',
    body: 'event: connected\ndata: {}\n\n'
  }))
  await page.route('**/system/user/current', (route) => json(route, {
    code: 200,
    data: {
      userId: 42,
      tenantId: 99,
      deptId: 1,
      username: 'e2e-vendor',
      nickname: 'E2E Vendor',
      avatar: '',
      phone: '',
      email: '',
      roles: ['vendor'],
      permissions: ['*:*:*']
    }
  }))
  await page.route('**/system/menu/routers', (route) => json(route, {
    code: 200,
    data: [{
      id: 1,
      name: 'OpenPortal',
      menuName: '厂商门户',
      path: 'open',
      component: 'Layout',
      children: [{
        id: 2,
        name: 'OpenPortalPage',
        menuName: '厂商门户',
        path: 'portal',
        component: 'open/portal/index',
        meta: { title: '厂商门户', icon: 'Platform' }
      }]
    }]
  }))
  await page.route('**/system/dict/data/type/open_identity_scope', (route) => json(route, { code: 200, data: [] }))
  await page.route('**/open/vendor/my', (route) => json(route, {
    code: 200,
    data: [{ id: 301, name: 'E2E 厂商', qualificationNo: 'E2E-USCC', status: 2 }]
  }))
  await page.route('**/open/vendor/301', (route) => json(route, {
    code: 200,
    data: { id: 301, name: 'E2E 厂商', qualificationNo: 'E2E-USCC', status: 2, users: [], apps: [app] }
  }))
  await page.route('**/open/app/list**', (route) => json(route, {
    code: 200,
    data: { rows: [app], total: 1, pageNum: 1, pageSize: 100, pages: 1 }
  }))
  await page.route('**/open/api-resource/list**', (route) => json(route, { code: 200, data: [resource] }))
  await page.route('**/open/api-resource/401', (route) => json(route, { code: 200, data: resource }))
  await page.route('**/open/api-resource/401**', (route) => json(route, { code: 200, data: resource }))
  await page.route('**/open/authorization/app/201', (route) => json(route, { code: 200, data: [{ id: 501, appId: app.appId, resourceId: resource.id, environment: 'SANDBOX', scopes: resource.scopeCode, status: 1 }] }))
  await page.route('**/open/authorization/credential/list**', (route) => json(route, { code: 200, data: [{ id: 601, appId: app.appId, environment: 'SANDBOX', clientId: 'e2e-client-id', status: 0 }] }))
  await page.route('**/open/debug/run/list**', (route) => json(route, { code: 200, data: [] }))
}

async function openPortal(page: Page, tokenHandler: (route: Route) => Promise<void> | void) {
  await installPortalShell(page)
  await page.route('**/open/oauth2/token', tokenHandler)
  await page.goto('/open/portal')
  await expect(page.getByTestId('open-portal-page')).toBeVisible()
  await page.getByRole('tab', { name: '在线调测' }).click()
  await selectDebugInputs(page)
}

async function selectDebugInputs(page: Page) {
  const debugForm = page.locator('.debug-form')
  const selects = debugForm.locator('.el-select')
  await selects.nth(0).click()
  await page.locator('.el-select-dropdown__item').filter({ hasText: app.appName }).last().click()
  await selects.nth(1).click()
  await page.locator('.el-select-dropdown__item').filter({ hasText: resource.resourceName }).last().click()
  await debugForm.locator('.el-form-item').filter({ hasText: 'Client ID' }).locator('input').fill('e2e-client-id')
  await debugForm.locator('.el-form-item').filter({ hasText: 'Client Secret' }).locator('input').fill('e2e-client-secret')
  await debugForm.locator('textarea').first().fill('{"pageNum":1,"pageSize":5}')
}

test.describe('厂商门户在线调测受控联调', () => {
  test('Token→目录接口→审计链路成功，敏感字段不进入审计且 Secret 保留在当前页面', async ({ page }) => {
    let tokenRequestBody = ''
    let directoryAuthorization = ''
    let auditRequestBody = ''
    const pageErrors: string[] = []
    const consoleErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    await openPortal(page, async (route) => {
      tokenRequestBody = route.request().postData() || ''
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ access_token: 'e2e-access-token', token_type: 'Bearer', expires_in: 300 })
      })
    })
    await page.route('**/open/api/v1/directory/teachers**', async (route) => {
      directoryAuthorization = (await route.request().headerValue('authorization')) || ''
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { rows: [], total: 0, pageNum: 1, pageSize: 5, pages: 0 } })
      })
    })
    await page.route('**/open/debug/run/add', async (route) => {
      auditRequestBody = route.request().postData() || ''
      await json(route, { code: 200, data: { id: 9001, appId: app.appId, resourceId: resource.id, statusCode: 200 } })
    })

    await page.getByRole('button', { name: '获取 Token 并调测' }).click()
    await expect(page.locator('.debug-response')).toContainText('rows')
    await expect(page.locator('.debug-parameter-table')).toContainText('schoolId')
    await expect(page.locator('.debug-form').locator('.el-form-item').filter({ hasText: 'Client Secret' }).locator('input')).toHaveValue('e2e-client-secret')

    expect(tokenRequestBody).toContain('client_secret=e2e-client-secret')
    expect(directoryAuthorization).toBe('Bearer e2e-access-token')
    expect(auditRequestBody).not.toContain('e2e-client-secret')
    expect(auditRequestBody).not.toContain('e2e-access-token')
    expect(auditRequestBody).not.toContain('responseBody')
    const audit = JSON.parse(auditRequestBody) as Record<string, unknown>
    expect(audit).toEqual(expect.objectContaining({ appId: app.appId, resourceId: resource.id, statusCode: 200 }))
    expect(audit).not.toHaveProperty('token')
    expect(audit).not.toHaveProperty('clientSecret')
    expect(audit).not.toHaveProperty('responseBody')
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
  })

  test('Token 业务错误和 HTTP 错误会中断调测并保留 Secret 便于重试', async ({ page }) => {
    await openPortal(page, (route) => json(route, { code: 401, msg: '客户端凭证无效' }))
    await page.getByRole('button', { name: '获取 Token 并调测' }).click()
    await expect(page.locator('.debug-response')).toContainText('客户端凭证无效')
    await expect(page.locator('.debug-form').locator('.el-form-item').filter({ hasText: 'Client Secret' }).locator('input')).toHaveValue('e2e-client-secret')

    await page.reload()
    await page.getByRole('tab', { name: '在线调测' }).click()
    await selectDebugInputs(page)
    await page.unroute('**/open/oauth2/token')
    await page.route('**/open/oauth2/token', (route) => json(route, { code: 500, msg: '认证服务不可用' }, 503))
    await page.getByRole('button', { name: '获取 Token 并调测' }).click()
    await expect(page.locator('.debug-response')).toContainText('认证服务不可用')
    await expect(page.locator('.debug-form').locator('.el-form-item').filter({ hasText: 'Client Secret' }).locator('input')).toHaveValue('e2e-client-secret')
  })

  test('Token requestfailed 与页面错误监听可观测，且中断后不提交审计', async ({ page }) => {
    const failedRequests: string[] = []
    const pageErrors: string[] = []
    const consoleErrors: string[] = []
    let auditRequests = 0
    page.on('requestfailed', (request) => failedRequests.push(request.url()))
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    await openPortal(page, (route) => route.abort('failed'))
    await page.route('**/open/debug/run/add', () => { auditRequests += 1 })

    await page.getByRole('button', { name: '获取 Token 并调测' }).click()
    await expect(page.locator('.debug-response')).toContainText('Failed to fetch')
    await expect(page.locator('.debug-form').locator('.el-form-item').filter({ hasText: 'Client Secret' }).locator('input')).toHaveValue('e2e-client-secret')
    expect(failedRequests.some((url) => url.includes('/open/oauth2/token'))).toBeTruthy()
    expect(auditRequests).toBe(0)
  })

  test('调测审计保存失败时只提交一次记录', async ({ page }) => {
    let auditRequests = 0
    await openPortal(page, (route) => route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ access_token: 'e2e-access-token' })
    }))
    await page.route('**/open/api/v1/directory/teachers**', (route) => route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ code: 200, data: { rows: [] } })
    }))
    await page.route('**/open/debug/run/add', (route) => {
      auditRequests += 1
      return json(route, { code: 500, msg: '审计记录保存失败' }, 500)
    })

    await page.getByRole('button', { name: '获取 Token 并调测' }).click()

    await expect(page.locator('.el-message--error').last()).toBeVisible()
    expect(auditRequests).toBe(1)
  })
})
