import type { APIRequestContext } from '@playwright/test'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

/**
 * 清空当前用户未读通知，确保通知中心用例可以重复执行。
 * 旧环境如果仍使用 GET，会在明确返回“不支持的请求方法”时自动回退。
 */
export async function markAllNoticesRead(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string
): Promise<void> {
  const response = await request.post(`${apiBaseUrl}/system/notice/markAllRead`, {
    headers: buildHeaders(accessToken)
  })

  const result = (await response.json()) as ApiEnvelope<unknown>
  if (response.ok() && result.code === 200) {
    return
  }

  if (!isMethodNotAllowed(result)) {
    throw new Error(`清空未读通知失败: ${JSON.stringify(result)}`)
  }

  const fallbackResponse = await request.get(`${apiBaseUrl}/system/notice/markAllRead`, {
    headers: buildHeaders(accessToken)
  })
  const fallbackResult = (await fallbackResponse.json()) as ApiEnvelope<unknown>
  if (!fallbackResponse.ok() || fallbackResult.code !== 200) {
    throw new Error(`清空未读通知失败: ${JSON.stringify(fallbackResult)}`)
  }
}

/**
 * 创建通知公告，供通知中心测试复用。
 * 当服务端触发防重复提交时，等待一个窗口后重试一次。
 */
export async function createNotice(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  title: string
): Promise<void> {
  const payload = {
    noticeTitle: title,
    noticeType: '1',
    noticeContent: `<p>${title}</p>`,
    status: 0
  }

  const result = await postNotice(request, apiBaseUrl, accessToken, payload)
  if (result.code === 200) {
    return
  }

  if (isRepeatSubmit(result)) {
    await sleep(5200)
    const retryResult = await postNotice(request, apiBaseUrl, accessToken, payload)
    if (retryResult.code === 200) {
      return
    }
    throw new Error(`创建通知失败: ${JSON.stringify(retryResult)}`)
  }

  throw new Error(`创建通知失败: ${JSON.stringify(result)}`)
}

function buildHeaders(accessToken: string): Record<string, string> {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
}

function isMethodNotAllowed(result: ApiEnvelope<unknown> | null | undefined): boolean {
  return typeof result?.msg === 'string' && result.msg.includes('不支持的请求方法')
}

function isRepeatSubmit(result: ApiEnvelope<unknown> | null | undefined): boolean {
  return typeof result?.msg === 'string' && result.msg.includes('请勿重复提交')
}

async function postNotice(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  payload: Record<string, string | number>
): Promise<ApiEnvelope<unknown>> {
  const response = await request.post(`${apiBaseUrl}/system/notice/add`, {
    headers: buildHeaders(accessToken),
    data: JSON.stringify(payload)
  })
  return (await response.json()) as ApiEnvelope<unknown>
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}
