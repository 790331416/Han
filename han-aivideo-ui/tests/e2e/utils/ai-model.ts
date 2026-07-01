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

export interface AiModelRecord {
  modelId: string | number
  modelName: string
  modelType: string
  provider: string
  modelCode: string
  baseUrl: string
  apiKey?: string
  credentialConfigured?: boolean
  credentialSource?: string
  maxTokens: number
  temperature: number
  status: string
  remark?: string
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
    throw new Error(`AI 模型测试请求失败: ${JSON.stringify(result)}`)
  }
  return result
}

export async function createAiModel(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  payload: Partial<AiModelRecord>
): Promise<AiModelRecord> {
  const response = await request.post(`${apiBaseUrl}/ai/model`, {
    headers: buildHeaders(accessToken),
    data: {
      modelName: payload.modelName,
      modelType: payload.modelType || 'LLM',
      provider: payload.provider || 'qwen',
      modelCode: payload.modelCode || 'qwen-plus',
      baseUrl: payload.baseUrl || 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      apiKey: payload.apiKey || '',
      maxTokens: payload.maxTokens || 1024,
      temperature: payload.temperature ?? 0.7,
      status: payload.status || '0',
      remark: payload.remark || ''
    }
  })
  await ensureSuccess<void>(response)
  const created = await findAiModelByName(request, apiBaseUrl, accessToken, String(payload.modelName))
  if (!created) {
    throw new Error(`未找到新建模型: ${payload.modelName}`)
  }
  return created
}

export async function findAiModelByName(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  modelName: string
): Promise<AiModelRecord | null> {
  const models = await listAiModels(request, apiBaseUrl, accessToken, modelName)
  return models.find((item) => item.modelName === modelName) || null
}

export async function listAiModels(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  modelName = ''
): Promise<AiModelRecord[]> {
  const response = await request.get(`${apiBaseUrl}/ai/model/list?pageNum=1&pageSize=100&modelName=${encodeURIComponent(modelName)}`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<AiModelRecord>>(response)
  return result.data?.rows || []
}

export async function deleteAiModelById(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  modelId: string | number
): Promise<void> {
  const response = await request.post(`${apiBaseUrl}/ai/model/remove/${modelId}`, {
    headers: buildHeaders(accessToken)
  })
  await ensureSuccess<void>(response)
}

export async function cleanupAiModelsByPrefix(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  prefix: string
): Promise<void> {
  const response = await request.get(`${apiBaseUrl}/ai/model/list?pageNum=1&pageSize=100&modelName=${encodeURIComponent(prefix)}`, {
    headers: buildHeaders(accessToken)
  })
  const result = await ensureSuccess<PageResult<AiModelRecord>>(response)
  const matches = (result.data?.rows || []).filter((item) => item.modelName.startsWith(prefix))
  for (const item of matches) {
    await deleteAiModelById(request, apiBaseUrl, accessToken, item.modelId)
  }
}
