import { test, expect } from '@playwright/test'

function responseBody(data: unknown) {
  return JSON.stringify({ code: 200, msg: '操作成功', data, timestamp: Date.now() })
}

test('已登录用户访问旧 404 地址时回到首页', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('Admin-Token', 'router-test-token'))
  await page.route('**/system/public/brand', (route) => route.fulfill({
    contentType: 'application/json', body: responseBody({ fullName: '测试平台', shortName: '测试', displayMode: 'FULL_NAME', loginSubtitle: '', logoUrl: '' })
  }))
  await page.route('**/system/runtime/capabilities**', (route) => route.fulfill({
    contentType: 'application/json', body: responseBody({ tier: 'small', enabledModules: [], optionalServices: {}, featureFlags: {} })
  }))
  await page.route('**/system/user/current', (route) => route.fulfill({
    contentType: 'application/json', body: responseBody({ userId: 1, username: 'admin', nickname: '管理员', tenantId: 1, roles: ['admin'], permissions: ['*:*:*'] })
  }))
  await page.route('**/system/menu/routers', (route) => route.fulfill({
    contentType: 'application/json', body: responseBody([{ id: 1, menuName: '首页', path: 'dashboard', component: 'dashboard/index', menuType: 'C', visible: 0 }])
  }))
  await page.route('**/system/dashboard/stats', (route) => route.fulfill({ contentType: 'application/json', body: responseBody({}) }))
  await page.route('**/system/dashboard/charts', (route) => route.fulfill({ contentType: 'application/json', body: responseBody({}) }))

  await page.goto('/404')
  await page.waitForURL((url) => url.pathname.endsWith('/dashboard'), { timeout: 15000 })
  await expect(page.getByTestId('dashboard-page')).toBeVisible()
})
