import type { Page } from '@playwright/test'
import { test, expect, e2eRuntime } from '../fixtures/test'
import { cleanupOpenAppsByPrefix, findOpenAppByName } from '../utils/open-app'

const OPEN_APP_E2E_PREFIX = 'E2E Open App'

interface ApiEnvelope {
  code: number
  msg: string
}

test.describe('开放平台应用管理', () => {
  test('支持真实列表与应用完整生命周期', async ({ authenticatedPage, request, authSession }) => {
    const uniqueSuffix = Date.now()
    const appName = `${OPEN_APP_E2E_PREFIX} ${uniqueSuffix}`
    const updatedAppName = `${appName} Updated`
    const contactName = `E2E Contact ${uniqueSuffix}`
    const updatedContactName = `${contactName} Updated`

    await cleanupOpenAppsByPrefix(request, e2eRuntime.apiBaseUrl, authSession.accessToken, OPEN_APP_E2E_PREFIX)

    try {
      await authenticatedPage.goto('/open/app')
      await authenticatedPage.waitForURL('**/open/app')
      await authenticatedPage.waitForLoadState('networkidle')

      await expect(authenticatedPage.getByTestId('open-app-page')).toBeVisible()
      await expect(authenticatedPage.getByTestId('open-app-table')).toBeVisible()
      await expect(authenticatedPage.getByTestId('open-app-add-button')).toBeVisible()
      await expect(authenticatedPage.getByTestId('sidebar-menu-openapp')).toBeVisible()

      await authenticatedPage.getByTestId('open-app-add-button').click()
      const createDialog = authenticatedPage.getByRole('dialog', { name: '新增应用' })
      await expect(createDialog).toBeVisible()

      await createDialog.getByRole('textbox', { name: /应用名称/ }).fill(appName)
      await createDialog.getByRole('textbox', { name: '应用描述' }).fill('开放平台 E2E 新增验证')
      await createDialog.getByRole('textbox', { name: '回调地址' }).fill(`https://e2e.example.com/${uniqueSuffix}/callback`)
      await createDialog.getByRole('textbox', { name: '联系人' }).fill(contactName)
      const createResponse = waitForApiEnvelope(authenticatedPage, '/open/app')
      await createDialog.getByRole('button', { name: '确定' }).click()
      await expect(await createResponse).toMatchObject({ code: 200 })

      let createdAppId: string | number | undefined
      await expect.poll(async () => {
        const record = await findOpenAppByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, appName)
        createdAppId = record?.appId
        return record?.appName ?? null
      }).toBe(appName)

      const createdRow = authenticatedPage.locator('.el-table__row').filter({ hasText: appName }).first()
      await expect(createdRow).toBeVisible()
      await expect(createdRow).toContainText(contactName)
      await expect(createdAppId, '创建后必须能拿到应用 ID').toBeTruthy()

      await authenticatedPage.getByTestId(`open-app-edit-button-${createdAppId}`).click()
      const editDialog = authenticatedPage.getByRole('dialog', { name: '编辑应用' })
      await expect(editDialog).toBeVisible()
      await editDialog.getByRole('textbox', { name: /应用名称/ }).fill(updatedAppName)
      await editDialog.getByRole('textbox', { name: '联系人' }).fill(updatedContactName)
      await editDialog.getByRole('textbox', { name: '应用描述' }).fill('开放平台 E2E 编辑验证')
      const editResponse = waitForApiEnvelope(authenticatedPage, '/open/app/edit')
      await editDialog.getByRole('button', { name: '确定' }).click()
      await expect(await editResponse).toMatchObject({ code: 200 })

      await expect.poll(async () => {
        const record = await findOpenAppByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, updatedAppName)
        createdAppId = record?.appId
        return record?.contactName ?? null
      }).toBe(updatedContactName)

      const updatedRow = authenticatedPage.locator('.el-table__row').filter({ hasText: updatedAppName }).first()
      await expect(updatedRow).toBeVisible()
      await expect(updatedRow).toContainText(updatedContactName)

      // 停用会立刻让所有使用该 AppKey 的接入方鉴权失败，页面对此加了二次确认
      await authenticatedPage.getByTestId(`open-app-status-switch-${createdAppId}`).click()
      const disableDialog = authenticatedPage.locator('.el-message-box').last()
      await expect(disableDialog).toContainText('确认停用吗')
      await disableDialog.locator('.el-button--primary').click()
      await expect.poll(async () => {
        const record = await findOpenAppByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, updatedAppName)
        return record?.status ?? null
      }).toBe(1)

      await authenticatedPage.getByTestId(`open-app-reset-secret-button-${createdAppId}`).click()
      const confirmDialog = authenticatedPage.locator('.el-message-box').last()
      await expect(confirmDialog).toContainText('确认重置应用')
      await confirmDialog.locator('.el-button--primary').click()

      // 新密钥改用带复制按钮的独立弹窗展示，不再是只有文案的 alert
      const secretDialog = authenticatedPage.getByTestId('open-app-secret-dialog')
      await expect(secretDialog).toBeVisible()
      await expect(secretDialog).toContainText('新密钥仅本次可见')
      // el-input 的 inheritAttrs 为 false，data-testid 直接落在内层 <input> 上，不能再往下找 input
      const secretValue = await authenticatedPage.getByTestId('open-app-secret-value').inputValue()
      expect(secretValue).toMatch(/\S+/)
      await authenticatedPage.getByTestId('open-app-secret-close').click()

      await authenticatedPage.getByTestId(`open-app-delete-button-${createdAppId}`).click()
      const deleteDialog = authenticatedPage.locator('.el-message-box').last()
      await expect(deleteDialog).toContainText('确认删除应用')
      await deleteDialog.locator('.el-button--primary').click()

      await expect.poll(async () => {
        const record = await findOpenAppByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, updatedAppName)
        return record
      }).toBeNull()
    } finally {
      await cleanupOpenAppsByPrefix(request, e2eRuntime.apiBaseUrl, authSession.accessToken, OPEN_APP_E2E_PREFIX)
    }
  })
})

async function waitForApiEnvelope(
  page: Page,
  urlFragment: string
): Promise<ApiEnvelope> {
  const response = await page.waitForResponse((currentResponse) => {
    return currentResponse.request().method() === 'POST' && currentResponse.url().includes(urlFragment)
  })
  return response.json() as Promise<ApiEnvelope>
}
