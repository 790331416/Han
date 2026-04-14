import type { APIRequestContext } from '@playwright/test'
import { test, expect, e2eRuntime } from '../fixtures/test'

interface ApiEnvelope<T> {
  code: number
  data: T
  msg: string
}

interface PageResult<T> {
  rows: T[]
  total: number
}

interface GenTableRecord {
  id: number
  tableName: string
  tableComment: string
}

interface DbTableInfo {
  tableName: string
  tableComment: string
}

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`
  }
}

async function listGenTables(accessToken: string, request: APIRequestContext, tableName: string) {
  const response = await request.get(`${e2eRuntime.apiBaseUrl}/gen/list`, {
    headers: authHeaders(accessToken),
    params: {
      pageNum: 1,
      pageSize: 20,
      tableName
    }
  })
  const json = await response.json() as ApiEnvelope<PageResult<GenTableRecord>>
  expect(response.ok()).toBeTruthy()
  expect(json.code).toBe(200)
  return json.data?.rows ?? []
}

async function cleanupGenTables(accessToken: string, request: APIRequestContext, tableName: string) {
  const rows = await listGenTables(accessToken, request, tableName)
  for (const row of rows) {
    const deleteResponse = await request.post(`${e2eRuntime.apiBaseUrl}/gen/remove/${row.id}`, {
      headers: authHeaders(accessToken)
    })
    const deleteJson = await deleteResponse.json() as ApiEnvelope<null>
    expect(deleteResponse.ok()).toBeTruthy()
    expect(deleteJson.code).toBe(200)
  }
}

test('gen module should support import, preview, download and cleanup', async ({
  authenticatedPage,
  request,
  authSession
}) => {
  const accessToken = authSession.accessToken
  const tableName = 'sys_notice'
  let importedTableId: number | undefined

  await cleanupGenTables(accessToken, request, tableName)

  try {
    await authenticatedPage.goto('/tool/gen')
    await authenticatedPage.waitForURL('**/tool/gen')
    await expect(authenticatedPage.getByTestId('gen-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('gen-table')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-toolgen')).toBeVisible()

    const dbListResponse = await request.get(`${e2eRuntime.apiBaseUrl}/gen/db/list`, {
      headers: authHeaders(accessToken),
      params: { tableName }
    })
    const dbListJson = await dbListResponse.json() as ApiEnvelope<DbTableInfo[]>
    expect(dbListResponse.ok()).toBeTruthy()
    expect(dbListJson.code).toBe(200)
    expect(dbListJson.data.some((item) => item.tableName === tableName)).toBeTruthy()

    const importResponse = await request.post(`${e2eRuntime.apiBaseUrl}/gen/importTable`, {
      headers: {
        ...authHeaders(accessToken),
        'Content-Type': 'application/json'
      },
      data: [tableName]
    })
    const importJson = await importResponse.json() as ApiEnvelope<null>
    expect(importResponse.ok()).toBeTruthy()
    expect(importJson.code).toBe(200)

    await expect.poll(async () => {
      const rows = await listGenTables(accessToken, request, tableName)
      importedTableId = rows[0]?.id
      return rows[0]?.tableName ?? null
    }).toBe(tableName)

    const previewResponse = await request.get(`${e2eRuntime.apiBaseUrl}/gen/preview/${importedTableId}`, {
      headers: authHeaders(accessToken)
    })
    const previewJson = await previewResponse.json() as ApiEnvelope<Record<string, string>>
    expect(previewResponse.ok()).toBeTruthy()
    expect(previewJson.code).toBe(200)
    const previewEntries = Object.entries(previewJson.data || {})
    expect(previewEntries.length).toBeGreaterThan(0)
    expect(
      previewEntries.some(
        ([fileName]) => fileName.includes('/controller/admin/') && fileName.endsWith('Controller.java')
      )
    ).toBeTruthy()

    const downloadResponse = await request.get(`${e2eRuntime.apiBaseUrl}/gen/download/${importedTableId}`, {
      headers: authHeaders(accessToken)
    })
    expect(downloadResponse.ok()).toBeTruthy()
    expect(downloadResponse.headers()['content-type'] || '').toContain('application/octet-stream')
    const downloadBuffer = await downloadResponse.body()
    expect(downloadBuffer.byteLength).toBeGreaterThan(128)
  } finally {
    if (importedTableId) {
      await cleanupGenTables(accessToken, request, tableName)
    }
  }
})
