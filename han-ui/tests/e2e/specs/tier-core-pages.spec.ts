import { test, expect, e2eRuntime } from '../fixtures/test'

interface RouteSmokeTarget {
  path: string
  testId?: string
}

const smallTierRoutes: RouteSmokeTarget[] = [
  { path: '/system/user' },
  { path: '/system/role' },
  { path: '/system/menu' },
  { path: '/system/dept' },
  { path: '/system/post' },
  { path: '/system/dict' },
  { path: '/system/config' },
  { path: '/system/notice' },
  { path: '/system/operlog' },
  { path: '/system/loginlog' },
  { path: '/system/online' },
  { path: '/system/server' },
  { path: '/system/cache-monitor' },
  { path: '/job/list' },
  { path: '/job/log' }
]

const mediumTierRoutes: RouteSmokeTarget[] = [
  { path: '/system/tenant', testId: 'tenant-page' },
  { path: '/system/tenant-package', testId: 'tenant-package-page' },
  { path: '/system/tenant-quota', testId: 'tenant-quota-page' },
  { path: '/workflow/definition' },
  { path: '/workflow/instance' },
  { path: '/workflow/todo' },
  { path: '/workflow/done' },
  { path: '/open/app', testId: 'open-app-page' },
  { path: '/system/oss-config', testId: 'oss-config-page' }
]

test('core tier routes should be reachable for the current runtime tier', async ({ authenticatedPage }) => {
  const capabilityResponse = await authenticatedPage.request.get(`${e2eRuntime.apiBaseUrl}/system/runtime/capabilities`)
  const capabilityJson = await capabilityResponse.json()
  const tier = capabilityJson?.data?.tier ?? 'small'
  const routes = [...smallTierRoutes]

  if (tier === 'medium' || tier === 'full') {
    routes.push(...mediumTierRoutes)
  }

  for (const route of routes) {
    await test.step(`open ${route.path}`, async () => {
      await authenticatedPage.goto(route.path)
      await expect
        .poll(() => new URL(authenticatedPage.url()).pathname, { timeout: 15000 })
        .toBe(route.path)

      const pageRoot = route.testId
        ? authenticatedPage.getByTestId(route.testId)
        : authenticatedPage.locator('.app-container').first()

      await expect(pageRoot).toBeVisible()
    })
  }
})
