<template>
  <div class="app-container" data-testid="open-api-resource-page">
    <el-card shadow="never" class="search-form">
      <el-form :inline="true">
        <el-form-item label="接口名称">
          <el-input v-model="keyword" clearable placeholder="请输入接口名称或编码" @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>开放接口目录</span>
          <el-button v-if="canAdd" type="primary" :icon="Plus" @click="openAdd">新增接口</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredResources" data-testid="open-api-resource-table">
        <el-table-column label="接口名称" prop="resourceName" min-width="150" show-overflow-tooltip />
        <el-table-column label="接口编码" prop="resourceCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类" prop="category" width="110" show-overflow-tooltip />
        <el-table-column label="方法" prop="httpMethod" width="80" align="center" />
        <el-table-column label="开放路径" prop="path" min-width="240" show-overflow-tooltip />
        <el-table-column label="Scope" prop="scopeCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="发布状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="publishStatusTag(row.publishStatus)">{{ publishStatusLabel(row.publishStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-if="canEdit" :model-value="row.status === 0" @change="(value: any) => changeStatus(row, !!value)" />
            <el-tag v-else size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button v-if="canEdit" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canEdit && row.status === 0" type="warning" link @click="offline(row)">下线</el-button>
            <el-button v-if="canRemove" type="danger" link :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="接口名称" prop="resourceName"><el-input v-model="form.resourceName" /></el-form-item>
        <el-form-item label="接口编码" prop="resourceCode"><el-input v-model="form.resourceCode" /></el-form-item>
        <el-form-item label="接口分类" prop="category"><el-input v-model="form.category" placeholder="例如：教育目录" /></el-form-item>
        <el-form-item label="请求方法" prop="httpMethod">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option v-for="method in methods" :key="method" :label="method" :value="method" />
          </el-select>
        </el-form-item>
        <el-form-item label="开放路径" prop="path"><el-input v-model="form.path" placeholder="必须以 /open/api/ 开头" /></el-form-item>
        <el-form-item label="Scope" prop="scopeCode"><el-input v-model="form.scopeCode" /></el-form-item>
        <el-form-item label="接口说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.sensitivity" style="width: 100%">
            <el-option label="普通查询" value="NORMAL" />
            <el-option label="敏感数据" value="SENSITIVE" />
            <el-option label="控制操作" value="CONTROL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitResource">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="接口详情" size="70%" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <template v-else-if="selectedDetail">
        <el-descriptions :column="3" border class="resource-summary">
          <el-descriptions-item label="接口名称">{{ selectedDetail.resourceName }}</el-descriptions-item>
          <el-descriptions-item label="接口编码">{{ selectedDetail.resourceCode }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ selectedDetail.category || '—' }}</el-descriptions-item>
          <el-descriptions-item label="方法"><el-tag size="small">{{ selectedDetail.httpMethod }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="路径" :span="2"><code>{{ selectedDetail.path }}</code></el-descriptions-item>
          <el-descriptions-item label="Scope">{{ selectedDetail.scopeCode }}</el-descriptions-item>
          <el-descriptions-item label="启用状态"><el-tag size="small" :type="selectedDetail.status === 0 ? 'success' : 'info'">{{ selectedDetail.status === 0 ? '启用' : '停用' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="发布状态"><el-tag size="small" :type="publishStatusTag(selectedDetail.publishStatus)">{{ publishStatusLabel(selectedDetail.publishStatus) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="在线调测" :span="3">
            <el-button disabled title="需要应用授权后由 T07 在线调测模块实现">在线调测（需应用授权）</el-button>
            <span class="muted-text">暂未开放真实请求调测。</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-header">
          <span>版本管理</span>
          <el-button v-if="canEdit" type="primary" size="small" :icon="Plus" @click="openNewVersion">新建草稿版本</el-button>
        </div>
        <el-table :data="selectedDetail.versions || []" border>
          <el-table-column label="版本" prop="version" width="120" />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }"><el-tag size="small" :type="versionStatusTag(row.status)">{{ versionStatusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="发布时间" prop="publishedAt" min-width="170" />
          <el-table-column label="废弃时间" prop="deprecatedAt" min-width="170" />
          <el-table-column label="操作" min-width="240" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openVersionEditor(row, row.status !== VERSION_DRAFT)">{{ row.status === VERSION_DRAFT ? '编辑' : '查看' }}</el-button>
              <el-button v-if="canEdit && row.status === VERSION_DRAFT" type="success" link @click="publishVersion(row)">发布</el-button>
              <el-button v-if="canEdit && row.status === VERSION_PUBLISHED" type="warning" link @click="deprecateVersion(row)">废弃</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!selectedDetail.versions?.length" description="暂无版本，请先创建草稿版本" />
      </template>
    </el-drawer>

    <el-dialog v-model="versionDialogVisible" :title="versionDialogTitle" width="82%" top="5vh" destroy-on-close>
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="110px">
        <el-form-item label="版本号" prop="version">
          <el-input v-model="versionForm.version" :readonly="versionReadOnly" placeholder="例如：v1" style="max-width: 360px" />
        </el-form-item>
      </el-form>
      <el-tabs v-model="versionTab" class="version-tabs">
        <el-tab-pane label="OpenAPI JSON" name="openapi">
          <div class="json-toolbar"><span>必须包含当前资源的路径、HTTP 方法和 responses。</span><el-button size="small" :disabled="versionReadOnly" @click="formatJson('openapiSchema')">格式化</el-button></div>
          <el-input v-model="versionForm.openapiSchema" type="textarea" :rows="18" :readonly="versionReadOnly" class="json-editor" spellcheck="false" />
        </el-tab-pane>
        <el-tab-pane label="请求示例" name="request">
          <div class="json-toolbar"><span>可选，必须是 JSON 对象。</span><el-button size="small" :disabled="versionReadOnly" @click="formatJson('requestExample')">格式化</el-button></div>
          <el-input v-model="versionForm.requestExample" type="textarea" :rows="18" :readonly="versionReadOnly" class="json-editor" spellcheck="false" />
        </el-tab-pane>
        <el-tab-pane label="响应示例" name="response">
          <div class="json-toolbar"><span>可选，必须是 JSON 对象。</span><el-button size="small" :disabled="versionReadOnly" @click="formatJson('responseExamples')">格式化</el-button></div>
          <el-input v-model="versionForm.responseExamples" type="textarea" :rows="18" :readonly="versionReadOnly" class="json-editor" spellcheck="false" />
        </el-tab-pane>
        <el-tab-pane label="错误示例" name="error">
          <div class="json-toolbar"><span>可选，必须是 JSON 对象。</span><el-button size="small" :disabled="versionReadOnly" @click="formatJson('errorExamples')">格式化</el-button></div>
          <el-input v-model="versionForm.errorExamples" type="textarea" :rows="18" :readonly="versionReadOnly" class="json-editor" spellcheck="false" />
        </el-tab-pane>
        <el-tab-pane label="在线调测（需应用授权）" name="test" disabled />
      </el-tabs>
      <template #footer>
        <el-button @click="versionDialogVisible = false">关闭</el-button>
        <el-button v-if="!versionReadOnly" type="primary" :loading="versionSaving" @click="saveVersion">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  addOpenApiResource,
  changeOpenApiResourceStatus,
  createOpenApiResourceDraftVersion,
  deprecateOpenApiResourceVersion,
  getOpenApiResourceDetail,
  listOpenApiResource,
  offlineOpenApiResource,
  publishOpenApiResourceVersion,
  removeOpenApiResource,
  updateOpenApiResource,
  updateOpenApiResourceDraftVersion,
  type OpenApiResource,
  type OpenApiResourceDetail,
  type OpenApiResourceVersion
} from '@/api/open/resource'

const VERSION_DRAFT = 0
const VERSION_PUBLISHED = 1
type JsonField = 'openapiSchema' | 'requestExample' | 'responseExamples' | 'errorExamples'
type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const versionSaving = ref(false)
const detailLoading = ref(false)
const keyword = ref('')
const resources = ref<OpenApiResource[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const detailVisible = ref(false)
const selectedDetail = ref<OpenApiResourceDetail | null>(null)
const versionDialogVisible = ref(false)
const versionDialogTitle = ref('')
const versionReadOnly = ref(false)
const versionTab = ref('openapi')
const versionFormRef = ref<FormInstance>()
const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
const form = reactive<OpenApiResource>({ resourceName: '', resourceCode: '', category: '', httpMethod: 'GET', path: '/open/api/', scopeCode: '', description: '', sensitivity: 'NORMAL', status: 0, sort: 100 })
const versionForm = reactive({
  id: undefined as string | number | undefined,
  resourceId: undefined as string | number | undefined,
  version: '', openapiSchema: '', requestExample: '', responseExamples: '', errorExamples: ''
})

const canAdd = computed(() => userStore.hasPermission('open:api-resource:add'))
const canEdit = computed(() => userStore.hasPermission('open:api-resource:edit'))
const canRemove = computed(() => userStore.hasPermission('open:api-resource:remove'))
const filteredResources = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return resources.value
  return resources.value.filter(item => `${item.resourceName}${item.resourceCode}${item.path}`.toLowerCase().includes(value))
})
const rules: FormRules = {
  resourceName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  resourceCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
  category: [{ required: true, message: '请输入接口分类', trigger: 'blur' }],
  httpMethod: [{ required: true, message: '请选择请求方法', trigger: 'change' }],
  path: [{ required: true, message: '请输入开放路径', trigger: 'blur' }, { pattern: /^\/open\/api\//, message: '开放路径必须以 /open/api/ 开头', trigger: 'blur' }],
  scopeCode: [{ required: true, message: '请输入 Scope', trigger: 'blur' }]
}
const versionRules: FormRules = { version: [{ required: true, message: '请输入版本号', trigger: 'blur' }] }

onMounted(loadList)

async function loadList() {
  loading.value = true
  try { resources.value = (await listOpenApiResource()).data || [] } finally { loading.value = false }
}

function resetQuery() { keyword.value = ''; loadList() }

function resetForm() {
  Object.assign(form, { id: undefined, resourceName: '', resourceCode: '', category: '', httpMethod: 'GET', path: '/open/api/', scopeCode: '', description: '', sensitivity: 'NORMAL', status: 0, sort: 100 })
}

function openAdd() { resetForm(); dialogTitle.value = '新增接口'; dialogVisible.value = true }
function openEdit(row: OpenApiResource) { Object.assign(form, row); dialogTitle.value = '编辑接口'; dialogVisible.value = true }

async function submitResource() {
  if (!formRef.value || !(await formRef.value.validate())) return
  saving.value = true
  try {
    if (form.id) await updateOpenApiResource(form)
    else await addOpenApiResource(form)
    ElMessage.success('保存成功'); dialogVisible.value = false; await loadList()
  } finally { saving.value = false }
}

async function changeStatus(row: OpenApiResource, enabled: boolean) {
  await changeOpenApiResourceStatus(row.id!, enabled ? 0 : 1)
  row.status = enabled ? 0 : 1
  ElMessage.success(enabled ? '已启用' : '已停用')
  if (!enabled) await loadList()
}

async function offline(row: OpenApiResource) {
  await ElMessageBox.confirm(`确认下线接口“${row.resourceName}”？下线后将保留版本历史。`, '提示', { type: 'warning' })
  await offlineOpenApiResource(row.id!); ElMessage.success('已下线'); await loadList()
}

async function remove(row: OpenApiResource) {
  await ElMessageBox.confirm(`确认删除接口“${row.resourceName}”？`, '提示', { type: 'warning' })
  await removeOpenApiResource(row.id!); ElMessage.success('删除成功'); await loadList()
}

async function openDetail(row: OpenApiResource) {
  detailVisible.value = true; selectedDetail.value = null; detailLoading.value = true
  try { selectedDetail.value = (await getOpenApiResourceDetail(row.id!)).data } finally { detailLoading.value = false }
}

function openNewVersion() {
  if (!selectedDetail.value) return
  const detail = selectedDetail.value
  const method = detail.httpMethod.toLowerCase()
  const schema = { openapi: '3.0.3', info: { title: detail.resourceName, version: '1.0.0' }, paths: { [detail.path]: { [method]: { responses: { '200': { description: '成功' } } } } } }
  Object.assign(versionForm, { id: undefined, resourceId: detail.id, version: nextVersion(detail.versions || []), openapiSchema: JSON.stringify(schema, null, 2), requestExample: '', responseExamples: '', errorExamples: '' })
  versionDialogTitle.value = '新建草稿版本'; versionReadOnly.value = false; versionTab.value = 'openapi'; versionDialogVisible.value = true
}

function nextVersion(versions: OpenApiResourceVersion[]) {
  const used = new Set(versions.map(item => item.version)); let index = 1
  while (used.has(`v${index}`)) index++
  return `v${index}`
}

function openVersionEditor(version: OpenApiResourceVersion, readOnly: boolean) {
  Object.assign(versionForm, { id: version.id, resourceId: version.resourceId || selectedDetail.value?.id, version: version.version || '', openapiSchema: jsonText(version.openapiSchema), requestExample: jsonText(version.requestExample), responseExamples: jsonText(version.responseExamples), errorExamples: jsonText(version.errorExamples) })
  versionDialogTitle.value = readOnly ? `查看版本 ${version.version}` : `编辑草稿 ${version.version}`; versionReadOnly.value = readOnly; versionTab.value = 'openapi'; versionDialogVisible.value = true
}

function jsonText(value?: Record<string, unknown>) { return value ? JSON.stringify(value, null, 2) : '' }

function formatJson(field: JsonField) {
  const value = versionForm[field].trim()
  if (!value) return
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    versionForm[field] = JSON.stringify(parsed, null, 2)
  } catch { ElMessage.error('当前内容不是合法 JSON 对象') }
}

function parseJson(value: string, label: string, required = false) {
  if (!value.trim()) { if (required) ElMessage.error(`${label}不能为空`); return undefined }
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    return parsed as Record<string, unknown>
  } catch { ElMessage.error(`${label}必须是合法 JSON 对象`); return undefined }
}

async function saveVersion() {
  if (!selectedDetail.value || !versionFormRef.value || !(await versionFormRef.value.validate())) return
  const openapiSchema = parseJson(versionForm.openapiSchema, 'OpenAPI Schema', true)
  if (!openapiSchema) return
  const requestExample = parseJson(versionForm.requestExample, '请求示例')
  if (versionForm.requestExample.trim() && !requestExample) return
  const responseExamples = parseJson(versionForm.responseExamples, '响应示例')
  if (versionForm.responseExamples.trim() && !responseExamples) return
  const errorExamples = parseJson(versionForm.errorExamples, '错误示例')
  if (versionForm.errorExamples.trim() && !errorExamples) return
  const payload: OpenApiResourceVersion = { id: versionForm.id, resourceId: selectedDetail.value.id, version: versionForm.version.trim(), openapiSchema, requestExample, responseExamples, errorExamples }
  versionSaving.value = true
  try {
    if (versionForm.id) await updateOpenApiResourceDraftVersion(payload)
    else await createOpenApiResourceDraftVersion(selectedDetail.value.id!, payload)
    ElMessage.success('草稿保存成功'); versionDialogVisible.value = false; await refreshDetail()
  } finally { versionSaving.value = false }
}

async function publishVersion(version: OpenApiResourceVersion) {
  await ElMessageBox.confirm(`确认发布版本“${version.version}”？发布后正文不可编辑。`, '提示', { type: 'warning' })
  await publishOpenApiResourceVersion(version.id!); ElMessage.success('版本发布成功'); await refreshDetail()
}

async function deprecateVersion(version: OpenApiResourceVersion) {
  await ElMessageBox.confirm(`确认废弃版本“${version.version}”？`, '提示', { type: 'warning' })
  await deprecateOpenApiResourceVersion(version.id!); ElMessage.success('版本已废弃'); await refreshDetail()
}

async function refreshDetail() {
  if (!selectedDetail.value) return
  selectedDetail.value = (await getOpenApiResourceDetail(selectedDetail.value.id!)).data
  await loadList()
}

function publishStatusLabel(status?: number) { return ({ 0: '草稿', 1: '待审核', 2: '已发布', 3: '已下线' } as Record<number, string>)[status ?? 0] || '未知' }
function publishStatusTag(status?: number): TagType { return ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' } as Record<number, TagType>)[status ?? 0] || 'info' }
function versionStatusLabel(status?: number) { return ({ 0: '草稿', 1: '已发布', 2: '已废弃' } as Record<number, string>)[status ?? 0] || '未知' }
function versionStatusTag(status?: number): TagType { return ({ 0: 'info', 1: 'success', 2: 'warning' } as Record<number, TagType>)[status ?? 0] || 'info' }
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header, .section-header, .json-toolbar { display: flex; align-items: center; justify-content: space-between; }
.search-form { margin-bottom: 16px; }
.resource-summary { margin-bottom: 22px; }
.section-header { margin: 8px 0 12px; font-size: 16px; font-weight: 600; }
.json-toolbar { margin-bottom: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.json-editor :deep(textarea) { font-family: Consolas, Monaco, monospace; line-height: 1.5; }
.muted-text { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
code { color: var(--el-color-primary); }
</style>
