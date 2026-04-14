import type { Page, APIRequestContext } from '@playwright/test'
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

interface JobRecord {
  jobId: number
  jobName: string
  status: string
}

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`
  }
}

async function listJobs(request: APIRequestContext, accessToken: string, jobName: string) {
  const response = await request.get(`${e2eRuntime.apiBaseUrl}/job/list`, {
    headers: authHeaders(accessToken),
    params: {
      pageNum: 1,
      pageSize: 20,
      jobName
    }
  })
  const json = await response.json() as ApiEnvelope<PageResult<JobRecord>>
  expect(response.ok()).toBeTruthy()
  expect(json.code).toBe(200)
  return json.data?.rows ?? []
}

async function waitForApiEnvelope(
  page: Page,
  urlFragment: string
): Promise<{ envelope: ApiEnvelope<null>, responseUrl: string }> {
  const response = await page.waitForResponse((currentResponse) => {
    return currentResponse.request().method() === 'POST' && currentResponse.url().includes(urlFragment)
  })

  return {
    envelope: await response.json() as ApiEnvelope<null>,
    responseUrl: response.url()
  }
}

test('job page should toggle status with query params contract', async ({
  authenticatedPage,
  request,
  authSession
}) => {
  const accessToken = authSession.accessToken
  const jobName = `任务开关验证-${Date.now()}`
  let jobId: number | undefined

  try {
    const createResponse = await request.post(`${e2eRuntime.apiBaseUrl}/job`, {
      headers: {
        ...authHeaders(accessToken),
        'Content-Type': 'application/json'
      },
      data: {
        jobName,
        jobGroup: 'DEFAULT',
        invokeTarget: 'sampleTask.execute',
        cronExpression: '0 0/30 * * * ?',
        misfirePolicy: '1',
        concurrent: '1',
        status: '0',
        remark: '任务页面开关回归验证'
      }
    })
    const createJson = await createResponse.json() as ApiEnvelope<null>
    expect(createResponse.ok()).toBeTruthy()
    expect(createJson.code).toBe(200)

    await expect.poll(async () => {
      const rows = await listJobs(request, accessToken, jobName)
      jobId = rows[0]?.jobId
      return rows[0]?.jobName ?? null
    }).toBe(jobName)

    await authenticatedPage.goto('/job/list')
    await authenticatedPage.waitForURL('**/job/list')
    await expect(authenticatedPage.locator('.app-container')).toBeVisible()

    await authenticatedPage.getByPlaceholder('请输入任务名称').fill(jobName)
    const searchRequest = authenticatedPage.waitForResponse((response) => {
      return response.request().method() === 'GET' && response.url().includes('/job/list')
    })
    await authenticatedPage.getByRole('button', { name: '搜索' }).click()
    await searchRequest

    const jobRow = authenticatedPage.locator('.el-table__row').filter({ hasText: jobName }).first()
    await expect(jobRow).toBeVisible()

    await jobRow.locator('.el-switch').click()
    const confirmDialog = authenticatedPage.locator('.el-message-box').last()
    await expect(confirmDialog).toContainText(`确定暂停任务"${jobName}"吗?`)

    const statusResponse = waitForApiEnvelope(authenticatedPage, '/job/changeStatus')
    await confirmDialog.locator('.el-button--primary').click()
    const { envelope, responseUrl } = await statusResponse
    expect(envelope.code).toBe(200)
    expect(responseUrl).toContain(`jobId=${jobId}`)
    expect(responseUrl).toContain('status=1')

    await expect.poll(async () => {
      const rows = await listJobs(request, accessToken, jobName)
      return rows[0]?.status ?? null
    }).toBe('1')
  } finally {
    if (jobId) {
      await request.post(`${e2eRuntime.apiBaseUrl}/job/remove/${jobId}`, {
        headers: authHeaders(accessToken)
      })
    }
  }
})
