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
 * Send an AI streaming request and aggregate SSE chunks into the final content.
 */
export async function requestAiStream(options: AiStreamRequestOptions): Promise<string> {
  const response = await fetch(resolveUrl(options.baseUrl, options.path), {
    method: 'POST',
    headers: buildHeaders(options.token, options.tenantId, options.body !== undefined),
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal
  })

  return consumeAiStreamResponse(response, options)
}

/**
 * Consume an existing AI SSE response.
 */
export async function consumeAiStreamResponse(response: Response, options: AiStreamConsumeOptions = {}): Promise<string> {
  if (!response.ok) {
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

function normalizeLineBreaks(content: string): string {
  return content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}
