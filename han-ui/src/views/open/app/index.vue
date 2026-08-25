<template>
  <div class="app-container" data-testid="open-app-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="queryParams.appName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="应用类型" prop="appType">
          <el-select v-model="queryParams.appType" placeholder="请选择" clearable style="width: 140px">
            <el-option label="Web应用" value="web" />
            <el-option label="移动应用" value="mobile" />
            <el-option label="服务端" value="server" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="生命周期" prop="lifecycleStatus">
          <el-select v-model="queryParams.lifecycleStatus" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in lifecycleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" data-testid="open-app-search-button" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" data-testid="open-app-reset-button" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>开放应用列表</span>
          <el-button v-if="canAdd" type="primary" :icon="Plus" data-testid="open-app-add-button" @click="handleAdd">新增应用</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="appList" :empty-text="canList ? '暂无应用数据' : '无权限查看应用数据'" data-testid="open-app-table">
        <el-table-column label="应用名称" prop="appName" min-width="180" show-overflow-tooltip />
        <el-table-column label="AppKey" prop="appKey" min-width="250" show-overflow-tooltip />
        <el-table-column label="所属厂商" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.vendorName || row.vendorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="应用类型" prop="appType" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.appType === 'web'">Web</el-tag>
            <el-tag v-else-if="row.appType === 'mobile'" type="success">移动端</el-tag>
            <el-tag v-else type="info">{{ row.appType || '服务端' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联系人" prop="contactName" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-if="canEdit"
              :model-value="row.status === 0"
              :data-testid="`open-app-status-switch-${row.appId}`"
              @change="(val: any) => handleStatusChange(row, !!val)"
            />
            <el-tag v-else size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生命周期" width="120" align="center">
          <template #default="{ row }"><el-tag :type="lifecycleTagType(row.lifecycleStatus)">{{ lifecycleLabel(row.lifecycleStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="环境策略" prop="environmentPolicy" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ environmentPolicyLabel(row.environmentPolicy) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button v-if="canEdit" type="primary" link :icon="Edit" :data-testid="`open-app-edit-button-${row.appId}`" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canRemove" type="danger" link :icon="Delete" :data-testid="`open-app-delete-button-${row.appId}`" @click="handleDelete(row)">删除</el-button>
            <el-button v-if="canEdit && lifecycleAction(row)" type="success" link @click="handleLifecycleAction(row)">{{ lifecycleAction(row)?.label }}</el-button>
            <el-button v-if="canLifecycleReview && needsLifecycleReview(row)" type="warning" link @click="openLifecycleReview(row)">审核</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="canList ? '暂无应用数据' : '无权限查看应用数据'" :image-size="80" /></template>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="55%" class="dialog-md" destroy-on-close data-testid="open-app-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="form.appName" placeholder="请输入应用名称" data-testid="open-app-form-name" />
        </el-form-item>
        <el-form-item label="应用类型" prop="appType">
          <el-select v-model="form.appType" placeholder="请选择" data-testid="open-app-form-type">
            <el-option label="Web应用" value="web" />
            <el-option label="移动应用" value="mobile" />
            <el-option label="服务端" value="server" />
          </el-select>
        </el-form-item>
        <el-form-item label="应用描述">
          <el-input v-model="form.appDesc" type="textarea" placeholder="请输入应用描述" data-testid="open-app-form-desc" />
        </el-form-item>
        <el-form-item label="回调地址">
          <el-input v-model="redirectUrisStr" type="textarea" placeholder="多个地址用换行分隔" :rows="3" data-testid="open-app-form-redirect-uris" />
        </el-form-item>
        <el-form-item label="身份授权范围">
          <el-checkbox-group v-model="selectedProtocolScopes">
            <el-checkbox v-for="scope in identityScopeOptions" :key="scope.value" :label="scope.value">{{ scope.label }}</el-checkbox>
          </el-checkbox-group>
          <div class="form-hint">仅用于用户授权后的 userinfo；普通服务调用无需选择。</div>
        </el-form-item>
        <el-form-item label="授权接口">
          <div class="api-resource-list">
            <div v-for="(resources, category) in apiResourcesByCategory" :key="category" class="api-resource-group">
              <div class="api-resource-category">{{ category }}</div>
              <el-checkbox-group v-model="selectedApiResourceIds">
                <el-checkbox v-for="resource in resources" :key="resource.id" :label="resource.id">
                  <span>{{ resource.resourceName }}</span>
                  <el-tag size="small" effect="plain" class="api-resource-method">{{ resource.httpMethod }}</el-tag>
                  <span class="api-resource-path">{{ resource.path }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <el-empty v-if="!apiResources.length" description="暂无可授权接口" :image-size="60" />
          </div>
        </el-form-item>
        <el-form-item v-if="needsSchoolScope" label="授权学校" prop="schoolIds">
          <el-select v-model="form.schoolIds" multiple filterable allow-create default-first-option clearable placeholder="选择学校或输入平台学校ID" style="width: 100%">
            <el-option v-for="school in schools" :key="school.value" :label="school.label" :value="school.value" />
          </el-select>
          <div class="form-hint">视频课堂及教育目录接口必须限定学校；可选择校内学校，也可直接输入平台学校ID。</div>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人" data-testid="open-app-form-contact-name" />
        </el-form-item>
        <el-form-item label="AccessToken有效期">
          <el-input-number v-model="form.accessTokenTtl" :min="60" :step="3600" data-testid="open-app-form-access-token-ttl" />
          <span class="form-hint">秒</span>
        </el-form-item>
        <el-form-item label="RefreshToken有效期">
          <el-input-number v-model="form.refreshTokenTtl" :min="60" :step="3600" data-testid="open-app-form-refresh-token-ttl" />
          <span class="form-hint">秒</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status" data-testid="open-app-form-status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-testid="open-app-dialog-cancel" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" data-testid="open-app-dialog-submit" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lifecycleReviewVisible" title="审核应用开通申请" width="500px" destroy-on-close>
      <el-form ref="lifecycleReviewFormRef" :model="lifecycleReviewForm" :rules="lifecycleReviewRules" label-width="90px">
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="lifecycleReviewForm.status"><el-radio :value="1">通过</el-radio><el-radio :value="2">驳回</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="审核说明"><el-input v-model="lifecycleReviewForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="lifecycleReviewVisible = false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="submitLifecycleReview">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listOpenApp, getOpenApp, addOpenApp, updateOpenApp, deleteOpenApp, changeAppStatus, changeAppLifecycleStatus, submitAppLifecycleApply, reviewAppLifecycleApply, listOpenApiResources, type OpenApp, type OpenAppForm, type OpenApiResource } from '@/api/open/app'
import { listOrganizationTree } from '@/api/education'
import { schoolOptions as flattenSchoolOptions } from '@/utils/education-school-tree'
import { useUserStore } from '@/stores/user'
import { loadDictOptions, OPEN_IDENTITY_SCOPE_DICT, type DictOption } from '@/utils/dict-options'

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

const userStore = useUserStore()
const canList = computed(() => userStore.hasPermission('open:app:list'))
const canAdd = computed(() => userStore.hasPermission('open:app:add'))
const canEdit = computed(() => userStore.hasPermission('open:app:edit'))
const canRemove = computed(() => userStore.hasPermission('open:app:remove'))
const canLifecycleReview = computed(() => userStore.hasPermission('open:grant:review'))

const loading = ref(false)
const submitLoading = ref(false)
const appList = ref<OpenApp[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ appName: '', appType: '' as string | undefined, status: undefined as number | undefined, lifecycleStatus: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<OpenAppForm>({ appName: '', appDesc: '', appType: 'web', redirectUris: [], scopes: [], grantTypes: ['authorization_code', 'refresh_token'], schoolIds: [], contactName: '', accessTokenTtl: 7200, refreshTokenTtl: 604800, status: 0 })
const schools = ref<Array<{ label: string; value: string | number }>>([])
const apiResources = ref<OpenApiResource[]>([])
const identityScopeOptions = ref<DictOption[]>([])
const selectedProtocolScopes = ref<string[]>([])
const selectedApiResourceIds = ref<Array<string | number>>([])
const lifecycleOptions = [
  { value: 0, label: '草稿' }, { value: 1, label: '待审核' }, { value: 2, label: '沙箱开通' },
  { value: 3, label: '调测中' }, { value: 4, label: '生产待审' }, { value: 5, label: '生产开通' },
  { value: 6, label: '暂停' }, { value: 7, label: '撤销' }
]
const lifecycleReviewVisible = ref(false)
const lifecycleReviewFormRef = ref<FormInstance>()
const lifecycleReviewForm = reactive({ appId: undefined as string | number | undefined, status: 1, reason: '' })
const lifecycleReviewRules: FormRules = { status: [{ required: true, message: '请选择审核结果', trigger: 'change' }] }

const redirectUrisStr = computed({
  get: () => (form.redirectUris || []).join('\n'),
  set: (val: string) => { form.redirectUris = val.split('\n').map(s => s.trim()).filter(s => s) }
})

const needsSchoolScope = computed(() => (form.scopes || []).some(scope =>
  scope.startsWith('classroom.') || scope === 'edu.teacher.read' || scope === 'edu.student.read' || scope === 'edu.device.read'))

const apiResourcesByCategory = computed<Record<string, OpenApiResource[]>>(() => apiResources.value.reduce((groups, resource) => {
  const category = resource.category || '其他接口'
  groups[category] = groups[category] || []
  groups[category].push(resource)
  return groups
}, {} as Record<string, OpenApiResource[]>))

watch([selectedProtocolScopes, selectedApiResourceIds, apiResources], () => {
  const selectedScopes = apiResources.value
    .filter(resource => selectedApiResourceIds.value.some(id => String(id) === String(resource.id)))
    .map(resource => resource.scopeCode)
  form.scopes = [...new Set([...selectedProtocolScopes.value, ...selectedScopes])]
}, { deep: true })

watch(() => form.appType, (appType) => {
  form.grantTypes = appType === 'server' ? ['client_credentials'] : ['authorization_code', 'refresh_token']
})

const rules: FormRules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  appType: [{ required: true, message: '请选择应用类型', trigger: 'change' }]
}

onMounted(async () => {
  identityScopeOptions.value = await loadDictOptions(OPEN_IDENTITY_SCOPE_DICT, [
    { label: '用户唯一标识（openid）', value: 'openid' },
    { label: '用户基础资料（profile）', value: 'profile' }
  ])
  await loadSchools(); await loadApiResources(); await getList()
})

async function loadSchools() {
  try {
    const response = await listOrganizationTree(0)
    schools.value = flattenSchoolOptions(response.data || []).map(item => ({
      label: `${item.schoolName}（${item.schoolCode}）`, value: item.id
    }))
  } catch (error) { schools.value = []; notifyError(error, '加载学校范围失败') }
}

async function loadApiResources() {
  try {
    const response = await listOpenApiResources()
    apiResources.value = ((response as any).data || []).filter((resource: OpenApiResource) => resource.status === 0)
  } catch (error) { apiResources.value = []; notifyError(error, '加载接口目录失败') }
}

async function getList() {
  if (!canList.value) { appList.value = []; total.value = 0; return }
  loading.value = true
  try {
    const res = await listOpenApp(queryParams)
    const data = (res as any).data
    appList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch (error) { appList.value = []; total.value = 0; notifyError(error, '加载应用列表失败') } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.appName = ''; queryParams.appType = undefined; queryParams.status = undefined; queryParams.lifecycleStatus = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增应用'
  dialogVisible.value = true
}

async function handleEdit(row: OpenApp) {
  resetForm()
  dialogTitle.value = '编辑应用'
  try {
    const res = await getOpenApp(row.appId)
    const d = (res as any).data
    Object.assign(form, { appId: d.appId, vendorId: d.vendorId, appName: d.appName, appDesc: d.appDesc, appType: d.appType, redirectUris: d.redirectUris || [], scopes: d.scopes || [], grantTypes: d.grantTypes || [], schoolIds: d.schoolIds || [], contactName: d.contactName, accessTokenTtl: d.accessTokenTtl, refreshTokenTtl: d.refreshTokenTtl, status: d.status })
    selectedProtocolScopes.value = (d.scopes || []).filter((scope: string) => scope === 'openid' || scope === 'profile')
    selectedApiResourceIds.value = apiResources.value.filter(resource => (d.scopes || []).includes(resource.scopeCode)).map(resource => resource.id)
  } catch (error) { notifyError(error, '加载应用详情失败') }
  dialogVisible.value = true
}

async function handleDelete(row: OpenApp) {
  try {
    await ElMessageBox.confirm(`确认删除应用"${row.appName}"？`, '提示', { type: 'warning' })
    await deleteOpenApp(row.appId)
    ElMessage.success('删除成功')
    getList()
  } catch (error) { if (!isCancel(error)) notifyError(error, '删除应用失败') }
}

async function handleStatusChange(row: OpenApp, val: boolean) {
  const newStatus = val ? 0 : 1
  try {
    await changeAppStatus(row.appId, newStatus)
    ElMessage.success(val ? '启用成功' : '停用成功')
    getList()
  } catch (error) { if (!isCancel(error)) notifyError(error, '变更应用状态失败') }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (needsSchoolScope.value && !(form.schoolIds || []).length) {
    ElMessage.warning('视频课堂或教育目录接口必须选择授权学校')
    return
  }
  submitLoading.value = true
  try {
    if (form.appId) {
      await updateOpenApp(form)
      ElMessage.success('修改成功')
    } else {
      await addOpenApp(form)
      ElMessage.success('应用草稿已创建，请提交沙箱审核')
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    notifyError(error, form.appId ? '修改应用失败' : '创建应用失败')
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.appId = undefined; form.appName = ''; form.appDesc = ''; form.appType = 'web'; form.redirectUris = []; form.scopes = []; form.grantTypes = ['authorization_code', 'refresh_token']; form.schoolIds = []; form.contactName = ''; form.accessTokenTtl = 7200; form.refreshTokenTtl = 604800; form.status = 0
  selectedProtocolScopes.value = []
  selectedApiResourceIds.value = []
}

function lifecycleAction(row: OpenApp) {
  if (row.lifecycleStatus === 0) return { label: '提交沙箱审核', type: 'apply' as const }
  if (row.lifecycleStatus === 2) return { label: '进入调测', type: 'testing' as const }
  if (row.lifecycleStatus === 3) return { label: '提交生产审核', type: 'apply' as const }
  return null
}

function needsLifecycleReview(row: OpenApp) { return row.lifecycleStatus === 1 || row.lifecycleStatus === 4 }

async function handleLifecycleAction(row: OpenApp) {
  const action = lifecycleAction(row)
  if (!action) return
  try {
    if (action.type === 'apply') {
      await ElMessageBox.confirm(`确认${action.label}？`, '应用开通申请', { type: 'warning' })
      await submitAppLifecycleApply(row.appId)
      ElMessage.success('已提交，等待平台审核')
    } else {
      await changeAppLifecycleStatus(row.appId, 3)
      ElMessage.success('已进入调测')
    }
    await getList()
  } catch (error) { if (!isCancel(error)) notifyError(error, '提交应用开通申请失败') }
}

function openLifecycleReview(row: OpenApp) {
  Object.assign(lifecycleReviewForm, { appId: row.appId, status: 1, reason: '' })
  lifecycleReviewVisible.value = true
}

async function submitLifecycleReview() {
  if (!(await lifecycleReviewFormRef.value?.validate()) || !lifecycleReviewForm.appId) return
  submitLoading.value = true
  try {
    await reviewAppLifecycleApply(lifecycleReviewForm.appId, lifecycleReviewForm.status, lifecycleReviewForm.reason || undefined)
    lifecycleReviewVisible.value = false
    ElMessage.success('审核完成')
    await getList()
  } catch (error) { notifyError(error, '审核应用开通申请失败') } finally { submitLoading.value = false }
}

function lifecycleLabel(status?: number) { return lifecycleOptions.find(item => item.value === status)?.label || '未知' }
function lifecycleTagType(status?: number): TagType {
  if (status === 5) return 'success'
  if (status === 6) return 'warning'
  if (status === 7) return 'danger'
  if (status === 1 || status === 4) return 'warning'
  return 'primary'
}
function environmentPolicyLabel(value?: string) {
  return ({ SANDBOX_FIRST: '先沙箱', PROD_ONLY: '仅生产', ALL: '全部环境' } as Record<string, string>)[value || ''] || value || '-'
}
function isCancel(error: unknown) { return error === 'cancel' || (error as any)?.message === 'cancel' }
function notifyError(error: unknown, fallback: string) {
  const message = error instanceof Error && error.message && error.message !== '请求失败' ? error.message : fallback
  ElMessage.error(message)
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
.form-hint { margin-left: 8px; color: #999; font-size: 12px; }
.credential-warning { margin-bottom: 20px; }
.credential-form :deep(.el-input-group__append) { padding: 0; }
.api-resource-list { width: 100%; max-height: 220px; overflow: auto; padding: 8px 12px; border: 1px solid var(--el-border-color); border-radius: 4px; }
.api-resource-group + .api-resource-group { margin-top: 10px; }
.api-resource-category { margin-bottom: 4px; color: var(--el-text-color-primary); font-weight: 600; }
.api-resource-method { margin-left: 8px; }
.api-resource-path { margin-left: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
