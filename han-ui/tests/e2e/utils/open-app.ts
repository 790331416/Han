import type { APIRequestContext } from '@playwright/test'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface PageResult<T> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface OpenAppRecord {
  appId: string | number
  appName: string
  appKey: string
  appType?: string
  status: number
  contactName?: string
}

function buildHeaders(accessToken: string): Record<string, string> {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
}

export async function fetchOpenApps(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  appName?: string
): Promise<OpenAppRecord[]> {
  const response = await request.get(`${apiBaseUrl}/open/app/list`, {
    headers: buildHeaders(accessToken),
    params: {
      pageNum: 1,
      pageSize: 100,
      appName
    }
  })
  const result = (await response.json()) as ApiEnvelope<PageResult<OpenAppRecord>>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`Failed to fetch open app list: ${JSON.stringify(result)}`)
  }
  return result.data?.rows || []
}

export async function findOpenAppByName(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  appName: string
): Promise<OpenAppRecord | null> {
  const records = await fetchOpenApps(request, apiBaseUrl, accessToken, appName)
  return records.find((record) => record.appName === appName) || null
}

export async function cleanupOpenAppsByPrefix(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  prefix: string
): Promise<void> {
  const records = await fetchOpenApps(request, apiBaseUrl, accessToken)
  const targets = records.filter((record) => record.appName.startsWith(prefix))
  for (const record of targets) {
    await deleteOpenApp(request, apiBaseUrl, accessToken, record.appId)
  }
}

export async function deleteOpenApp(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  appId: string | number
): Promise<void> {
  const result = await postWithRetry(
    request,
    `${apiBaseUrl}/open/app/remove/${appId}`,
    accessToken
  )
  if (result.code !== 200) {
    throw new Error(`Failed to delete open app: ${JSON.stringify(result)}`)
  }
}

async function postWithRetry(
  request: APIRequestContext,
  url: string,
  accessToken: string
): Promise<ApiEnvelope<unknown>> {
  const result = await postJson(request, url, accessToken)
  if (result.code === 200) {
    return result
  }

  if (isRepeatSubmit(result)) {
    await sleep(5200)
    return postJson(request, url, accessToken)
  }

  return result
}

async function postJson(
  request: APIRequestContext,
  url: string,
  accessToken: string
): Promise<ApiEnvelope<unknown>> {
  const response = await request.post(url, {
    headers: buildHeaders(accessToken)
  })
  return (await response.json()) as ApiEnvelope<unknown>
}

function isRepeatSubmit(result: ApiEnvelope<unknown> | null | undefined): boolean {
  return typeof result?.msg === 'string' && result.msg.includes('请勿重复提交')
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}
