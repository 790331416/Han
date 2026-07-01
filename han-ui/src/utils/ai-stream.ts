import { useUserStore } from '@/stores/user'
import { getToken, removeRefreshToken, removeToken } from '@/utils/auth'
import {
  clearSessionAndRedirectToLogin,
  tryRefreshSession
} from '@/utils/session-refresh'

export interface AiStreamRequestOptions {
  baseUrl: string
  path: string
  token: string
  tenantId?: string | number | null
  body?: unknown
  signal?: AbortSignal
  onDelta?: (payload: { chunk: string; fullContent: string }) => void
  onError?: (message: string) => void
  onMeta?: (payload: AiStreamMetaPayload) => void
}

export interface AiStreamConsumeOptions {
  onDelta?: (payload: { chunk: string; fullContent: string }) => void
  onError?: (message: string) => void
  onMeta?: (payload: AiStreamMetaPayload) => void
}

interface ParsedAiStreamEvent {
  done: boolean
  delta?: string
  error?: string
  meta?: AiStreamMetaPayload
}

export interface AiStreamMetaPayload {
  messageId?: string | number
  versionId?: string | number
  taskId?: string | number
  modelId?: string | number
  provider?: string
  modelCode?: string
  tokenCount?: number
  knowledgeSources?: unknown
  toolExecutions?: unknown
  [key: string]: unknown
}

/**
 * 发送 AI 流式请求，并把 SSE 增量片段聚合为最终文本。
 */
export async function requestAiStream(options: AiStreamRequestOptions): Promise<string> {
  const token = getToken() || options.token
  if (!token) {
    clearSessionAndRedirectToLogin()
    throw new Error('登录状态已过期，请重新登录')
  }

  const response = await sendAiStreamRequest(options, token)

  if (response.status === 401) {
    const refreshResult = await tryRefreshSession(options.baseUrl)
    if (refreshResult.status === 'success' && refreshResult.accessToken) {
      const retriedResponse = await sendAiStreamRequest(options, refreshResult.accessToken)
      return consumeAiStreamResponse(retriedResponse, options)
    }

    if (refreshResult.status === 'failed') {
      options.onError?.(refreshResult.message)
      throw new Error(refreshResult.message)
    }

    clearSessionAndRedirectToLogin()
    options.onError?.('登录状态已过期，请重新登录')
    throw new Error('登录状态已过期，请重新登录')
  }

  return consumeAiStreamResponse(response, options)
}

/**
 * 消费已有的 AI SSE 响应。
 *
 * <p>这里兼容模型返回的 delta、meta、error 三类事件，统一向页面输出
 * 增量文本、元数据和错误信息，避免各业务页重复解析 SSE。
 */
export async function consumeAiStreamResponse(response: Response, options: AiStreamConsumeOptions = {}): Promise<string> {
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorizedStream()
      options.onError?.('登录状态已过期，请重新登录')
      throw new Error('登录状态已过期，请重新登录')
    }
    throw new Error(`请求失败: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法获取响应流')
  }

  const decoder = new TextDecoder()
  let pending = ''
  let fullContent = ''
  let streamError = ''

  while (true) {
    const { done, value } = await reader.read()
    pending += decoder.decode(value ?? new Uint8Array(), { stream: !done })
    pending = normalizeLineBreaks(pending)

    const segments = pending.split('\n\n')
    pending = done ? '' : (segments.pop() ?? '')

    for (const segment of segments) {
      const event = parseAiStreamEvent(segment)
      if (!event) {
        continue
      }
      if (event.done) {
        if (streamError) {
          throw new Error(streamError)
        }
        return fullContent
      }
      if (event.error) {
        streamError = event.error
        options.onError?.(event.error)
        throw new Error(event.error)
      }
      if (event.meta) {
        options.onMeta?.(event.meta)
        continue
      }
      if (event.delta) {
        fullContent += event.delta
        options.onDelta?.({ chunk: event.delta, fullContent })
      }
    }

    if (done) {
      if (pending.trim()) {
        const tailEvent = parseAiStreamEvent(pending)
        if (tailEvent?.error) {
          streamError = tailEvent.error
          options.onError?.(tailEvent.error)
          throw new Error(tailEvent.error)
        }
        if (tailEvent?.meta) {
          options.onMeta?.(tailEvent.meta)
        }
        if (tailEvent?.delta) {
          fullContent += tailEvent.delta
          options.onDelta?.({ chunk: tailEvent.delta, fullContent })
        }
      }
      if (streamError) {
        throw new Error(streamError)
      }
      return fullContent
    }
  }
}

/**
 * 流式请求发送入口，统一封装 headers 与 body 结构，便于 401 后按新 token 重试。
 */
async function sendAiStreamRequest(options: AiStreamRequestOptions, token: string): Promise<Response> {
  return fetch(resolveUrl(options.baseUrl, options.path), {
    method: 'POST',
    headers: buildHeaders(token, options.tenantId, options.body !== undefined),
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal
  })
}

function parseAiStreamEvent(segment: string): ParsedAiStreamEvent | null {
  const lines = segment
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  if (lines.length === 0) {
    return null
  }

  const dataLines = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .filter(Boolean)

  if (dataLines.length === 0) {
    return null
  }

  const payload = dataLines.join('\n')
  if (payload === '[DONE]') {
    return { done: true }
  }

  try {
    const parsed = JSON.parse(payload) as { type?: string; content?: string }
    if (parsed.type === 'error') {
      return { done: false, error: parsed.content || 'AI 响应异常' }
    }
    if (parsed.type === 'delta') {
      return { done: false, delta: parsed.content || '' }
    }
    if (parsed.type === 'meta') {
      return {
        done: false,
        meta: (parsed as { content?: AiStreamMetaPayload }).content || (parsed as AiStreamMetaPayload)
      }
    }
  } catch {
    return { done: false, delta: payload }
  }

  return null
}

function resolveUrl(baseUrl: string, path: string): string {
  if (!path) {
    return baseUrl
  }
  if (!baseUrl) {
    return path
  }
  if (baseUrl.endsWith('/')) {
    return `${baseUrl.slice(0, -1)}${path}`
  }
  return `${baseUrl}${path}`
}

function buildHeaders(token: string, tenantId?: string | number | null, includeJson = false): HeadersInit {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    Accept: 'text/event-stream'
  }
  if (includeJson) {
    headers['Content-Type'] = 'application/json'
  }
  if (tenantId !== undefined && tenantId !== null && tenantId !== '') {
    headers['X-Tenant-Id'] = String(tenantId)
  }
  return headers
}

function handleUnauthorizedStream(): void {
  /**
   * 流式接口命中 401 时必须走统一会话清理入口，
   * 否则只删裸 token 不清理持久化 Store，会留下“页面已回登录、但本地会话仍像已登录”的脏状态。
   */
  try {
    const userStore = useUserStore()
    userStore.resetToken()
  } catch {
    removeToken()
    removeRefreshToken()
    window.localStorage.removeItem('HAN-user')
  }

  clearSessionAndRedirectToLogin()
}

function normalizeLineBreaks(content: string): string {
  return content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}
