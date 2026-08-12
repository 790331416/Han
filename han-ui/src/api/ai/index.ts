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
  /** 是否支持视觉输入（图片理解）：'1'支持 '0'不支持 */
  supportsVision?: string
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
  /** 对话历史注入条数（空=默认 12） */
  historyLimit?: number
  published: boolean
  /** 公开分享链接 key（发布时生成） */
  shareKey?: string
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

// 重置分享链接（旧 shareKey 立即失效，返回新 key）
export function resetAgentShareKey(agentId: string | number) {
  return post<string>(`/ai/agent/reset-share-key/${agentId}`)
}

// ===================== 公开分享（免登录，/ai/share 网关白名单） =====================

export interface ShareProfile {
  agentName?: string
  avatar?: string
  prologue?: string
  description?: string
}

export interface ShareChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export function getShareProfile(shareKey: string) {
  return get<ShareProfile>(`/ai/share/${shareKey}/profile`, undefined, { silentError: true })
}

export function shareChat(shareKey: string, message: string, history: ShareChatHistoryItem[]) {
  return post<{ reply: string }>(`/ai/share/${shareKey}/chat`, { message, history })
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

// 编排节点执行轨迹（advanced 工作流执行时间线）
export interface AiFlowNodeTrace {
  nodeId: string
  nodeType: string
  nodeName?: string
  /** succeeded / failed / skipped */
  status: string
  input?: string
  output?: string
  costMs?: number
  error?: string
}

export interface AiFlowDebugResult {
  success: boolean
  reply: string
  nodeTraces: AiFlowNodeTrace[]
}

// 编排调试运行（设计器调试抽屉）：不要求已发布、不落会话消息
export function debugAiWorkflow(workflowId: string | number, message: string) {
  return post<AiFlowDebugResult>(`/ai/workflow/debug/${workflowId}`, { message })
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
  /** 图片附件（多模态输入图 / 对话内生成图） */
  imageList?: AiChatImage[]
  knowledgeSources?: AiChatKnowledgeSource[]
  toolExecutions?: AiChatToolTrace[]
  /** 编排节点执行时间线（advanced 工作流消息专有） */
  nodeTraces?: AiFlowNodeTrace[]
}

export interface AiChatImage {
  fileId?: string | number
  url: string
  name?: string
}

export interface AiChatKnowledgeSource {
  kbId?: string | number
  kbName?: string
  kbType?: string
  kbStatus?: string
  documentCount?: number
  paragraphCount?: number
  charCount?: number
  paragraphId?: string | number
  paragraphTitle?: string
  hitCount?: number
  excerpt?: string
  /** 相关度 0~1（向量=余弦相似度，关键词=启发式） */
  score?: number
  /** 检索方式：vector / keyword */
  retrievalType?: string
}

// 引用出处详情（引用点击查看）
export interface KnowledgeParagraphDetail {
  paragraphId: string | number
  title?: string
  content: string
  charCount?: number
  hitCount?: number
  kbId?: string | number
  kbName?: string
  docId?: string | number
  docName?: string
  vectorized?: boolean
}

// 查询段落出处详情
export function getKnowledgeParagraphDetail(paragraphId: string | number) {
  return get<KnowledgeParagraphDetail>(`/ai/kb/paragraph/${paragraphId}`)
}

export interface AiChatToolTrace {
  mcpId?: string | number
  serverName?: string
  transportType?: string
  status?: string
  toolCount?: number
  toolNames?: string[]
  summary?: string
  /** 以下字段非空时为一次真实 tools/call 调用记录 */
  toolName?: string
  callArgs?: string
  callResult?: string
  costMs?: number
  /** succeeded / failed */
  callStatus?: string
}

export interface ChatRequest {
  conversationId?: string | number
  workflowId?: string | number
  modelId?: string | number
  message: string
  /** 图片附件文件ID（多模态输入，须模型支持视觉） */
  imageFileIds?: (string | number)[]
}

export function sendChatMessage(data: ChatRequest) {
  return post<AiChatMessage>('/ai/chat/send', data)
}

// 上传对话图片附件（走文件服务，返回 fileId + 公开访问地址）
export function uploadChatImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<{ id: string | number; name: string; url: string }>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export interface ChatImageRequest {
  conversationId?: string | number
  modelId?: string | number
  prompt: string
  size?: string
}

// 对话内文生图（IMAGE 模型），返回带图片附件的 assistant 消息
export function generateChatImage(data: ChatImageRequest) {
  return post<AiChatMessage>('/ai/chat/image', data)
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
//
// 说明：
// 1. 这里保留的是前端兜底选项，防止字典尚未初始化或字典接口暂时不可用时页面直接失效。
// 2. 正常情况下，页面应优先通过系统字典加载这些枚举；不要在业务页继续复制一份本地常量。
// 3. 若后续某一组选项已经完全迁移到字典中心，可在确认所有页面都已改造后再移除对应 fallback。

export const modelTypeOptions = [
  { label: '大语言模型', value: 'LLM' },
  { label: '图片生成模型', value: 'IMAGE' },
  { label: '视频生成模型', value: 'VIDEO' },
  { label: '视频剪辑合成', value: 'VIDEO_EDIT' },
  { label: '向量模型', value: 'EMBEDDING' },
  { label: '重排模型', value: 'RERANK' },
  { label: '语音合成', value: 'TTS' },
  { label: '语音识别', value: 'STT' }
]

export const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: '火山引擎/方舟', value: 'volcengine' },
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
  { label: '助手模板', value: 'assistant' },
  { label: 'AIVideo 文本润色', value: 'aivideo_text' },
  { label: 'AIVideo 剧本生成', value: 'aivideo_script' },
  { label: 'AIVideo 资产提取', value: 'aivideo_asset' },
  { label: 'AIVideo 分镜提取', value: 'aivideo_storyboard' },
  { label: 'AIVideo 图片生成', value: 'aivideo_image' },
  { label: 'AIVideo 视频生成', value: 'aivideo_video' },
  { label: 'AIVideo 语音合成', value: 'aivideo_tts' }
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
