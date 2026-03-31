import { test, expect, e2eRuntime } from '../fixtures/test'

test('sidebar should respect runtime capabilities on medium tier', async ({ authenticatedPage }) => {
  const page = authenticatedPage
  const capabilityResponse = await page.request.get(`${e2eRuntime.apiBaseUrl}/system/runtime/capabilities`)
  const capabilityJson = await capabilityResponse.json()
  const enabledModules = capabilityJson?.data?.enabledModules ?? []
  const featureFlags = capabilityJson?.data?.featureFlags ?? {}
  const hasJob = enabledModules.includes('job')
  const hasOpenPlatform = enabledModules.includes('open') && Boolean(featureFlags.openPlatform)
  const hasTenant = enabledModules.includes('tenant') && Boolean(featureFlags.tenantSelect)
  const hasOssConfig = Boolean(featureFlags.ossConfig)

  await page.goto('/')
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
  await expect(page.getByTestId('dashboard-page')).toBeVisible()

  if (hasJob) {
    await expect(page.getByTestId('sidebar-menu-job')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-job')).toHaveCount(0)
  }

  if (hasOpenPlatform) {
    await expect(page.getByTestId('sidebar-menu-openapp')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-openapp')).toHaveCount(0)
  }

  const systemMenuTitle = page.locator('[data-testid="sidebar-menu-system"] .el-sub-menu__title')
  await expect(systemMenuTitle).toBeVisible()
  await systemMenuTitle.click()

  if (hasTenant) {
    await expect(page.getByTestId('sidebar-menu-tenant')).toBeVisible()
    await expect(page.getByTestId('sidebar-menu-tenantpackage')).toBeVisible()
    await expect(page.getByTestId('sidebar-menu-tenantquota')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-tenant')).toHaveCount(0)
    await expect(page.getByTestId('sidebar-menu-tenantpackage')).toHaveCount(0)
    await expect(page.getByTestId('sidebar-menu-tenantquota')).toHaveCount(0)
  }

  if (hasOssConfig) {
    await expect(page.getByTestId('sidebar-menu-ossconfig')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-ossconfig')).toHaveCount(0)
  }

  if (enabledModules.includes('workflow') && featureFlags.workflow) {
    await expect(page.getByTestId('sidebar-menu-workflow')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-workflow')).toHaveCount(0)
  }

  if (enabledModules.includes('ai') && featureFlags.ai) {
    await expect(page.getByTestId('sidebar-menu-ai')).toBeVisible()
  } else {
    await expect(page.getByTestId('sidebar-menu-ai')).toHaveCount(0)
  }
})
