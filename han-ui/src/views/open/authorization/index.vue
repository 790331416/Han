<template>
  <div class="app-container" data-testid="open-authorization-page">
    <el-tabs v-model="activeTab" type="card">
      <el-tab-pane label="接口授权申请" name="requests">
        <el-card shadow="never" class="search-form">
          <el-form :model="requestQuery" :inline="true">
            <el-form-item label="应用">
              <el-select v-model="requestQuery.appId" clearable filterable placeholder="全部应用" style="width: 220px">
                <el-option v-for="app in apps" :key="app.appId" :label="`${app.appName}（${app.appId}）`" :value="app.appId" />
              </el-select>
            </el-form-item>
            <el-form-item label="环境">
              <el-select v-model="requestQuery.environment" clearable placeholder="全部" style="width: 130px">
                <el-option label="沙箱" value="SANDBOX" /><el-option label="生产" value="PROD" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="requestQuery.status" clearable placeholder="全部" style="width: 130px">
                <el-option v-for="item in requestStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadRequests">查询</el-button>
              <el-button :icon="Refresh" @click="resetRequestQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <el-table v-loading="requestLoading" :data="requests" :empty-text="canGrantQuery ? '暂无授权申请' : '无权限查看授权申请'">
            <el-table-column label="申请ID" prop="id" width="100" />
            <el-table-column label="应用" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.appName || row.appId }}</template>
            </el-table-column>
            <el-table-column label="环境" width="90" align="center"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="申请类型" width="100" align="center"><template #default="{ row }">{{ requestTypeLabel(row.requestType) }}</template></el-table-column>
            <el-table-column label="申请理由" prop="reason" min-width="220" show-overflow-tooltip />
            <el-table-column label="申请内容" min-width="260" show-overflow-tooltip><template #default="{ row }">{{ requestDataSummary(row.requestData) }}</template></el-table-column>
            <el-table-column label="状态" width="100" align="center"><template #default="{ row }"><el-tag :type="requestStatusTagType(row.status)">{{ requestStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="申请时间" min-width="170"><template #default="{ row }">{{ formatDate(row.createTime) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }"><el-button v-if="canGrantReview && row.status === 0" type="warning" link @click="openReview(row)">审核</el-button></template>
            </el-table-column>
            <template #empty><el-empty :description="canGrantQuery ? '暂无授权申请' : '无权限查看授权申请'" :image-size="80" /></template>
          </el-table>
          <el-pagination v-model:current-page="requestQuery.pageNum" v-model:page-size="requestQuery.pageSize" :page-sizes="[10, 20, 50]" :total="requestTotal" layout="total, sizes, prev, pager, next, jumper" class="pagination" @size-change="loadRequests" @current-change="loadRequests" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="接口授权台账" name="grants">
        <el-card shadow="never" class="search-form">
          <el-form :inline="true">
            <el-form-item label="应用">
              <el-select v-model="selectedGrantAppId" clearable filterable placeholder="请选择应用" style="width: 260px">
                <el-option v-for="app in apps" :key="app.appId" :label="`${app.appName}（${app.appId}）`" :value="app.appId" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" :icon="Search" @click="loadGrants">查询</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <el-table v-loading="grantLoading" :data="grants" empty-text="请选择应用并查询授权">
            <el-table-column label="资源" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.resourceName || row.resourceCode || row.resourceId }}</template></el-table-column>
            <el-table-column label="环境" width="90" align="center"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="Scope" prop="scopes" min-width="180" show-overflow-tooltip />
            <el-table-column label="数据范围" prop="dataScope" min-width="220" show-overflow-tooltip />
            <el-table-column label="配额" width="90" align="center"><template #default="{ row }">{{ row.quota || '不限' }}</template></el-table-column>
            <el-table-column label="过期时间" min-width="170"><template #default="{ row }">{{ row.expiresAt ? formatDate(row.expiresAt) : '永久' }}</template></el-table-column>
            <el-table-column label="状态" width="100" align="center"><template #default="{ row }"><el-tag :type="grantStatusTagType(row.status)">{{ grantStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button v-if="canGrantRevoke && row.status === 1" type="danger" link @click="revokeGrant(row)">撤销</el-button></template></el-table-column>
            <template #empty><el-empty description="请选择应用并查询授权" :image-size="80" /></template>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="应用凭证（唯一入口）" name="credentials">
        <el-card shadow="never" class="search-form">
          <el-form :inline="true">
            <el-form-item label="应用">
              <el-select v-model="credentialAppId" clearable filterable placeholder="全部应用" style="width: 260px" @change="loadCredentials">
                <el-option v-for="app in apps" :key="app.appId" :label="`${app.appName}（${app.appId}）`" :value="app.appId" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadCredentials">查询</el-button>
              <el-button v-if="canCredentialManage" type="success" :icon="Plus" @click="openGenerate">生成凭证</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <el-alert title="凭证按沙箱、生产环境分别生成和轮换；应用管理不再提供另一套重置密钥。" type="info" :closable="false" style="margin-bottom: 16px" />
          <el-table v-loading="credentialLoading" :data="credentials" :empty-text="canCredentialQuery ? '暂无应用凭证' : '无权限查看应用凭证'">
            <el-table-column label="Client ID" prop="clientId" min-width="220" show-overflow-tooltip />
            <el-table-column label="应用ID" prop="appId" width="100" />
            <el-table-column label="环境" width="90" align="center"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="状态" width="100" align="center"><template #default="{ row }"><el-tag :type="credentialStatusTagType(row.status)">{{ credentialStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="过期时间" min-width="170"><template #default="{ row }">{{ row.expireAt ? formatDate(row.expireAt) : '永久' }}</template></el-table-column>
            <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatDate(row.createTime) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button v-if="canCredentialManage && row.status === 0" type="warning" link @click="rotateCredential(row)">轮换</el-button></template></el-table-column>
            <template #empty><el-empty :description="canCredentialQuery ? '暂无应用凭证' : '无权限查看应用凭证'" :image-size="80" /></template>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reviewVisible" title="审核授权申请" width="520px" destroy-on-close>
      <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="90px">
        <el-form-item label="申请内容"><el-input :model-value="reviewForm.requestData" type="textarea" :rows="8" readonly /></el-form-item>
        <el-form-item label="审核结果" prop="status"><el-radio-group v-model="reviewForm.status"><el-radio :value="1">通过</el-radio><el-radio :value="2">驳回</el-radio></el-radio-group></el-form-item>
        <el-form-item label="审核说明"><el-input v-model="reviewForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewVisible = false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="submitReview">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="generateVisible" title="生成应用凭证" width="460px" destroy-on-close>
      <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="90px">
        <el-form-item label="应用" prop="appId"><el-select v-model="generateForm.appId" filterable placeholder="请选择应用" style="width: 100%"><el-option v-for="app in apps" :key="app.appId" :label="`${app.appName}（${app.appId}）`" :value="app.appId" /></el-select></el-form-item>
        <el-form-item label="环境" prop="environment"><el-radio-group v-model="generateForm.environment"><el-radio value="SANDBOX">沙箱</el-radio><el-radio value="PROD">生产</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="generateVisible = false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="submitGenerate">生成</el-button></template>
    </el-dialog>

    <el-dialog v-model="secretVisible" :title="secretTitle" width="560px" destroy-on-close @closed="clearSecret">
      <el-alert title="Client Secret 仅在本次生成或轮换后显示一次，请立即保存。查询列表不会返回密钥。" type="warning" :closable="false" show-icon class="secret-warning" />
      <el-form label-width="120px">
        <el-form-item label="Client ID"><el-input :model-value="secretCredential.clientId || ''" readonly><template #append><el-button :icon="CopyDocument" @click="copySecret(secretCredential.clientId || '', 'Client ID')">复制</el-button></template></el-input></el-form-item>
        <el-form-item label="Client Secret"><el-input :model-value="secretCredential.clientSecret || ''" type="password" show-password readonly autocomplete="off"><template #append><el-button :icon="CopyDocument" @click="copySecret(secretCredential.clientSecret || '', 'Client Secret')">复制</el-button></template></el-input></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="secretVisible = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CopyDocument, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { formatDate } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { listOpenApp, type OpenApp } from '@/api/open/app'
import {
  generateAppCredential,
  listAppCredentials,
  listAppGrants,
  listAuthorizationRequests,
  revokeAppGrant,
  reviewAuthorizationRequest,
  rotateAppCredential,
  type OpenAuthorizationRequest,
  type OpenAuthorizationRequestQuery,
  type OpenCredential,
  type OpenCredentialSecret,
  type OpenGrant
} from '@/api/open/authorization'

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'
const userStore = useUserStore()
const canGrantQuery = computed(() => userStore.hasPermission('open:grant:query'))
const canGrantReview = computed(() => userStore.hasPermission('open:grant:review'))
const canGrantRevoke = computed(() => userStore.hasPermission('open:grant:revoke'))
const canCredentialQuery = computed(() => userStore.hasPermission('open:credential:query'))
const canCredentialManage = computed(() => userStore.hasPermission('open:credential:manage'))
const activeTab = ref('requests')
const apps = ref<OpenApp[]>([])
const requestLoading = ref(false)
const requests = ref<OpenAuthorizationRequest[]>([])
const requestTotal = ref(0)
const requestQuery = reactive<OpenAuthorizationRequestQuery>({ pageNum: 1, pageSize: 10, appId: undefined, environment: undefined, status: undefined })
const requestStatusOptions = [{ value: 0, label: '待审核' }, { value: 1, label: '已通过' }, { value: 2, label: '已驳回' }, { value: 3, label: '已撤销' }]
const grantLoading = ref(false)
const grants = ref<OpenGrant[]>([])
const selectedGrantAppId = ref<string | number>()
const credentialLoading = ref(false)
const credentials = ref<OpenCredential[]>([])
const credentialAppId = ref<string | number>()
const submitLoading = ref(false)

const reviewVisible = ref(false)
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive({ id: '', requestData: '', status: 1, reason: '' })
const reviewRules: FormRules = { status: [{ required: true, message: '请选择审核结果', trigger: 'change' }] }
const generateVisible = ref(false)
const generateFormRef = ref<FormInstance>()
const generateForm = reactive<{ appId?: string | number; environment: string }>({ appId: undefined, environment: 'SANDBOX' })
const generateRules: FormRules = { appId: [{ required: true, message: '请选择应用', trigger: 'change' }], environment: [{ required: true, message: '请选择环境', trigger: 'change' }] }
const secretVisible = ref(false)
const secretTitle = ref('应用凭证')
const secretCredential = reactive<Partial<OpenCredentialSecret>>({})

onMounted(async () => { await loadApps(); await Promise.all([loadRequests(), loadGrants(), loadCredentials()]) })

async function loadApps() {
  try {
    const response = await listOpenApp({ pageNum: 1, pageSize: 200 })
    const data = (response as any).data
    apps.value = data?.rows || data?.records || []
    const firstAppId = apps.value[0]?.appId
    if (firstAppId) {
      selectedGrantAppId.value ||= firstAppId
      credentialAppId.value ||= firstAppId
    }
  } catch (error) { apps.value = []; notifyError(error, '加载应用列表失败') }
}

async function loadRequests() {
  if (!canGrantQuery.value) return
  requestLoading.value = true
  try {
    const response = await listAuthorizationRequests(requestQuery)
    const data = (response as any).data
    requests.value = data?.rows || data?.records || []
    requestTotal.value = data?.total || 0
  } catch (error) { requests.value = []; requestTotal.value = 0; notifyError(error, '加载授权申请失败') }
  finally { requestLoading.value = false }
}

function resetRequestQuery() { requestQuery.appId = undefined; requestQuery.environment = undefined; requestQuery.status = undefined; requestQuery.pageNum = 1; loadRequests() }

async function loadGrants() {
  if (!selectedGrantAppId.value) { grants.value = []; ElMessage.warning('请选择应用'); return }
  grantLoading.value = true
  try { grants.value = ((await listAppGrants(selectedGrantAppId.value) as any).data || []) }
  catch (error) { grants.value = []; notifyError(error, '加载授权台账失败') }
  finally { grantLoading.value = false }
}

async function loadCredentials() {
  if (!canCredentialQuery.value) return
  credentialLoading.value = true
  try { credentials.value = ((await listAppCredentials(credentialAppId.value) as any).data || []) }
  catch (error) { credentials.value = []; notifyError(error, '加载应用凭证失败') }
  finally { credentialLoading.value = false }
}

function openReview(row: OpenAuthorizationRequest) { Object.assign(reviewForm, { id: String(row.id), requestData: formatRequestData(row.requestData), status: 1, reason: '' }); reviewVisible.value = true }
async function submitReview() {
  if (!(await reviewFormRef.value?.validate())) return
  submitLoading.value = true
  try { await reviewAuthorizationRequest(reviewForm.id, reviewForm.status, reviewForm.reason || undefined); ElMessage.success('审核完成'); reviewVisible.value = false; await Promise.all([loadRequests(), loadGrants()]) }
  catch (error) { notifyError(error, '审核授权申请失败') } finally { submitLoading.value = false }
}

async function revokeGrant(row: OpenGrant) {
  try {
    const result = await ElMessageBox.prompt('请输入撤销原因（可选）', '撤销授权', { inputType: 'textarea', inputPlaceholder: '撤销原因' }) as unknown as { value: string }
    await revokeAppGrant(row.id, result.value || undefined)
    ElMessage.success('授权已撤销')
    await loadGrants()
  } catch (error) { if (!isCancel(error)) notifyError(error, '撤销授权失败') }
}

function openGenerate() { Object.assign(generateForm, { appId: credentialAppId.value, environment: 'SANDBOX' }); generateVisible.value = true }
async function submitGenerate() {
  if (!(await generateFormRef.value?.validate()) || !generateForm.appId) return
  submitLoading.value = true
  try { const response = (await generateAppCredential(generateForm.appId, generateForm.environment) as any).data; generateVisible.value = false; openSecret(response, '凭证生成成功'); await loadCredentials() }
  catch (error) { notifyError(error, '生成应用凭证失败') } finally { submitLoading.value = false }
}

async function rotateCredential(row: OpenCredential) {
  try {
    await ElMessageBox.confirm('轮换后旧凭证会立即失效，确认继续？', '轮换凭证', { type: 'warning' })
    const response = (await rotateAppCredential(row.id) as any).data
    openSecret(response, '凭证轮换成功')
    await loadCredentials()
  } catch (error) { if (!isCancel(error)) notifyError(error, '轮换应用凭证失败') }
}

function openSecret(value: OpenCredentialSecret, title: string) { Object.assign(secretCredential, { ...value }); secretTitle.value = title; secretVisible.value = true }
function clearSecret() { Object.keys(secretCredential).forEach(key => delete (secretCredential as any)[key]) }
async function copySecret(value: string, label: string) {
  if (!value) return
  try { await navigator.clipboard.writeText(value) }
  catch { const textarea = document.createElement('textarea'); textarea.value = value; textarea.style.position = 'fixed'; textarea.style.opacity = '0'; document.body.appendChild(textarea); textarea.select(); document.execCommand('copy'); textarea.remove() }
  ElMessage.success(`${label}已复制`)
}

function environmentLabel(value?: string) { return value === 'SANDBOX' ? '沙箱' : value === 'PROD' ? '生产' : value || '-' }
function formatRequestData(value?: string) { if (!value) return '未提供'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }
function requestDataSummary(value?: string) { const formatted = formatRequestData(value); return formatted.length > 120 ? `${formatted.slice(0, 120)}…` : formatted }
function requestTypeLabel(value?: number) { return ({ 0: '新增授权', 1: '变更授权', 2: '撤销授权' } as Record<number, string>)[value ?? -1] || '未知' }
function requestStatusLabel(value?: number) { return requestStatusOptions.find(item => item.value === value)?.label || '未知' }
function requestStatusTagType(value?: number): TagType { return value === 1 ? 'success' : value === 2 ? 'danger' : value === 0 ? 'warning' : 'info' }
function grantStatusLabel(value?: number) { return ({ 0: '待审核', 1: '已生效', 2: '已驳回', 3: '已过期', 4: '已撤销' } as Record<number, string>)[value ?? -1] || '未知' }
function grantStatusTagType(value?: number): TagType { return value === 1 ? 'success' : value === 0 ? 'warning' : value === 4 ? 'info' : 'danger' }
function credentialStatusLabel(value?: number) { return ({ 0: '正常', 1: '停用', 2: '已轮换' } as Record<number, string>)[value ?? -1] || '未知' }
function credentialStatusTagType(value?: number): TagType { return value === 0 ? 'success' : value === 2 ? 'info' : 'warning' }
function isCancel(error: unknown) { return error === 'cancel' || (error as any)?.message === 'cancel' }
function notifyError(error: unknown, fallback: string) { const message = error instanceof Error && error.message && error.message !== '请求失败' ? error.message : fallback; ElMessage.error(message) }
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.secret-warning { margin-bottom: 20px; }
</style>
