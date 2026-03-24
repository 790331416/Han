import { test, expect } from '../fixtures/test'

/**
 * 验证中配环境下，侧边栏会按后端运行时能力过滤入口。
 */
test('sidebar should respect runtime capabilities on medium tier', async ({ authenticatedPage }) => {
  const page = authenticatedPage

  await expect(page.getByTestId('sidebar-menu-job')).toBeVisible()
  await expect(page.getByTestId('sidebar-menu-openapp')).toBeVisible()

  const systemMenuTitle = page.locator('[data-testid="sidebar-menu-system"] .el-sub-menu__title')
  await expect(systemMenuTitle).toBeVisible()
  await systemMenuTitle.click()

  await expect(page.getByTestId('sidebar-menu-tenant')).toBeVisible()
  await expect(page.getByTestId('sidebar-menu-tenantpackage')).toBeVisible()
  await expect(page.getByTestId('sidebar-menu-tenantquota')).toBeVisible()
  await expect(page.getByTestId('sidebar-menu-ossconfig')).toBeVisible()

  await expect(page.getByTestId('sidebar-menu-workflow')).toHaveCount(0)
  await expect(page.getByTestId('sidebar-menu-ai')).toHaveCount(0)
})
