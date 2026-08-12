import { useUserStore } from '@/stores/user'
import { getToken, removeRefreshToken, removeToken } from '@/utils/auth'
import {
  clearSessionAndRedirectToLogin,
  tryRefreshSession
} from '@/utils/session-refresh'

/**
 * 客户端空闲超时默认阈值：连续这么久没有收到任何 chunk 就判定链路已挂死。
 *
 * <p>后端虽有 `SSE_TIMEOUT`，但上游模型 hang 住而连接未断时前端会永远停在
 * `streaming=true`，因此必须有一层客户端兜底。
 */
export const DEFAULT_AI_STREAM_IDLE_TIMEOUT = 60_000

/**
 * AI 流式链路的统一错误类型。
 *
 * <p>`reported=true` 表示错误已经通过 `onError` 回调交给调用方展示过，
 * 调用方的 catch 分支不应再弹第二条提示。
 */
export class AiStreamError extends Error {
  readonly reported: boolean

  constructor(message: string, reported = false) {
    super(message)
    this.name = 'AiStreamError'
    this.reported = reported
  }
}

/** 判断异常是否已由 `onError` 回调提示过，避免同一次失败弹两条 toast。 */
export function isReportedAiStreamError(error: unknown): boolean {
  return error instanceof AiStreamError && error.reported
}

export interface AiStreamRequestOptions {
  baseUrl: string
  path: string
  token: string
  tenantId?: string | number | null
  body?: unknown
  signal?: AbortSignal
  /** 空闲超时毫秒数，<=0 表示不启用；默认 {@link DEFAULT_AI_STREAM_IDLE_TIMEOUT}。 */
  idleTimeoutMs?: number
  onDelta?: (payload: { chunk: string; fullContent: string }) => void
  onError?: (message: string) => void
  onMeta?: (payload: AiStreamMetaPayload) => void
  onNodeEvent?: (event: AiStreamNodeEvent) => void
}

export interface AiStreamConsumeOptions {
  /** 空闲超时毫秒数，<=0 表示不启用；默认 {@link DEFAULT_AI_STREAM_IDLE_TIMEOUT}。 */
  idleTimeoutMs?: number
  onDelta?: (payload: { chunk: string; fullContent: string }) => void
  onError?: (message: string) => void
  onMeta?: (payload: AiStreamMetaPayload) => void
  onNodeEvent?: (event: AiStreamNodeEvent) => void
}

/**
 * 编排节点级流式事件（advanced 工作流）：
 * node_start 节点开始、node_delta llm 节点逐 token 增量、node_end 节点结束（content 为节点轨迹）。
 */
export interface AiStreamNodeEvent {
  type: 'node_start' | 'node_delta' | 'node_end'
  content: {
    nodeId?: string
    nodeType?: string
    nodeName?: string
    delta?: string
    status?: string
    input?: string
    output?: string
    costMs?: number
    error?: string
    [key: string]: unknown
  }
}

interface ParsedAiStreamEvent {
  done: boolean
  delta?: string
  error?: string
  meta?: AiStreamMetaPayload
  nodeEvent?: AiStreamNodeEvent
}

/**
 * 流式 meta 下发的图片附件。
 *
 * <p>结构与 `AiChatImage` 一致，这里单独声明是为了不让 `utils` 反向依赖 `api` 层。
 */
export interface AiStreamImagePayload {
  fileId?: string | number
  url: string
  name?: string
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
  nodeTraces?: unknown
  /** 对话内文生图/多模态回传的图片附件（后端 `buildStreamMeta` 会下发）。 */
  images?: AiStreamImagePayload[]
  [key: string]: unknown
}

/**
 * 发送 AI 流式请求，并把 SSE 增量片段聚合为最终文本。
 */
export async function requestAiStream(options: AiStreamRequestOptions): Promise<string> {
  const token = getToken() || options.token
  if (!token) {
    clearSessionAndRedirectToLogin()
    throw new AiStreamError('登录状态已过期，请重新登录')
  }

  const response = await sendAiStreamRequest(options, token)

  if (response.status === 401) {
    // 这个响应体不会再被消费，必须显式取消，否则底层连接与缓冲区要等 GC 才回收。
    await discardResponseBody(response)

    const refreshResult = await tryRefreshSession(options.baseUrl)
    if (refreshResult.status === 'success' && refreshResult.accessToken) {
      const retriedResponse = await sendAiStreamRequest(options, refreshResult.accessToken)
      return consumeAiStreamResponse(retriedResponse, options)
    }

    if (refreshResult.status === 'failed') {
      options.onError?.(refreshResult.message)
      throw new AiStreamError(refreshResult.message, true)
    }

    clearSessionAndRedirectToLogin()
    options.onError?.('登录状态已过期，请重新登录')
    throw new AiStreamError('登录状态已过期，请重新登录', true)
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
    await discardResponseBody(response)
    if (response.status === 401) {
      handleUnauthorizedStream()
      options.onError?.('登录状态已过期，请重新登录')
      throw new AiStreamError('登录状态已过期，请重新登录', true)
    }
    throw new AiStreamError(`请求失败: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new AiStreamError('无法获取响应流')
  }

  const idleTimeoutMs = options.idleTimeoutMs ?? DEFAULT_AI_STREAM_IDLE_TIMEOUT
  const decoder = new TextDecoder()
  let pending = ''
  let fullContent = ''

  // 无论是 [DONE]、error 事件还是抛错早退，都要在 finally 里释放响应体流，
  // 否则 body stream 一直处于 locked 且未消费状态，底层 HTTP 连接不会回收。
  try {
    while (true) {
      const { done, value } = await readWithIdleTimeout(reader, idleTimeoutMs)
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
          return fullContent
        }
        if (event.error) {
          options.onError?.(event.error)
          throw new AiStreamError(event.error, true)
        }
        if (event.meta) {
          options.onMeta?.(event.meta)
          continue
        }
        if (event.nodeEvent) {
          options.onNodeEvent?.(event.nodeEvent)
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
            options.onError?.(tailEvent.error)
            throw new AiStreamError(tailEvent.error, true)
          }
          if (tailEvent?.meta) {
            options.onMeta?.(tailEvent.meta)
          }
          if (tailEvent?.nodeEvent) {
            options.onNodeEvent?.(tailEvent.nodeEvent)
          }
          if (tailEvent?.delta) {
            fullContent += tailEvent.delta
            options.onDelta?.({ chunk: tailEvent.delta, fullContent })
          }
        }
        return fullContent
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined)
  }
}

/**
 * 带空闲超时的读取：超过阈值仍未收到任何 chunk 就判定链路挂死。
 *
 * <p>超时后由外层 `finally` 的 `reader.cancel()` 结束底层流，
 * 竞态里那个未完成的 `read()` 也会随之 settle。
 */
interface ChunkReader {
  read(): Promise<{ done: boolean; value?: Uint8Array }>
}

async function readWithIdleTimeout(
  reader: ChunkReader,
  idleTimeoutMs: number
): Promise<{ done: boolean; value?: Uint8Array }> {
  if (idleTimeoutMs <= 0) {
    return reader.read()
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([
      reader.read(),
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => {
          reject(new AiStreamError(`AI 响应超时：${Math.round(idleTimeoutMs / 1000)} 秒内未收到任何数据`))
        }, idleTimeoutMs)
      })
    ])
  } finally {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
  }
}

/** 丢弃不再消费的响应体，避免连接与缓冲区悬挂。 */
async function discardResponseBody(response: Response): Promise<void> {
  try {
    await response.body?.cancel()
  } catch {
    // 响应体可能已被消费或已关闭，忽略即可
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
    if (parsed.type === 'node_start' || parsed.type === 'node_delta' || parsed.type === 'node_end') {
      return {
        done: false,
        nodeEvent: {
          type: parsed.type,
          content: ((parsed as { content?: AiStreamNodeEvent['content'] }).content || {})
        }
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
