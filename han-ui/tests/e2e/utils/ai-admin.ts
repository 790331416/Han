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

export interface KnowledgeBaseRecord {
  kbId: string | number
  kbName: string
  kbType: string
  description?: string
  status?: string
  documentCount?: number
  paragraphCount?: number
}

export interface KbDocumentRecord {
  docId: string | number
  kbId: string | number
  docName: string
  indexStatus: string
  indexError?: string
}

export interface McpServerRecord {
  mcpId: string | number
  serverName: string
  transportType: string
  command?: string
  args?: string
  envVars?: string
  url?: string
  tools?: string
  status?: string
}

export interface PromptTemplateRecord {
  templateId: string | number
  templateName: string
  category: string
  content: string
  variables?: string
  description?: string
  builtIn?: number
  status?: string
}

function buildHeaders(accessToken: string): Record<string, string> {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
}

async function ensureSuccess<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<ApiEnvelope<T>> {
  const result = (await response.json()) as ApiEnvelope<T>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`AI 管理测试请求失败: ${JSON.stringify(result)}`)
  }
  return result
}

export async function createKnowledgeBase(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  payload: Partial<KnowledgeBaseRecord>
): Promise<KnowledgeBaseRecord> {
  const response = await request.post(`${apiBaseUrl}/ai/kb`, {
    headers: buildHeaders(accessToken),
    data: {
      kbName: payload.kbName,
      kbType: payload.kbType || 'general',
      description: payload.description || '',
      status: payload.status || '0'
    }
  })
  await ensureSuccess<void>(response)
  const created = await findKnowledgeBaseByName(request, apiBaseUrl, accessToken, String(payload.kbName))
  if (!created) {
    throw new Error(`未找到新建知识库: ${payload.kbName}`)
  }
  return created
}

export async function findKnowledgeBaseByName(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  kbName: string
): Promise<KnowledgeBaseRecord | null> {
  const response = await request.get(`${apiBaseUrl}/ai/kb/list?pageNum=1&pageSize=100&kbName=${encodeURIComponent(kbName)}`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<KnowledgeBaseRecord>>(response)
  return result.data?.rows?.find((item) => item.kbName === kbName) || null
}

export async function fetchKnowledgeDocuments(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  kbId: string | number
): Promise<KbDocumentRecord[]> {
  const response = await request.get(`${apiBaseUrl}/ai/kb/${kbId}/document/list?pageNum=1&pageSize=100`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<KbDocumentRecord>>(response)
  return result.data?.rows || []
}

export async function deleteKnowledgeBase(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  kbId: string | number
): Promise<void> {
  const response = await request.post(`${apiBaseUrl}/ai/kb/remove/${kbId}`, {
    headers: buildHeaders(accessToken)
  })
  await ensureSuccess<void>(response)
}

export async function createPromptTemplate(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  payload: Partial<PromptTemplateRecord>
): Promise<PromptTemplateRecord> {
  const response = await request.post(`${apiBaseUrl}/ai/prompt`, {
    headers: buildHeaders(accessToken),
    data: {
      templateName: payload.templateName,
      category: payload.category || 'user',
      content: payload.content,
      variables: payload.variables || '',
      description: '',
      status: payload.status || '0'
    }
  })
  await ensureSuccess<void>(response)
  const created = await findPromptTemplateByName(request, apiBaseUrl, accessToken, String(payload.templateName))
  if (!created) {
    throw new Error(`未找到新建 Prompt 模板: ${payload.templateName}`)
  }
  return created
}

export async function findPromptTemplateByName(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  templateName: string
): Promise<PromptTemplateRecord | null> {
  const response = await request.get(`${apiBaseUrl}/ai/prompt/list?pageNum=1&pageSize=100&templateName=${encodeURIComponent(templateName)}`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<PromptTemplateRecord>>(response)
  return result.data?.rows?.find((item) => item.templateName === templateName) || null
}

export async function deletePromptTemplate(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  templateId: string | number
): Promise<void> {
  const response = await request.post(`${apiBaseUrl}/ai/prompt/remove/${templateId}`, {
    headers: buildHeaders(accessToken)
  })
  await ensureSuccess<void>(response)
}

export async function createMcpServer(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  payload: Partial<McpServerRecord>
): Promise<McpServerRecord> {
  const transportType = payload.transportType || 'sse'
  const response = await request.post(`${apiBaseUrl}/ai/mcp`, {
    headers: buildHeaders(accessToken),
    data: {
      serverName: payload.serverName,
      description: '',
      transportType,
      command: transportType === 'stdio' ? (payload.command || 'npx') : '',
      args: transportType === 'stdio' ? (payload.args || '[]') : '[]',
      envVars: transportType === 'stdio' ? (payload.envVars || '{}') : '{}',
      url: transportType === 'stdio' ? '' : (payload.url || 'http://127.0.0.1:65535/sse'),
      status: payload.status || '0'
    }
  })
  await ensureSuccess<void>(response)
  const created = await findMcpServerByName(request, apiBaseUrl, accessToken, String(payload.serverName))
  if (!created) {
    throw new Error(`未找到新建 MCP 服务: ${payload.serverName}`)
  }
  return created
}

export async function findMcpServerByName(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  serverName: string
): Promise<McpServerRecord | null> {
  const response = await request.get(`${apiBaseUrl}/ai/mcp/list?pageNum=1&pageSize=100&serverName=${encodeURIComponent(serverName)}`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<McpServerRecord>>(response)
  return result.data?.rows?.find((item) => item.serverName === serverName) || null
}

export async function deleteMcpServer(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  mcpId: string | number
): Promise<void> {
  const response = await request.post(`${apiBaseUrl}/ai/mcp/remove/${mcpId}`, {
    headers: buildHeaders(accessToken)
  })
  await ensureSuccess<void>(response)
}
