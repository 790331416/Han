import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// ===================== AI模型 =====================

export interface AiModel {
  modelId: string | number
  modelName: string
  modelType: string
  provider: string
  modelCode: string
  baseUrl: string
  apiKey: string
  credentialConfigured?: boolean
  credentialSource?: string
  maxTokens: number
  temperature: number
  status: string
  remark?: string
  createTime?: string
}

export interface AiModelQuery extends PageQuery {
  modelName?: string
  modelType?: string
  provider?: string
  status?: string
}

export function listAiModel(query: AiModelQuery) {
  return get<PageResult<AiModel>>('/ai/model/list', query)
}

export function getAiModel(modelId: string | number) {
  return get<AiModel>(`/ai/model/${modelId}`)
}

export function addAiModel(data: AiModel) {
  return post<void>('/ai/model', data)
}

export function updateAiModel(data: AiModel) {
  return post<void>('/ai/model/edit', data)
}

export function deleteAiModel(modelId: string | number) {
  return post<void>(`/ai/model/remove/${modelId}`)
}

export function testAiModel(modelId: string | number) {
  return post<string>(`/ai/model/test/${modelId}`)
}

export function listAllModels(modelType?: string) {
  return get<AiModel[]>('/ai/model/all', { modelType })
}

// ===================== 知识库 =====================

export interface KnowledgeBase {
  kbId: string | number
  kbName: string
  description?: string
  kbType: string
  embeddingModelId?: string | number
  documentCount: number
  paragraphCount: number
  charCount: number
  status: string
  createTime?: string
}

export interface KnowledgeBaseQuery extends PageQuery {
  kbName?: string
  kbType?: string
  status?: string
}

export function listKnowledgeBase(query: KnowledgeBaseQuery) {
  return get<PageResult<KnowledgeBase>>('/ai/kb/list', query)
}

export function getKnowledgeBase(kbId: string | number) {
  return get<KnowledgeBase>(`/ai/kb/${kbId}`)
}

export function addKnowledgeBase(data: Partial<KnowledgeBase>) {
  return post<void>('/ai/kb', data)
}

export function updateKnowledgeBase(data: Partial<KnowledgeBase>) {
  return post<void>('/ai/kb/edit', data)
}

export function deleteKnowledgeBase(kbId: string | number) {
  return post<void>(`/ai/kb/remove/${kbId}`)
}

export function listAllKnowledgeBases() {
  return get<KnowledgeBase[]>('/ai/kb/all')
}

// ===================== 知识库文档 =====================

export interface KbDocument {
  docId: string | number
  kbId: string | number
  docName: string
  docType: string
  filePath?: string
  fileSize: number
  charCount: number
  paragraphCount: number
  indexStatus: string
  indexError?: string
  status: string
  createTime?: string
}

export function listKbDocument(kbId: string | number, query: PageQuery & { docName?: string; indexStatus?: string }) {
  return get<PageResult<KbDocument>>(`/ai/kb/${kbId}/document/list`, query)
}

export function uploadKbDocument(kbId: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<void>(`/ai/kb/${kbId}/document/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function reindexKbDocument(docId: string | number) {
  return post<void>(`/ai/kb/document/reindex/${docId}`)
}

export function deleteKbDocument(docId: string | number) {
  return post<void>(`/ai/kb/document/remove/${docId}`)
}

// ===================== MCP服务 =====================

export interface McpServer {
  mcpId: string | number
  serverName: string
  description?: string
  transportType: string
  command?: string
  args?: string
  envVars?: string
  url?: string
  tools?: string
  status: string
  createTime?: string
}

export interface McpServerQuery extends PageQuery {
  serverName?: string
  transportType?: string
  status?: string
}

export function listMcpServer(query: McpServerQuery) {
  return get<PageResult<McpServer>>('/ai/mcp/list', query)
}

export function getMcpServer(mcpId: string | number) {
  return get<McpServer>(`/ai/mcp/${mcpId}`)
}

export function addMcpServer(data: Partial<McpServer>) {
  return post<void>('/ai/mcp', data)
}

export function updateMcpServer(data: Partial<McpServer>) {
  return post<void>('/ai/mcp/edit', data)
}

export function deleteMcpServer(mcpId: string | number) {
  return post<void>(`/ai/mcp/remove/${mcpId}`)
}

export function refreshMcpTools(mcpId: string | number) {
  return post<string>(`/ai/mcp/refresh/${mcpId}`)
}

export function listAllMcpServers() {
  return get<McpServer[]>('/ai/mcp/all')
}

// ===================== AI智能体 =====================

export interface AiAgent {
  agentId: string | number
  agentName: string
  description?: string
  avatar?: string
  modelId?: string | number
  knowledgeBaseIds?: string
  mcpServerIds?: string
  systemPrompt?: string
  welcomeMessage?: string
  prologue?: string
  published: boolean
  status: string
  createTime?: string
}

export interface AiAgentQuery extends PageQuery {
  agentName?: string
  published?: boolean
  status?: string
}

export function listAiAgent(query: AiAgentQuery) {
  return get<PageResult<AiAgent>>('/ai/agent/list', query)
}

export function getAiAgent(agentId: string | number) {
  return get<AiAgent>(`/ai/agent/${agentId}`)
}

export function addAiAgent(data: Partial<AiAgent>) {
  return post<void>('/ai/agent', data)
}

export function updateAiAgent(data: Partial<AiAgent>) {
  return post<void>('/ai/agent/edit', data)
}

export function deleteAiAgent(agentId: string | number) {
  return post<void>(`/ai/agent/remove/${agentId}`)
}

export function publishAiAgent(agentId: string | number) {
  return post<void>(`/ai/agent/publish/${agentId}`)
}

export function unpublishAiAgent(agentId: string | number) {
  return post<void>(`/ai/agent/unpublish/${agentId}`)
}

export function chatWithAgent(agentId: string | number, message: string, conversationId?: string) {
  return post<string>(`/ai/agent/chat/${agentId}`, { message, conversationId })
}

// ===================== AI工作流 =====================

export interface AiWorkflow {
  workflowId: string | number
  workflowName: string
  description?: string
  workflowType: string
  modelId?: string | number
  knowledgeBaseIds?: string
  mcpServerIds?: string
  systemPrompt?: string
  flowConfig?: string
  prologue?: string
  published: string
  status: string
  createTime?: string
}

export interface AiWorkflowQuery extends PageQuery {
  workflowName?: string
  workflowType?: string
  status?: string
}

export function listAiWorkflow(query: AiWorkflowQuery) {
  return get<PageResult<AiWorkflow>>('/ai/workflow/list', query)
}

export function getAiWorkflow(workflowId: string | number) {
  return get<AiWorkflow>(`/ai/workflow/${workflowId}`)
}

export function addAiWorkflow(data: Partial<AiWorkflow>) {
  return post<void>('/ai/workflow', data)
}

export function updateAiWorkflow(data: Partial<AiWorkflow>) {
  return post<void>('/ai/workflow/edit', data)
}

export function deleteAiWorkflow(workflowId: string | number) {
  return post<void>(`/ai/workflow/remove/${workflowId}`)
}

export function publishAiWorkflow(workflowId: string | number) {
  return post<void>(`/ai/workflow/publish/${workflowId}`)
}

export function unpublishAiWorkflow(workflowId: string | number) {
  return post<void>(`/ai/workflow/unpublish/${workflowId}`)
}

export function chatWithWorkflow(workflowId: string | number, message: string, conversationId?: string) {
  return post<string>(`/ai/workflow/chat/${workflowId}`, { message, conversationId })
}

// ===================== AI对话 =====================

export interface AiConversation {
  conversationId: string | number
  title: string
  workflowId?: string | number
  modelId?: string | number
  userId: string | number
  messageCount: number
  createTime?: string
  updateTime?: string
}

export interface AiChatMessage {
  messageId: string | number
  conversationId: string | number
  role: string
  content: string
  tokenCount?: number
  sortOrder: number
  createTime?: string
}

export interface ChatRequest {
  conversationId?: string | number
  workflowId?: string | number
  modelId?: string | number
  message: string
}

export function sendChatMessage(data: ChatRequest) {
  return post<AiChatMessage>('/ai/chat/send', data)
}

export interface AiConversationQuery extends PageQuery {
  workflowId?: string | number
}

export function listConversations(query: AiConversationQuery) {
  return get<PageResult<AiConversation>>('/ai/chat/conversations', query)
}

export function listChatMessages(conversationId: string | number) {
  return get<AiChatMessage[]>(`/ai/chat/messages/${conversationId}`)
}

export function deleteConversation(conversationId: string | number) {
  return post<void>(`/ai/chat/conversations/remove/${conversationId}`)
}

export function clearConversationMessages(conversationId: string | number) {
  return post<void>(`/ai/chat/conversations/clear/${conversationId}`)
}

export function renameConversation(conversationId: string | number, title: string) {
  return post<void>(`/ai/chat/conversations/rename/${conversationId}`, { title })
}

// ===================== Prompt模板 =====================

export interface AiPromptTemplate {
  templateId?: string | number
  tenantId?: string | number
  templateName: string
  category: string
  content: string
  variables?: string
  description?: string
  builtIn?: number
  status: string
  createTime?: string
  updateTime?: string
}

export function listPromptTemplate(params: any) {
  return get<PageResult<AiPromptTemplate>>('/ai/prompt/list', params)
}

export function getPromptTemplate(templateId: string | number) {
  return get<AiPromptTemplate>(`/ai/prompt/${templateId}`)
}

export function addPromptTemplate(data: AiPromptTemplate) {
  return post<void>('/ai/prompt', data)
}

export function editPromptTemplate(data: AiPromptTemplate) {
  return post<void>('/ai/prompt/edit', data)
}

export function removePromptTemplate(templateId: string | number) {
  return post<void>(`/ai/prompt/remove/${templateId}`)
}

export function listAllPromptTemplate() {
  return get<AiPromptTemplate[]>('/ai/prompt/all')
}

export function renderPromptTemplate(templateId: string | number, variables: Record<string, string>) {
  return post<string>(`/ai/prompt/render/${templateId}`, variables)
}

// ===================== Token统计 =====================

export function tokenStatsByModel(startTime: string, endTime: string) {
  return get<any[]>('/ai/token/stats/model', { startTime, endTime })
}

export function tokenStatsByUser(startTime: string, endTime: string) {
  return get<any[]>('/ai/token/stats/user', { startTime, endTime })
}

export function tokenStatsByDay(startTime: string, endTime: string) {
  return get<any[]>('/ai/token/stats/daily', { startTime, endTime })
}

// ===================== 知识库命中测试 =====================

export function hitTestKnowledgeBase(kbId: string | number, query: string) {
  return post<any[]>(`/ai/kb/hit-test/${kbId}`, { query })
}

// ===================== 选项常量 =====================

export const modelTypeOptions = [
  { label: '大语言模型', value: 'LLM' },
  { label: '向量模型', value: 'EMBEDDING' },
  { label: '重排模型', value: 'RERANK' },
  { label: '语音合成', value: 'TTS' },
  { label: '语音识别', value: 'STT' }
]

export const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: '通义千问', value: 'qwen' },
  { label: '智谱AI', value: 'zhipu' },
  { label: '百度千帆', value: 'baidu' },
  { label: 'Ollama', value: 'ollama' },
  { label: 'Azure OpenAI', value: 'azure' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'SiliconFlow', value: 'siliconflow' },
  { label: 'Coze(扣子)', value: 'coze' },
  { label: 'DIFY', value: 'dify' },
  { label: 'FastGPT', value: 'fastgpt' }
]

export const promptCategoryOptions = [
  { label: '系统提示词', value: 'system' },
  { label: '用户模板', value: 'user' },
  { label: '助手模板', value: 'assistant' }
]

export const kbTypeOptions = [
  { label: '通用知识库', value: 'general' },
  { label: 'QA问答库', value: 'qa' },
  { label: '网页爬取', value: 'web' }
]

export const transportTypeOptions = [
  { label: 'SSE', value: 'sse' },
  { label: 'Streamable HTTP', value: 'streamable_http' },
  { label: 'Stdio', value: 'stdio' }
]

export const workflowTypeOptions = [
  { label: '简单对话', value: 'simple' },
  { label: '高级编排', value: 'advanced' }
]

export const indexStatusOptions = [
  { label: '待处理', value: 'pending' },
  { label: '索引中', value: 'indexing' },
  { label: '已完成', value: 'completed' },
  { label: '失败', value: 'failed' }
]
