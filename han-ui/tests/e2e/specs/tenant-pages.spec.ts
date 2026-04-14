import type { APIRequestContext } from '@playwright/test'
import { test, expect, e2eRuntime } from '../fixtures/test'
import {
  fetchTenantList,
  fetchTenantPackages,
  fetchTenantQuota,
  fetchValidTenants,
  type TenantPackageRecord,
  type TenantQuotaRecord,
  type TenantRecord
} from '../utils/tenant'

function pickTenantForList(records: TenantRecord[]): TenantRecord {
  return records.find((record) => record.packageName) || records[0]
}

function pickTenantPackage(records: TenantPackageRecord[]): TenantPackageRecord {
  return records.find((record) => record.packageName) || records[0]
}

async function pickTenantWithQuota(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string
): Promise<{ tenant: TenantRecord; quota: TenantQuotaRecord }> {
  const tenants = await fetchValidTenants(request, apiBaseUrl, accessToken)
  if (tenants.length === 0) {
    throw new Error('No valid tenants available for quota validation')
  }

  for (const tenant of tenants) {
    try {
      const quota = await fetchTenantQuota(request, apiBaseUrl, accessToken, tenant.tenantId)
      return { tenant, quota }
    } catch {
      // Continue until a readable quota is found.
    }
  }

  throw new Error('No tenant quota endpoint returned readable data')
}

test.describe('Tenant pages', () => {
  test('tenant list page should render real tenant data', async ({ authenticatedPage, request, authSession }) => {
    const tenants = await fetchTenantList(request, e2eRuntime.apiBaseUrl, authSession.accessToken)
    expect(tenants.length).toBeGreaterThan(0)

    const targetTenant = pickTenantForList(tenants)

    await authenticatedPage.goto('/system/tenant')
    await authenticatedPage.waitForURL('**/system/tenant')
    await authenticatedPage.waitForLoadState('networkidle')

    await expect(authenticatedPage.getByTestId('tenant-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-table')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-add-button')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-tenant')).toBeVisible()

    const tenantRow = authenticatedPage.locator('.el-table__row').filter({ hasText: String(targetTenant.tenantName) }).first()
    await expect(tenantRow).toBeVisible()
    if (targetTenant.packageName) {
      await expect(tenantRow).toContainText(String(targetTenant.packageName))
    }

    await expect(authenticatedPage.getByTestId(`tenant-edit-button-${targetTenant.tenantId}`)).toBeVisible()
    await expect(authenticatedPage.getByTestId(`tenant-reset-pwd-button-${targetTenant.tenantId}`)).toBeVisible()
    await expect(authenticatedPage.getByTestId(`tenant-delete-button-${targetTenant.tenantId}`)).toBeVisible()
  })

  test('tenant package page should render real package data and open menu dialog', async ({ authenticatedPage, request, authSession }) => {
    const packages = await fetchTenantPackages(request, e2eRuntime.apiBaseUrl, authSession.accessToken)
    expect(packages.length).toBeGreaterThan(0)

    const targetPackage = pickTenantPackage(packages)

    await authenticatedPage.goto('/system/tenant-package')
    await authenticatedPage.waitForURL('**/system/tenant-package')
    await authenticatedPage.waitForLoadState('networkidle')

    await expect(authenticatedPage.getByTestId('tenant-package-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-package-table')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-package-add-button')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-tenantpackage')).toBeVisible()

    const packageRow = authenticatedPage.locator('.el-table__row').filter({ hasText: String(targetPackage.packageName) }).first()
    await expect(packageRow).toBeVisible()
    await expect(packageRow).toContainText(String(targetPackage.tenantCount))

    await expect(authenticatedPage.getByTestId(`tenant-package-edit-button-${targetPackage.packageId}`)).toBeVisible()
    const menuButton = authenticatedPage.getByTestId(`tenant-package-menu-button-${targetPackage.packageId}`)
    await expect(menuButton).toBeVisible()
    await menuButton.click()
    await expect(authenticatedPage.getByTestId('tenant-package-menu-dialog')).toBeVisible()
    await authenticatedPage.keyboard.press('Escape')
    await expect(authenticatedPage.getByTestId('tenant-package-menu-dialog')).toBeHidden()
  })

  test('tenant quota page should render quota cards for a real tenant', async ({ authenticatedPage, request, authSession }) => {
    const { tenant, quota } = await pickTenantWithQuota(request, e2eRuntime.apiBaseUrl, authSession.accessToken)

    await authenticatedPage.goto('/system/tenant-quota')
    await authenticatedPage.waitForURL('**/system/tenant-quota')
    await authenticatedPage.waitForLoadState('networkidle')

    await expect(authenticatedPage.getByTestId('tenant-quota-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-tenantquota')).toBeVisible()

    const quotaSelect = authenticatedPage.getByTestId('tenant-quota-select')
    await expect(quotaSelect).toBeVisible()
    await quotaSelect.click()
    await authenticatedPage.locator('.el-select-dropdown__item').filter({ hasText: String(tenant.tenantName) }).first().click()

    await expect(authenticatedPage.getByTestId('tenant-quota-user-card')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-quota-storage-card')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-quota-api-card')).toBeVisible()
    await expect(authenticatedPage.getByTestId('tenant-quota-save-button')).toBeVisible()

    if (quota.userUsed !== undefined) {
      await expect(authenticatedPage.getByTestId('tenant-quota-user-card')).toContainText(String(quota.userUsed))
    }
    if (quota.apiUsed !== undefined) {
      await expect(authenticatedPage.getByTestId('tenant-quota-api-card')).toContainText(String(quota.apiUsed))
    }
  })
})
