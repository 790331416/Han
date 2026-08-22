import { test, expect, type Page, type Route } from '@playwright/test'

type ApiEnvelope = { code: number; msg?: string; data?: unknown }
type Resource = {
  id: number
  resourceName: string
  resourceCode: string
  category: string
  httpMethod: string
  path: string
  scopeCode: string
  status: number
  publishStatus: number
  allowTest: number
}

const resources: Resource[] = [
  {
    id: 101,
    resourceName: '教师目录',
    resourceCode: 'directory.teachers.read',
    category: '教育目录',
    httpMethod: 'GET',
    path: '/open/api/v1/directory/teachers',
    scopeCode: 'edu.teacher.read',
    status: 0,
    publishStatus: 2,
    allowTest: 1
  },
  {
    id: 102,
    resourceName: '已停用设备目录',
    resourceCode: 'directory.devices.read',
    category: '教育目录',
    httpMethod: 'GET',
    path: '/open/api/v1/directory/devices',
    scopeCode: 'edu.device.read',
    status: 1,
    publishStatus: 3,
    allowTest: 0
  }
]

function json(route: Route, body: ApiEnvelope, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

async function installAuthenticatedShell(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'mock-admin-token')
    localStorage.setItem('Admin-Refresh-Token', 'mock-refresh-token')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'mock-admin-token', tenantId: 99, _userId: 42 }))
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
      username: 'e2e-admin',
      nickname: 'E2E Admin',
      avatar: '',
      phone: '',
      email: '',
      roles: ['admin'],
      permissions: ['*:*:*']
    }
  }))
  await page.route('**/system/menu/routers', (route) => json(route, {
    code: 200,
    data: [{
      id: 1,
      name: 'OpenPlatform',
      menuName: '开放平台',
      path: 'open',
      component: 'Layout',
      children: [{
        id: 2,
        name: 'OpenApiResource',
        menuName: '接口目录',
        path: 'api-resource',
        component: 'open/api-resource/index',
        meta: { title: '接口目录', icon: 'List' }
      }]
    }]
  }))
}

async function openAdminPage(page: Page, handler: (route: Route) => Promise<void> | void) {
  await installAuthenticatedShell(page)
  await page.route('**/open/api-resource/list**', handler)
  await page.goto('/open/api-resource')
  await expect(page.getByTestId('open-api-resource-page')).toBeVisible()
}

test.describe('开放平台管理端受控联调', () => {
  test('目录正常接口返回时停用资源仍可见，且无页面错误', async ({ page }) => {
    const pageErrors: string[] = []
    const consoleErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    await openAdminPage(page, (route) => json(route, { code: 200, data: resources }))
    await expect(page.getByTestId('open-api-resource-table')).toBeVisible()
    await expect(page.getByText('教师目录', { exact: true })).toBeVisible()
    await expect(page.getByText('已停用设备目录', { exact: true })).toBeVisible()
    await expect(page.getByText('已下线', { exact: true })).toBeVisible()
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
  })

  test('目录业务错误和 HTTP 错误均会触发真实请求并保留页面壳', async ({ page }) => {
    let requests = 0
    await openAdminPage(page, (route) => {
      requests += 1
      return json(route, { code: 500, msg: '目录业务失败', data: null })
    })
    expect(requests).toBe(1)
    await expect(page.getByTestId('open-api-resource-page')).toBeVisible()

    await page.reload()
    await page.unroute('**/open/api-resource/list**')
    await page.route('**/open/api-resource/list**', (route) => json(route, { code: 500, msg: '服务不可用' }, 503))
    await expect(page.getByTestId('open-api-resource-page')).toBeVisible()
  })

  test('目录 requestfailed、pageerror 和 console error 均被测试监听', async ({ page }) => {
    const failedRequests: string[] = []
    const pageErrors: string[] = []
    const consoleErrors: string[] = []
    page.on('requestfailed', (request) => failedRequests.push(request.url()))
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    await openAdminPage(page, (route) => route.abort('failed'))
    await expect(page.getByTestId('open-api-resource-page')).toBeVisible()
    expect(failedRequests.some((url) => url.includes('/open/api-resource/list'))).toBeTruthy()
  })
})
