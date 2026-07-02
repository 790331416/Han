<template>
  <div class="app-container" data-testid="ai-knowledge-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="知识库名称" prop="kbName">
          <el-input v-model="queryParams.kbName" placeholder="请输入知识库名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="kbType">
          <el-select v-model="queryParams.kbType" placeholder="请选择" clearable>
            <el-option v-for="item in kbTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <el-button type="primary" :icon="Plus" data-testid="ai-knowledge-create-button" @click="handleAdd">创建知识库</el-button>
        </div>
      </template>

      <!-- 卡片模式展示 -->
      <el-row :gutter="20" v-loading="loading" data-testid="ai-knowledge-list">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in kbList" :key="kb.kbId" class="kb-col">
          <el-card shadow="hover" class="kb-card" data-testid="ai-knowledge-card" :data-kb-id="String(kb.kbId)" :data-kb-name="kb.kbName" @click="handleDetail(kb)">
            <div class="kb-card-header">
              <el-icon :size="32" color="#409eff"><Collection /></el-icon>
              <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, kb)" @click.stop>
                <el-icon class="kb-more" :data-testid="`ai-knowledge-card-actions-${kb.kbId}`"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="hitTest" data-testid="ai-knowledge-hit-test-command">命中测试</el-dropdown-item>
                    <el-dropdown-item command="delete" data-testid="ai-knowledge-delete-command" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <h3 class="kb-name">{{ kb.kbName }}</h3>
            <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
            <div class="kb-stats">
              <el-tag size="small">{{ getKbTypeLabel(kb.kbType) }}</el-tag>
              <span class="kb-stat-item">
                <el-icon><Document /></el-icon> {{ kb.documentCount }} 文档
              </span>
              <span class="kb-stat-item">
                <el-icon><Tickets /></el-icon> {{ kb.paragraphCount }} 段落
              </span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="24" v-if="!loading && kbList.length === 0">
          <el-empty description="暂无知识库" />
        </el-col>
      </el-row>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[8, 16, 32]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.kbId ? '编辑知识库' : '创建知识库'" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" data-testid="ai-knowledge-form">
        <el-form-item label="知识库名称" prop="kbName">
          <el-input v-model="form.kbName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="类型" prop="kbType">
          <el-select v-model="form.kbType" placeholder="请选择类型">
            <el-option v-for="item in kbTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Embedding模型" prop="embeddingModelId">
          <el-select v-model="form.embeddingModelId" placeholder="请选择向量模型(可选)" clearable>
            <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="知识库描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 文档管理对话框 -->
    <el-dialog v-model="docVisible" :title="`文档管理 - ${currentKb?.kbName || ''}`" width="80%" class="dialog-xl" destroy-on-close>
      <div data-testid="ai-knowledge-doc-dialog">
      <div class="doc-header">
        <el-upload data-testid="ai-knowledge-upload" :auto-upload="false" :show-file-list="false" accept=".txt,.pdf,.md,.docx,.html" :on-change="handleFileSelect">
          <el-button type="primary" :icon="Upload" data-testid="ai-knowledge-upload-button">上传文档</el-button>
        </el-upload>
      </div>
      <el-table v-loading="docLoading" :data="docList" style="margin-top: 16px;" data-testid="ai-knowledge-doc-table">
        <el-table-column label="文档名称" prop="docName" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" prop="docType" width="80" align="center">
          <template #default="{ row }"><el-tag size="small">{{ row.docType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="center">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="段落数" prop="paragraphCount" width="80" align="center" />
        <el-table-column label="索引状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getIndexStatusType(row.indexStatus)" size="small">
              {{ getIndexStatusLabel(row.indexStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" prop="createTime" min-width="170" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="150">
          <template #default="{ row }">
            <el-button type="primary" link data-testid="ai-knowledge-reindex-button" @click="handleReindex(row)">重新索引</el-button>
            <el-button type="danger" link data-testid="ai-knowledge-delete-doc-button" @click="handleDeleteDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
    </el-dialog>
    <!-- 命中测试对话框 -->
    <el-dialog v-model="hitTestVisible" :title="`命中测试 - ${hitTestKbName}`" width="70%" class="dialog-xl" destroy-on-close>
      <div class="hit-test-input" data-testid="ai-knowledge-hit-test-dialog">
        <el-input v-model="hitTestQuery" data-testid="ai-knowledge-hit-test-input" placeholder="请输入测试查询文本" type="textarea" :rows="3" />
        <el-button type="primary" :loading="hitTestLoading" data-testid="ai-knowledge-hit-test-submit" @click="doHitTest" style="margin-top: 12px;">检索测试</el-button>
      </div>
      <div v-if="hitTestResults.length > 0" class="hit-test-results" data-testid="ai-knowledge-hit-test-results">
        <div class="hit-test-title">检索到 {{ hitTestResults.length }} 条结果：</div>
        <el-collapse accordion>
          <el-collapse-item v-for="(item, idx) in hitTestResults" :key="idx" :name="idx" data-testid="ai-knowledge-hit-test-result">
            <template #title>
              <div class="hit-item-header">
                <span class="hit-rank">#{{ idx + 1 }}</span>
                <span class="hit-title">{{ item.title }}</span>
                <el-tag v-if="item.retrievalType" size="small" :type="item.retrievalType === 'vector' ? 'success' : 'info'">
                  {{ item.retrievalType === 'vector' ? '向量' : '关键词' }}
                </el-tag>
                <el-tag v-if="item.score" size="small" type="warning" class="hit-score">
                  相似度: {{ (item.score * 100).toFixed(1) }}%
                </el-tag>
              </div>
            </template>
            <div v-if="item.docName" class="hit-doc">来源文档：{{ item.docName }}</div>
            <div class="hit-content">{{ item.content }}</div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <el-empty v-else-if="hitTestSearched && !hitTestLoading" description="未检索到相关段落" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Upload, Document, Tickets, Collection, MoreFilled } from '@element-plus/icons-vue'
import {
  listKnowledgeBase, addKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase,
  listKbDocument, uploadKbDocument, reindexKbDocument, deleteKbDocument,
  listAllModels, hitTestKnowledgeBase, kbTypeOptions as fallbackKbTypeOptions, indexStatusOptions as fallbackIndexStatusOptions,
  type KnowledgeBase, type KnowledgeBaseQuery, type KbDocument, type AiModel
} from '@/api/ai'
import { AI_KB_TYPE_DICT, AI_KNOWLEDGE_INDEX_STATUS_DICT, findDictLabel, loadDictOptionSet, type DictOption } from '@/utils/dict-options'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'

const loading = ref(false)
const kbList = ref<KnowledgeBase[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const docVisible = ref(false)
const docLoading = ref(false)
const docList = ref<KbDocument[]>([])
const currentKb = ref<KnowledgeBase | null>(null)
const embeddingModels = ref<AiModel[]>([])
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const kbTypeOptions = ref<DictOption[]>([...fallbackKbTypeOptions])
const indexStatusOptions = ref<DictOption[]>([...fallbackIndexStatusOptions])

const queryParams = reactive<KnowledgeBaseQuery>({ pageNum: 1, pageSize: 8 })
const form = reactive<any>({ kbId: undefined, kbName: '', kbType: 'general', description: '', embeddingModelId: undefined })

const rules: FormRules = {
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  kbType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

/**
 * 知识库页面统一复用系统字典，避免知识库类型、索引状态在页面内长期写死。
 */
const getKbTypeLabel = (v: string) => findDictLabel(kbTypeOptions.value, v, v)
const getIndexStatusLabel = (v: string) => findDictLabel(indexStatusOptions.value, v, v)
const getIndexStatusType = (v: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  const m: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { pending: 'info', indexing: 'warning', completed: 'success', failed: 'danger' }
  return m[v] || 'info'
}
const formatSize = (bytes: number) => {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listKnowledgeBase(queryParams)
    kbList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }

const handleAdd = async () => {
  Object.assign(form, { kbId: undefined, kbName: '', kbType: 'general', description: '', embeddingModelId: undefined })
  const res = await listAllModels('EMBEDDING')
  embeddingModels.value = res.data || []
  dialogVisible.value = true
}

const hitTestVisible = ref(false)
const hitTestLoading = ref(false)
const hitTestQuery = ref('')
const hitTestResults = ref<any[]>([])
const hitTestKbName = ref('')
const hitTestKbId = ref<string | number>(0)
const hitTestSearched = ref(false)

const handleCommand = (cmd: string, kb: KnowledgeBase) => {
  if (cmd === 'edit') handleEdit(kb)
  else if (cmd === 'delete') handleDelete(kb)
  else if (cmd === 'hitTest') openHitTest(kb)
}

const openHitTest = (kb: KnowledgeBase) => {
  hitTestKbId.value = kb.kbId
  hitTestKbName.value = kb.kbName
  hitTestQuery.value = ''
  hitTestResults.value = []
  hitTestSearched.value = false
  hitTestVisible.value = true
}

const doHitTest = async () => {
  if (!hitTestQuery.value.trim()) {
    ElMessage.warning('请输入查询文本')
    return
  }
  hitTestLoading.value = true
  hitTestSearched.value = true
  try {
    const res = await hitTestKnowledgeBase(hitTestKbId.value, hitTestQuery.value)
    hitTestResults.value = res.data || []
  } catch { hitTestResults.value = [] } finally {
    hitTestLoading.value = false
  }
}

const handleEdit = async (kb: KnowledgeBase) => {
  Object.assign(form, kb)
  const res = await listAllModels('EMBEDDING')
  embeddingModels.value = res.data || []
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.kbId) { await updateKnowledgeBase(form); ElMessage.success('修改成功') }
    else { await addKnowledgeBase(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    getList()
  } catch { /* 接口不可用 */ } finally { submitLoading.value = false }
}

const handleDelete = async (kb: KnowledgeBase) => {
  try {
    await ElMessageBox.confirm(`确定删除知识库"${kb.kbName}"吗? 相关文档将一并删除!`, '提示', { type: 'warning' })
    await deleteKnowledgeBase(kb.kbId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleDetail = async (kb: KnowledgeBase) => {
  currentKb.value = kb
  docVisible.value = true
  docLoading.value = true
  try {
    const res = await listKbDocument(kb.kbId, { pageNum: 1, pageSize: 100 })
    docList.value = res.data.rows
  } catch { /* 接口不可用 */ } finally { docLoading.value = false }
}

const handleFileSelect = async (file: UploadFile) => {
  try {
    if (!currentKb.value || !file.raw) return
    await uploadKbDocument(currentKb.value.kbId, file.raw)
    ElMessage.success('上传成功')
    handleDetail(currentKb.value)
    getList()
  } catch { /* 接口不可用 */ }
}

const handleReindex = async (doc: KbDocument) => {
  try {
    await reindexKbDocument(doc.docId)
    ElMessage.success('已提交重新索引')
    if (currentKb.value) handleDetail(currentKb.value)
  } catch { /* 接口不可用 */ }
}

const handleDeleteDoc = async (doc: KbDocument) => {
  try {
    await ElMessageBox.confirm(`确定删除文档"${doc.docName}"吗?`, '提示', { type: 'warning' })
    await deleteKbDocument(doc.docId)
    ElMessage.success('删除成功')
    if (currentKb.value) handleDetail(currentKb.value)
    getList()
  } catch { /* 接口不可用 */ }
}

onMounted(async () => {
  const options = await loadDictOptionSet({
    kbTypeOptions: { dictType: AI_KB_TYPE_DICT, fallback: fallbackKbTypeOptions },
    indexStatusOptions: { dictType: AI_KNOWLEDGE_INDEX_STATUS_DICT, fallback: fallbackIndexStatusOptions }
  })
  kbTypeOptions.value = options.kbTypeOptions
  indexStatusOptions.value = options.indexStatusOptions
  await getList()
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

.kb-col { margin-bottom: 20px; }
.kb-card {
  cursor: pointer;
  transition: transform 0.2s;
  &:hover { transform: translateY(-4px); }
}
.kb-card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.kb-more { cursor: pointer; color: #909399; &:hover { color: #409eff; } }
.kb-name { margin: 12px 0 8px; font-size: 16px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-desc { color: #909399; font-size: 13px; margin-bottom: 12px; height: 40px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.kb-stats { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #909399; }
.kb-stat-item { display: flex; align-items: center; gap: 4px; }
.doc-header { display: flex; justify-content: flex-end; }

.hit-test-input { margin-bottom: 16px; }
.hit-test-results { margin-top: 16px; }
.hit-test-title { font-weight: bold; margin-bottom: 12px; color: #303133; }
.hit-item-header { display: flex; align-items: center; gap: 8px; width: 100%; }
.hit-rank { font-weight: bold; color: #409eff; min-width: 28px; }
.hit-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hit-score { margin-left: auto; }
.hit-doc { font-size: 12px; color: #909399; margin-bottom: 8px; }
.hit-content { white-space: pre-wrap; line-height: 1.6; color: #606266; font-size: 13px; background: #f5f7fa; padding: 12px; border-radius: 4px; }
</style>
