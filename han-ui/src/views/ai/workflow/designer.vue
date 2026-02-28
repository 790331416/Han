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
import { ArrowLeft, ZoomIn, ZoomOut, FullScreen, Check } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, Position, Handle } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import { getAiWorkflow, updateAiWorkflow, listAllModels, listAllKnowledgeBases, listAllMcpServers } from '@/api/ai'
import type { AiModel, KnowledgeBase, McpServer } from '@/api/ai'
import type { Node, Edge, Connection } from '@vue-flow/core'

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
    case 'tool': return { mcpId: null, toolName: '' }
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

// 保存流程
const handleSave = async () => {
  saving.value = true
  try {
    const flowConfig = JSON.stringify({ nodes: nodes.value, edges: edges.value })
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
</style>
