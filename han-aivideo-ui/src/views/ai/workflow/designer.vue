<template>
  <div class="workflow-designer">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
        <el-divider direction="vertical" />
        <span class="workflow-title">{{ workflowName || '流程设计器' }}</span>
      </div>
      <div class="toolbar-center">
        <el-button-group>
          <el-button :icon="ZoomIn" @click="() => zoomIn({ duration: 300 })" title="放大" />
          <el-button :icon="ZoomOut" @click="() => zoomOut({ duration: 300 })" title="缩小" />
          <el-button :icon="FullScreen" @click="() => fitView({ padding: 0.2, duration: 800 })" title="适应画布" />
        </el-button-group>
      </div>
      <div class="toolbar-right">
        <el-button :icon="CircleCheck" data-testid="ai-flow-validate-btn" @click="handleValidate">校验</el-button>
        <el-button :icon="VideoPlay" data-testid="ai-flow-debug-btn" @click="openDebugDrawer">调试运行</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- 左侧节点面板 -->
      <div class="node-panel">
        <div class="panel-title">节点类型</div>
        <div
          v-for="nodeType in nodeTypes"
          :key="nodeType.type"
          class="node-item"
          draggable="true"
          @dragstart="onDragStart($event, nodeType)"
        >
          <div class="node-icon" :style="{ background: nodeType.color }">
            <span>{{ nodeType.icon }}</span>
          </div>
          <span class="node-label">{{ nodeType.label }}</span>
        </div>
      </div>

      <!-- 画布 -->
      <div class="flow-canvas" @drop="onDrop" @dragover.prevent>
        <VueFlow
          ref="vueFlowRef"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 1, x: 0, y: 0 }"
          :snap-to-grid="true"
          :snap-grid="[15, 15]"
          fit-view-on-init
          @connect="onConnect"
          @node-click="onNodeClick"
        >
          <Background />
          <Controls />
          <MiniMap />

          <!-- 自定义节点模板 -->
          <template #node-start="{ data }">
            <div class="custom-node node-start">
              <div class="node-header">🚀 开始</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-llm="{ data }">
            <div class="custom-node node-llm">
              <div class="node-header">🤖 LLM调用</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-knowledge="{ data }">
            <div class="custom-node node-knowledge">
              <div class="node-header">📚 知识库检索</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-tool="{ data }">
            <div class="custom-node node-tool">
              <div class="node-header">🔧 工具调用</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
              <Handle type="source" :position="Position.Bottom" />
            </div>
          </template>

          <template #node-condition="{ data }">
            <div class="custom-node node-condition">
              <div class="node-header">⚡ 条件分支</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
              <Handle id="yes" type="source" :position="Position.Bottom" :style="{ left: '30%' }" />
              <Handle id="no" type="source" :position="Position.Bottom" :style="{ left: '70%' }" />
            </div>
          </template>

          <template #node-output="{ data }">
            <div class="custom-node node-output">
              <div class="node-header">📤 输出</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
            </div>
          </template>

          <template #node-end="{ data }">
            <div class="custom-node node-end">
              <div class="node-header">🏁 结束</div>
              <div class="node-body">{{ data.label }}</div>
              <Handle type="target" :position="Position.Top" />
            </div>
          </template>
        </VueFlow>
      </div>

      <!-- 调试运行抽屉 -->
      <el-drawer v-model="debugVisible" title="调试运行" size="440px" data-testid="ai-flow-debug-drawer">
        <div class="debug-drawer">
          <el-input
            v-model="debugMessage"
            type="textarea"
            :rows="3"
            placeholder="输入调试消息，如：帮我总结一下产品优势"
            data-testid="ai-flow-debug-input"
          />
          <el-button
            type="primary"
            style="margin-top: 12px; width: 100%;"
            :loading="debugRunning"
            data-testid="ai-flow-debug-run"
            @click="runDebug"
          >
            执行
          </el-button>

          <template v-if="debugResult">
            <el-alert
              v-if="debugRunning"
              type="info"
              title="执行中…（节点事件实时推送）"
              :closable="false"
              style="margin-top: 16px;"
            />
            <el-alert
              v-else
              :type="debugResult.success ? 'success' : 'error'"
              :title="debugResult.success ? '执行成功' : '执行失败'"
              :closable="false"
              style="margin-top: 16px;"
            />
            <div class="debug-reply" data-testid="ai-flow-debug-reply">{{ debugResult.reply }}</div>
            <div class="debug-timeline-title">节点执行时间线</div>
            <el-timeline style="padding-left: 4px;">
              <el-timeline-item
                v-for="trace in debugResult.nodeTraces"
                :key="trace.nodeId"
                :type="trace.status === 'succeeded' ? 'success' : trace.status === 'failed' ? 'danger' : trace.status === 'running' ? 'warning' : 'info'"
                :hollow="trace.status === 'skipped'"
              >
                <div class="trace-item" data-testid="ai-flow-debug-trace">
                  <div class="trace-header">
                    <strong>{{ trace.nodeName || trace.nodeId }}</strong>
                    <el-tag size="small" :type="trace.status === 'succeeded' ? 'success' : trace.status === 'failed' ? 'danger' : trace.status === 'running' ? 'warning' : 'info'">
                      {{ trace.status === 'succeeded' ? '成功' : trace.status === 'failed' ? '失败' : trace.status === 'running' ? '执行中' : '跳过' }}
                    </el-tag>
                    <span v-if="trace.costMs !== undefined && trace.costMs !== null" class="trace-cost">{{ trace.costMs }}ms</span>
                  </div>
                  <div v-if="trace.input" class="trace-detail">入参：{{ trace.input }}</div>
                  <div v-if="trace.output" class="trace-detail">出参：{{ trace.output }}</div>
                  <div v-if="trace.error" class="trace-detail trace-error">错误：{{ trace.error }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </template>
        </div>
      </el-drawer>

      <!-- 右侧属性面板 -->
      <div class="prop-panel" v-if="selectedNode">
        <div class="panel-title">
          节点属性
          <el-button type="danger" link size="small" @click="deleteNode">删除节点</el-button>
        </div>
        <el-form label-position="top" size="small">
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.data.label" @change="updateNode" />
          </el-form-item>

          <template v-if="selectedNode.type === 'llm'">
            <el-form-item label="AI模型">
              <el-select v-model="selectedNode.data.modelId" placeholder="选择模型" filterable @change="updateNode">
                <el-option v-for="m in llmModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
              </el-select>
            </el-form-item>
            <el-form-item label="系统提示词">
              <el-input v-model="selectedNode.data.systemPrompt" type="textarea" :rows="4" @change="updateNode" />
            </el-form-item>
            <el-form-item label="温度">
              <el-slider v-model="selectedNode.data.temperature" :min="0" :max="2" :step="0.1" @change="updateNode" />
            </el-form-item>
          </template>

          <template v-if="selectedNode.type === 'knowledge'">
            <el-form-item label="知识库">
              <el-select v-model="selectedNode.data.kbId" placeholder="选择知识库" filterable @change="updateNode">
                <el-option v-for="kb in allKbs" :key="kb.kbId" :label="kb.kbName" :value="kb.kbId" />
              </el-select>
            </el-form-item>
            <el-form-item label="检索数量">
              <el-input-number v-model="selectedNode.data.topK" :min="1" :max="20" @change="updateNode" />
            </el-form-item>
          </template>

          <template v-if="selectedNode.type === 'tool'">
            <el-form-item label="MCP服务">
              <el-select v-model="selectedNode.data.mcpId" placeholder="选择MCP" filterable @change="updateNode">
                <el-option v-for="mcp in allMcps" :key="mcp.mcpId" :label="mcp.serverName" :value="mcp.mcpId" />
              </el-select>
            </el-form-item>
            <el-form-item label="工具名称">
              <el-input v-model="selectedNode.data.toolName" placeholder="工具名称" @change="updateNode" />
            </el-form-item>
            <el-form-item label="入参JSON">
              <el-input
                v-model="selectedNode.data.arguments"
                type="textarea"
                :rows="4"
                placeholder='如 {"query":"{{result}}"}，支持 {{message}}/{{result}}/{{knowledge}}'
                @change="updateNode"
              />
            </el-form-item>
          </template>

          <template v-if="selectedNode.type === 'condition'">
            <el-form-item label="条件表达式">
              <el-input v-model="selectedNode.data.expression" type="textarea" :rows="3" placeholder="如: {{result}} contains '是'" @change="updateNode" />
            </el-form-item>
          </template>

          <template v-if="selectedNode.type === 'output'">
            <el-form-item label="输出模板">
              <el-input v-model="selectedNode.data.template" type="textarea" :rows="4" placeholder="使用 {{变量名}} 引用上游输出" @change="updateNode" />
            </el-form-item>
          </template>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ZoomIn, ZoomOut, FullScreen, Check, CircleCheck, VideoPlay } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, Position, Handle } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import { getAiWorkflow, updateAiWorkflow, debugAiWorkflow, listAllModels, listAllKnowledgeBases, listAllMcpServers } from '@/api/ai'
import type { AiModel, KnowledgeBase, McpServer, AiFlowDebugResult, AiFlowNodeTrace } from '@/api/ai'
import type { Node, Edge, Connection } from '@vue-flow/core'
import { requestAiStream, type AiStreamNodeEvent } from '@/utils/ai-stream'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const workflowId = Number(route.params.workflowId)

const { zoomIn, zoomOut, fitView } = useVueFlow()

const vueFlowRef = ref()
const workflowName = ref('')
const saving = ref(false)
const selectedNode = ref<Node | null>(null)
const llmModels = ref<AiModel[]>([])
const allKbs = ref<KnowledgeBase[]>([])
const allMcps = ref<McpServer[]>([])

const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始', icon: '🚀', color: '#67c23a' },
  { type: 'llm', label: 'LLM调用', icon: '🤖', color: '#409eff' },
  { type: 'knowledge', label: '知识库检索', icon: '📚', color: '#e6a23c' },
  { type: 'tool', label: '工具调用', icon: '🔧', color: '#909399' },
  { type: 'condition', label: '条件分支', icon: '⚡', color: '#f56c6c' },
  { type: 'output', label: '输出', icon: '📤', color: '#0ea5e9' },
  { type: 'end', label: '结束', icon: '🏁', color: '#6366f1' },
]

let nodeId = 0
const getNodeId = () => `node_${++nodeId}`

const goBack = () => router.push('/ai/workflow')

// 拖拽创建节点
const onDragStart = (event: DragEvent, nodeType: any) => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType))
    event.dataTransfer.effectAllowed = 'move'
  }
}

const onDrop = (event: DragEvent) => {
  const data = event.dataTransfer?.getData('application/vueflow')
  if (!data) return
  const nodeType = JSON.parse(data)

  const canvas = document.querySelector('.vue-flow__viewport') as HTMLElement
  if (!canvas) return
  const bounds = canvas.getBoundingClientRect()

  const newNode: Node = {
    id: getNodeId(),
    type: nodeType.type,
    position: { x: event.clientX - bounds.left - 80, y: event.clientY - bounds.top - 20 },
    data: { label: nodeType.label, ...getDefaultData(nodeType.type) },
  }
  nodes.value = [...nodes.value, newNode]
}

const getDefaultData = (type: string) => {
  switch (type) {
    case 'llm': return { modelId: null, systemPrompt: '', temperature: 0.7 }
    case 'knowledge': return { kbId: null, topK: 5 }
    case 'tool': return { mcpId: null, toolName: '', arguments: '' }
    case 'condition': return { expression: '' }
    case 'output': return { template: '' }
    default: return {}
  }
}

// 连线
const onConnect = (connection: Connection) => {
  const newEdge: Edge = {
    id: `e${connection.source}-${connection.target}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle || undefined,
    targetHandle: connection.targetHandle || undefined,
    animated: true,
    style: { stroke: '#409eff' },
  }
  edges.value = [...edges.value, newEdge]
}

// 选中节点
const onNodeClick = (event: any) => {
  selectedNode.value = event.node
}

const updateNode = () => {
  // 触发响应式更新
  nodes.value = [...nodes.value]
}

const deleteNode = () => {
  if (!selectedNode.value) return
  const id = selectedNode.value.id
  nodes.value = nodes.value.filter(n => n.id !== id)
  edges.value = edges.value.filter(e => e.source !== id && e.target !== id)
  selectedNode.value = null
}

// ==================== 画布校验（前端体验层，后端保存时二次复核） ====================
const invalidNodeIds = ref<Set<string>>(new Set())

/** DAG 校验：唯一 start、无环、无孤岛；失败节点红框标注。返回错误列表。 */
const validateFlow = (): string[] => {
  const errors: string[] = []
  const invalid = new Set<string>()
  const nodeList = nodes.value
  const edgeList = edges.value

  const startNodes = nodeList.filter(n => n.type === 'start')
  if (startNodes.length === 0) {
    errors.push('缺少开始节点')
  } else if (startNodes.length > 1) {
    errors.push('只能有一个开始节点')
    startNodes.forEach(n => invalid.add(n.id))
  }

  if (nodeList.length > 30) {
    errors.push('节点数超过上限 30')
  }

  // Kahn 拓扑检环
  const inDegree = new Map<string, number>()
  nodeList.forEach(n => inDegree.set(n.id, 0))
  edgeList.forEach(e => inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1))
  const queue = nodeList.filter(n => (inDegree.get(n.id) ?? 0) === 0).map(n => n.id)
  let visitedCount = 0
  while (queue.length > 0) {
    const current = queue.shift()!
    visitedCount++
    edgeList.filter(e => e.source === current).forEach(e => {
      const next = (inDegree.get(e.target) ?? 0) - 1
      inDegree.set(e.target, next)
      if (next === 0) queue.push(e.target)
    })
  }
  if (visitedCount !== nodeList.length) {
    errors.push('存在环路，请检查连线方向')
    nodeList.forEach(n => {
      if ((inDegree.get(n.id) ?? 0) > 0) invalid.add(n.id)
    })
  }

  // 无向连通性检查孤岛
  if (startNodes.length === 1 && nodeList.length > 1) {
    const visited = new Set<string>([startNodes[0].id])
    const bfs = [startNodes[0].id]
    while (bfs.length > 0) {
      const current = bfs.shift()!
      edgeList.forEach(e => {
        const next = e.source === current ? e.target : e.target === current ? e.source : null
        if (next && !visited.has(next)) {
          visited.add(next)
          bfs.push(next)
        }
      })
    }
    const orphans = nodeList.filter(n => !visited.has(n.id))
    if (orphans.length > 0) {
      errors.push(`存在孤岛节点：${orphans.map(n => n.data?.label || n.id).join('、')}`)
      orphans.forEach(n => invalid.add(n.id))
    }
  }

  invalidNodeIds.value = invalid
  // 红框标注失败节点
  nodes.value = nodeList.map(n => ({
    ...n,
    class: invalid.has(n.id) ? 'node-invalid' : ''
  }))
  return errors
}

const handleValidate = () => {
  const errors = validateFlow()
  if (errors.length === 0) {
    ElMessage.success('校验通过：画布结构合法')
  } else {
    ElMessage.error('校验失败：' + errors.join('；'))
  }
}

// ==================== 调试运行 ====================
const debugVisible = ref(false)
const debugMessage = ref('')
const debugRunning = ref(false)
const debugResult = ref<AiFlowDebugResult | null>(null)

const openDebugDrawer = () => {
  debugVisible.value = true
}

const runDebug = async () => {
  const message = debugMessage.value.trim()
  if (!message) {
    ElMessage.warning('请输入调试消息')
    return
  }
  const errors = validateFlow()
  if (errors.length > 0) {
    ElMessage.error('画布校验失败：' + errors.join('；'))
    return
  }
  debugRunning.value = true
  debugResult.value = null
  try {
    // 先保存再调试，保证后端执行的是当前画布
    const flowConfig = JSON.stringify({ version: 1, nodes: nodes.value, edges: edges.value })
    await updateAiWorkflow({ workflowId, flowConfig } as any)
    await runDebugStream(message)
  } catch (streamError: any) {
    // 流式调试链路异常时降级为一次性调试接口
    try {
      const res = await debugAiWorkflow(workflowId, message)
      debugResult.value = (res as any).data || null
    } catch (e: any) {
      ElMessage.error('调试运行失败: ' + (e.message || streamError.message || '未知错误'))
    }
  } finally {
    debugRunning.value = false
  }
}

/**
 * 流式调试：消费 node_start/node_delta/node_end 事件实时点亮时间线，
 * llm 逐 token 先行展示，最终回复以后端 delta 事件为准。
 */
async function runDebugStream(message: string) {
  const userStore = useUserStore()
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const live: AiFlowDebugResult = { success: true, reply: '', nodeTraces: [] }
  debugResult.value = live
  let sawFinalReply = false
  const publish = () => {
    debugResult.value = { ...live, nodeTraces: [...live.nodeTraces] }
  }
  await requestAiStream({
    baseUrl,
    path: `/ai/workflow/debug-stream/${workflowId}`,
    token: userStore.token,
    tenantId: userStore.tenantId,
    body: { message },
    onNodeEvent: (event: AiStreamNodeEvent) => {
      if (event.type === 'node_start') {
        live.nodeTraces.push({
          nodeId: event.content.nodeId || '',
          nodeType: event.content.nodeType || '',
          nodeName: event.content.nodeName,
          status: 'running'
        })
      } else if (event.type === 'node_delta') {
        if (!sawFinalReply) {
          live.reply += event.content.delta || ''
        }
      } else if (event.type === 'node_end') {
        const trace = event.content as unknown as AiFlowNodeTrace
        const runningIndex = live.nodeTraces.findIndex(
          (item) => item.nodeId === trace.nodeId && item.status === 'running'
        )
        if (runningIndex >= 0) {
          live.nodeTraces.splice(runningIndex, 1, trace)
        } else {
          live.nodeTraces.push(trace)
        }
      }
      publish()
    },
    onDelta: ({ fullContent }) => {
      sawFinalReply = true
      live.reply = fullContent
      publish()
    },
    onMeta: (meta) => {
      if (typeof meta.success === 'boolean') {
        live.success = meta.success
      }
      if (Array.isArray(meta.nodeTraces)) {
        live.nodeTraces = meta.nodeTraces as AiFlowNodeTrace[]
      }
      publish()
    }
  })
}

// 保存流程
const handleSave = async () => {
  const errors = validateFlow()
  if (errors.length > 0) {
    ElMessage.error('画布校验失败：' + errors.join('；'))
    return
  }
  saving.value = true
  try {
    const flowConfig = JSON.stringify({ version: 1, nodes: nodes.value, edges: edges.value })
    await updateAiWorkflow({ workflowId, flowConfig } as any)
    ElMessage.success('流程保存成功')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

// 加载工作流
const loadWorkflow = async () => {
  try {
    const res = await getAiWorkflow(workflowId)
    const wf = res.data
    workflowName.value = wf.workflowName || ''
    if (wf.flowConfig) {
      try {
        const config = JSON.parse(wf.flowConfig)
        nodes.value = config.nodes || []
        edges.value = config.edges || []
        // 更新 nodeId 计数器
        nodes.value.forEach(n => {
          const match = n.id.match(/node_(\d+)/)
          if (match) nodeId = Math.max(nodeId, parseInt(match[1]))
        })
      } catch { /* 无效配置，使用默认 */ }
    }
    // 如果没有节点，创建默认开始节点
    if (nodes.value.length === 0) {
      nodes.value = [
        { id: getNodeId(), type: 'start', position: { x: 250, y: 50 }, data: { label: '开始' } }
      ]
    }
  } catch { /* ignore */ }
}

const loadOptions = async () => {
  try {
    const [m, k, mc] = await Promise.all([listAllModels('LLM'), listAllKnowledgeBases(), listAllMcpServers()])
    llmModels.value = m.data || []
    allKbs.value = k.data || []
    allMcps.value = mc.data || []
  } catch { /* ignore */ }
}

onMounted(() => {
  loadWorkflow()
  loadOptions()
})
</script>

<style lang="scss" scoped>
.workflow-designer {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  z-index: 10;
}
.toolbar-left { display: flex; align-items: center; gap: 8px; }
.toolbar-center { display: flex; align-items: center; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.workflow-title { font-size: 16px; font-weight: 600; color: #303133; }

.designer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

// 左侧节点面板
.node-panel {
  width: 180px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.2s;
  &:hover { background: #f5f7fa; }
  &:active { cursor: grabbing; }
}
.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #fff;
  flex-shrink: 0;
}
.node-label { font-size: 13px; color: #303133; }

// 画布
.flow-canvas {
  flex: 1;
  position: relative;
}

// 右侧属性面板
.prop-panel {
  width: 280px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 16px;
  overflow-y: auto;
}

// 自定义节点样式
.custom-node {
  min-width: 160px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  overflow: hidden;
  background: #fff;
}
.node-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}
.node-body {
  padding: 8px 12px;
  font-size: 13px;
  color: #303133;
}

.node-start .node-header { background: #67c23a; }
.node-llm .node-header { background: #409eff; }
.node-knowledge .node-header { background: #e6a23c; }
.node-tool .node-header { background: #909399; }
.node-condition .node-header { background: #f56c6c; }
.node-output .node-header { background: #0ea5e9; }
.node-end .node-header { background: #6366f1; }

// 校验失败节点红框
:deep(.vue-flow__node.node-invalid) .custom-node {
  outline: 2px solid #f56c6c;
  outline-offset: 2px;
  border-radius: 10px;
}

// 调试运行抽屉
.debug-drawer {
  display: flex;
  flex-direction: column;
}
.debug-reply {
  margin: 12px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
}
.debug-timeline-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 4px 0 10px;
}
.trace-item {
  .trace-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .trace-cost { font-size: 12px; color: #909399; }
  .trace-detail {
    font-size: 12px;
    color: #606266;
    line-height: 1.6;
    word-break: break-word;
    max-height: 88px;
    overflow-y: auto;
  }
  .trace-error { color: #f56c6c; }
}
</style>
