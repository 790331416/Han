import { test, expect } from '../fixtures/test'

test.describe('OSS 配置页面', () => {
  test('medium 环境应展示 OSS 配置入口并加载页面', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/system/oss-config')
    await authenticatedPage.waitForURL('**/system/oss-config')
    await authenticatedPage.waitForLoadState('networkidle')

    await expect(authenticatedPage.getByTestId('oss-config-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('oss-config-table')).toBeVisible()
    await expect(authenticatedPage.getByTestId('oss-config-add-button')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-ossconfig')).toBeVisible()
  })
})
