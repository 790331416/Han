<template>
  <div class="app-container" data-testid="ai-prompt-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="queryParams.category" placeholder="请选择" clearable>
            <el-option v-for="item in promptCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>Prompt模板列表</span>
          <el-button v-hasPermi="['ai:prompt:add']" type="primary" :icon="Plus" data-testid="ai-prompt-create-button" @click="handleAdd">新增模板</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="templateList" data-testid="ai-prompt-table">
        <el-table-column label="模板名称" prop="templateName" min-width="150" show-overflow-tooltip />
        <el-table-column label="分类" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.category === 'system' ? 'primary' : row.category === 'user' ? 'success' : 'warning'">
              {{ getCategoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模板内容" prop="content" min-width="250" show-overflow-tooltip />
        <el-table-column label="变量" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.variables">{{ row.variables }}</span>
            <span v-else class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.builtIn === 1 ? 'info' : 'success'" size="small">
              {{ row.builtIn === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="170" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button type="info" link data-testid="ai-prompt-preview-button" @click="handlePreview(row)">预览</el-button>
            <el-button v-hasPermi="['ai:prompt:edit']" type="primary" link :icon="Edit" data-testid="ai-prompt-edit-button" @click="handleEdit(row)" :disabled="row.builtIn === 1">编辑</el-button>
            <el-button v-hasPermi="['ai:prompt:remove']" type="danger" link :icon="Delete" data-testid="ai-prompt-delete-button" @click="handleDelete(row)" :disabled="row.builtIn === 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.templateId ? '编辑模板' : '新增模板'" width="65%" class="dialog-lg" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" data-testid="ai-prompt-form">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" data-testid="ai-prompt-name-input" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类">
            <el-option v-for="item in promptCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" data-testid="ai-prompt-content-input" type="textarea" :rows="6" placeholder="请输入模板内容，支持 {{变量名}} 占位符" />
        </el-form-item>
        <el-form-item label="变量列表" prop="variables">
          <el-input v-model="form.variables" data-testid="ai-prompt-variables-input" placeholder='JSON数组，如 ["name","topic"]（可选）' />
        </el-form-item>
        <el-form-item label="场景说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="模板使用场景说明" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" data-testid="ai-prompt-submit-button" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="模板预览" width="55%" class="dialog-md">
      <div data-testid="ai-prompt-preview-panel">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="模板名称">{{ previewData.templateName }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ getCategoryLabel(previewData.category) }}</el-descriptions-item>
        <el-descriptions-item label="场景说明">{{ previewData.description || '无' }}</el-descriptions-item>
      </el-descriptions>
      <div class="preview-content">
        <div class="preview-label">模板内容：</div>
        <div class="preview-text">{{ previewData.content }}</div>
      </div>
      <div v-if="previewData.variables" class="preview-variables">
        <div class="preview-label">变量填写：</div>
        <el-form :inline="true" class="var-form">
          <el-form-item v-for="v in parsedVariables" :key="v" :label="v">
            <el-input v-model="varValues[v]" :data-testid="`ai-prompt-var-input-${v}`" :placeholder="'请输入 ' + v" size="small" />
          </el-form-item>
        </el-form>
        <el-button type="primary" size="small" data-testid="ai-prompt-render-button" @click="handleRender">渲染</el-button>
        <div v-if="renderedContent" class="rendered-content">
          <div class="preview-label">渲染结果：</div>
          <div class="preview-text rendered" data-testid="ai-prompt-rendered-content">{{ renderedContent }}</div>
        </div>
      </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  listPromptTemplate, listAllPromptTemplate, addPromptTemplate, editPromptTemplate, removePromptTemplate,
  renderPromptTemplate, promptCategoryOptions as fallbackPromptCategoryOptions,
  type AiPromptTemplate
} from '@/api/ai'
import { AI_PROMPT_CATEGORY_DICT, findDictLabel, loadDictOptions, SYS_NORMAL_DISABLE_DICT, type DictOption } from '@/utils/dict-options'
import { resolvePageResult } from '@/utils/page-result'
import { paginatePromptTemplates, shouldFallbackToAllPromptTemplates } from './listing'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const templateList = ref<AiPromptTemplate[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const submitLoading = ref(false)
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const renderedContent = ref('')
const promptCategoryOptions = ref<DictOption[]>([...fallbackPromptCategoryOptions])
const statusOptions = ref<DictOption[]>([
  { label: '正常', value: '0' },
  { label: '停用', value: '1' }
])

const queryParams = reactive({ templateName: '', category: '', status: '', pageNum: 1, pageSize: 10 })

const defaultForm = (): Partial<AiPromptTemplate> => ({
  templateId: undefined as any,
  templateName: '',
  category: 'system',
  content: '',
  variables: '',
  description: '',
  status: '0'
})

const form = reactive<any>(defaultForm())
const previewData = reactive<any>({})
const varValues = reactive<Record<string, string>>({})

const rules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

/**
 * Prompt 模板页优先复用系统字典，避免“分类、状态”在 AI 模块各页面各写一套。
 */
const getCategoryLabel = (v: string) => promptCategoryOptions.value.find(i => i.value === v)?.label || v
const getStatusLabel = (v: string) => findDictLabel(statusOptions.value, v, '正常')

const parsedVariables = computed(() => {
  try {
    return JSON.parse(previewData.variables || '[]')
  } catch {
    return []
  }
})

const loadFallbackPromptTemplates = async () => {
  const allRes = await listAllPromptTemplate()
  const allRows = resolvePageResult<AiPromptTemplate>((allRes as any).data).rows
  return paginatePromptTemplates(allRows, queryParams)
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listPromptTemplate(queryParams)
    let pageResult = resolvePageResult<AiPromptTemplate>((res as any).data)

    /**
     * 某些环境的分页接口仍可能返回空列表，但 `/all` 接口已经有完整模板。
     * 这里仅对“第一页且无筛选”的场景做兜底，避免用户看到整页空白。
     */
    if (shouldFallbackToAllPromptTemplates(queryParams, pageResult.rows)) {
      pageResult = await loadFallbackPromptTemplates()
    }

    templateList.value = pageResult.rows
    total.value = pageResult.total
  } catch (error) {
    if (shouldFallbackToAllPromptTemplates(queryParams, [])) {
      try {
        const pageResult = await loadFallbackPromptTemplates()
        templateList.value = pageResult.rows
        total.value = pageResult.total
        if (pageResult.rows.length > 0) {
          ElMessage.warning('分页接口暂时异常，已使用全部模板接口兜底展示')
          return
        }
      } catch (fallbackError) {
        console.error('Prompt 模板 /all 兜底加载失败:', fallbackError)
      }
    }

    templateList.value = []
    total.value = 0
    console.error('加载 Prompt 模板列表失败:', error)
    ElMessage.error('Prompt 模板列表加载失败，请检查权限或接口状态')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }

const handleAdd = () => { Object.assign(form, defaultForm()); dialogVisible.value = true }

const handleEdit = (row: AiPromptTemplate) => {
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.templateId) {
      await editPromptTemplate(form)
      ElMessage.success('修改成功')
    } else {
      await addPromptTemplate(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch { /* */ } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: AiPromptTemplate) => {
  try {
    await ElMessageBox.confirm(`确定删除模板"${row.templateName}"吗?`, '提示', { type: 'warning' })
    await removePromptTemplate(row.templateId!)
    ElMessage.success('删除成功')
    getList()
  } catch { /* */ }
}

const handlePreview = (row: AiPromptTemplate) => {
  Object.assign(previewData, row)
  renderedContent.value = ''
  // 清空变量值
  Object.keys(varValues).forEach(k => delete varValues[k])
  previewVisible.value = true
}

const handleRender = async () => {
  try {
    const res = await renderPromptTemplate(previewData.templateId, { ...varValues })
    renderedContent.value = res.data
  } catch {
    ElMessage.error('渲染失败')
  }
}

onMounted(async () => {
  const [categories, statuses] = await Promise.all([
    loadDictOptions(AI_PROMPT_CATEGORY_DICT, fallbackPromptCategoryOptions),
    loadDictOptions(SYS_NORMAL_DISABLE_DICT, statusOptions.value)
  ])
  promptCategoryOptions.value = categories
  statusOptions.value = statuses
  await getList()
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
.text-muted { color: #909399; }
.preview-content {
  margin-top: 16px;
  .preview-label { font-weight: bold; margin-bottom: 8px; color: #303133; }
  .preview-text {
    background: #f5f7fa; padding: 12px; border-radius: 4px; white-space: pre-wrap;
    line-height: 1.6; font-size: 14px;
  }
}
.preview-variables {
  margin-top: 16px;
  .preview-label { font-weight: bold; margin-bottom: 8px; color: #303133; }
  .var-form { margin-bottom: 8px; }
  .rendered-content { margin-top: 12px; }
  .rendered { border-left: 3px solid #409eff; }
}
</style>
