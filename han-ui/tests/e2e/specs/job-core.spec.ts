import { test, expect, e2eRuntime } from '../fixtures/test'
import type { APIRequestContext } from '@playwright/test'

interface ApiEnvelope<T> {
  code: number
  data: T
  msg: string
}

interface PageResult<T> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
}

interface JobRecord {
  jobId: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  status: string
  remark?: string
}

interface JobLogRecord {
  jobLogId: number
  jobName: string
  invokeTarget: string
  status: string
  jobMessage?: string
}

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`
  }
}

async function listJobs(request: APIRequestContext, accessToken: string, jobName?: string) {
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

async function listJobLogs(request: APIRequestContext, accessToken: string, jobName?: string) {
  const response = await request.get(`${e2eRuntime.apiBaseUrl}/job/log/list`, {
    headers: authHeaders(accessToken),
    params: {
      pageNum: 1,
      pageSize: 20,
      jobName
    }
  })
  const json = await response.json() as ApiEnvelope<PageResult<JobLogRecord>>
  expect(response.ok()).toBeTruthy()
  expect(json.code).toBe(200)
  return json.data?.rows ?? []
}

async function waitFor<T>(
  load: () => Promise<T>,
  predicate: (value: T) => boolean,
  timeoutMs = 10_000,
  intervalMs = 500
): Promise<T> {
  const start = Date.now()
  let lastValue = await load()
  while (!predicate(lastValue)) {
    if (Date.now() - start >= timeoutMs) {
      throw new Error(`等待条件超时: ${JSON.stringify(lastValue)}`)
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
    lastValue = await load()
  }
  return lastValue
}

test('job module should expose handlers, cron validation, task list, and log list', async ({ request, authSession }) => {
  const handlersResponse = await request.get(`${e2eRuntime.apiBaseUrl}/job/handlers`, {
    headers: authHeaders(authSession.accessToken)
  })
  const handlersJson = await handlersResponse.json() as ApiEnvelope<unknown[]>
  expect(handlersResponse.ok()).toBeTruthy()
  expect(handlersJson.code).toBe(200)
  expect(Array.isArray(handlersJson.data)).toBeTruthy()

  const validCronResponse = await request.get(`${e2eRuntime.apiBaseUrl}/job/checkCron`, {
    headers: authHeaders(authSession.accessToken),
    params: { cronExpression: '0/5 * * * * ?' }
  })
  const validCronJson = await validCronResponse.json() as ApiEnvelope<boolean>
  expect(validCronResponse.ok()).toBeTruthy()
  expect(validCronJson.code).toBe(200)
  expect(validCronJson.data).toBe(true)

  const invalidCronResponse = await request.get(`${e2eRuntime.apiBaseUrl}/job/checkCron`, {
    headers: authHeaders(authSession.accessToken),
    params: { cronExpression: 'invalid-cron' }
  })
  const invalidCronJson = await invalidCronResponse.json() as ApiEnvelope<boolean>
  expect(invalidCronResponse.ok()).toBeTruthy()
  expect(invalidCronJson.code).toBe(200)
  expect(invalidCronJson.data).toBe(false)

  const jobListResponse = await request.get(`${e2eRuntime.apiBaseUrl}/job/list`, {
    headers: authHeaders(authSession.accessToken),
    params: { pageNum: 1, pageSize: 10 }
  })
  const jobListJson = await jobListResponse.json() as ApiEnvelope<PageResult<unknown>>
  expect(jobListResponse.ok()).toBeTruthy()
  expect(jobListJson.code).toBe(200)
  expect(Array.isArray(jobListJson.data?.rows)).toBeTruthy()

  const jobLogListResponse = await request.get(`${e2eRuntime.apiBaseUrl}/job/log/list`, {
    headers: authHeaders(authSession.accessToken),
    params: { pageNum: 1, pageSize: 10 }
  })
  const jobLogListJson = await jobLogListResponse.json() as ApiEnvelope<PageResult<unknown>>
  expect(jobLogListResponse.ok()).toBeTruthy()
  expect(jobLogListJson.code).toBe(200)
  expect(Array.isArray(jobLogListJson.data?.rows)).toBeTruthy()
})

test('job module should support sample task lifecycle via APIs', async ({ request, authSession }) => {
  const accessToken = authSession.accessToken
  const jobName = `E2E Sample Task ${Date.now()}`
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
        remark: 'Playwright lifecycle verification'
      }
    })
    const createJson = await createResponse.json() as ApiEnvelope<null>
    expect(createResponse.ok()).toBeTruthy()
    expect(createJson.code).toBe(200)

    const jobsAfterCreate = await waitFor(
      () => listJobs(request, accessToken, jobName),
      (rows) => rows.length > 0
    )
    const createdJob = jobsAfterCreate.find((item) => item.jobName === jobName)
    expect(createdJob).toBeTruthy()
    expect(createdJob?.invokeTarget).toBe('sampleTask.execute')
    jobId = createdJob?.jobId
    expect(jobId).toBeTruthy()

    const pauseResponse = await request.post(`${e2eRuntime.apiBaseUrl}/job/changeStatus`, {
      headers: authHeaders(accessToken),
      params: {
        jobId,
        status: '1'
      }
    })
    const pauseJson = await pauseResponse.json() as ApiEnvelope<null>
    expect(pauseResponse.ok()).toBeTruthy()
    expect(pauseJson.code).toBe(200)

    const pausedJobs = await waitFor(
      () => listJobs(request, accessToken, jobName),
      (rows) => rows.some((item) => item.jobId === jobId && item.status === '1')
    )
    expect(pausedJobs.find((item) => item.jobId === jobId)?.status).toBe('1')

    const resumeResponse = await request.post(`${e2eRuntime.apiBaseUrl}/job/changeStatus`, {
      headers: authHeaders(accessToken),
      params: {
        jobId,
        status: '0'
      }
    })
    const resumeJson = await resumeResponse.json() as ApiEnvelope<null>
    expect(resumeResponse.ok()).toBeTruthy()
    expect(resumeJson.code).toBe(200)

    const resumeJobs = await waitFor(
      () => listJobs(request, accessToken, jobName),
      (rows) => rows.some((item) => item.jobId === jobId && item.status === '0')
    )
    expect(resumeJobs.find((item) => item.jobId === jobId)?.status).toBe('0')

    const runResponse = await request.post(`${e2eRuntime.apiBaseUrl}/job/run/${jobId}`, {
      headers: authHeaders(accessToken)
    })
    const runJson = await runResponse.json() as ApiEnvelope<null>
    expect(runResponse.ok()).toBeTruthy()
    expect(runJson.code).toBe(200)

    const logs = await waitFor(
      () => listJobLogs(request, accessToken, jobName),
      (rows) => rows.some((item) => item.jobName === jobName)
    )
    const latestLog = logs.find((item) => item.jobName === jobName)
    expect(latestLog).toBeTruthy()
    expect(latestLog?.status).toBe('0')
    expect(latestLog?.jobMessage || '').toContain('执行成功')
  } finally {
    const logs = await listJobLogs(request, accessToken, jobName)
    for (const log of logs.filter((item) => item.jobName === jobName)) {
      await request.post(`${e2eRuntime.apiBaseUrl}/job/log/remove/${log.jobLogId}`, {
        headers: authHeaders(accessToken)
      })
    }

    if (jobId) {
      await request.post(`${e2eRuntime.apiBaseUrl}/job/remove/${jobId}`, {
        headers: authHeaders(accessToken)
      })
    }
  }
})
