<template>
  <div class="app-container" data-testid="open-vendor-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="厂商名称">
          <el-input v-model="query.name" clearable placeholder="请输入" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>厂商管理</span>
          <el-button v-if="canApply" type="primary" :icon="Plus" @click="openApplication">新增入驻申请</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="vendors" :empty-text="canList ? '暂无厂商数据' : '无权限查看厂商数据'">
        <el-table-column label="厂商名称" prop="name" min-width="170" show-overflow-tooltip />
        <el-table-column label="统一社会信用代码" prop="qualificationNo" min-width="180" show-overflow-tooltip />
        <el-table-column label="联系人" prop="contactName" min-width="110" />
        <el-table-column label="联系电话" prop="contactPhone" min-width="130" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canQuery" type="primary" link :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canManage && nextStatuses(row).length" type="primary" link @click="openStatus(row)">变更状态</el-button>
            <el-button v-if="canManage" type="success" link @click="openBind(row)">绑定用户</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="canList ? '暂无厂商数据' : '无权限查看厂商数据'" :image-size="80" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <el-card shadow="never" class="application-card">
      <template #header>
        <div class="card-header">
          <span>厂商入驻申请</span>
          <el-form :model="applicationQuery" :inline="true" class="inline-filter">
            <el-form-item label="状态">
              <el-select v-model="applicationQuery.status" clearable placeholder="全部" style="width: 130px">
                <el-option label="待提交" :value="0" /><el-option label="待审核" :value="1" /><el-option label="审核通过" :value="2" /><el-option label="审核驳回" :value="3" />
              </el-select>
            </el-form-item>
            <el-button type="primary" :icon="Search" @click="handleApplicationQuery">查询</el-button>
          </el-form>
        </div>
      </template>
      <el-table v-loading="applicationLoading" :data="applications" :empty-text="canList ? '暂无入驻申请' : '无权限查看入驻申请'">
        <el-table-column label="申请编号" prop="applicationNo" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请ID" prop="applicationId" width="100" />
        <el-table-column label="厂商ID" prop="vendorId" width="100" />
        <el-table-column label="申请人ID" prop="applicantUserId" width="100" />
        <el-table-column label="状态" width="100" align="center"><template #default="{ row }"><el-tag :type="applicationStatusTagType(row.status)">{{ applicationStatusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="申请说明" prop="reason" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请时间" min-width="170"><template #default="{ row }">{{ formatDate(row.createTime) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button v-if="canReview && row.status === 1" type="warning" link @click="openReview(row)">审核</el-button></template></el-table-column>
        <template #empty><el-empty :description="canList ? '暂无入驻申请' : '无权限查看入驻申请'" :image-size="80" /></template>
      </el-table>
      <el-pagination v-model:current-page="applicationQuery.pageNum" v-model:page-size="applicationQuery.pageSize" :page-sizes="[10, 20, 50]" :total="applicationTotal" layout="total, sizes, prev, pager, next, jumper" class="pagination" @size-change="loadApplications" @current-change="loadApplications" />
    </el-card>

    <el-dialog v-model="applicationVisible" title="新增厂商入驻申请" width="620px" destroy-on-close>
      <el-form ref="applicationFormRef" :model="applicationForm" :rules="applicationRules" label-width="130px">
        <el-form-item label="厂商名称" prop="name"><el-input v-model="applicationForm.name" /></el-form-item>
        <el-form-item label="统一社会信用代码" prop="qualificationNo"><el-input v-model="applicationForm.qualificationNo" /></el-form-item>
        <el-form-item label="所属行业"><el-input v-model="applicationForm.industry" /></el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="applicationForm.contactName" /></el-form-item>
        <el-form-item label="联系电话" prop="contactPhone"><el-input v-model="applicationForm.contactPhone" /></el-form-item>
        <el-form-item label="联系邮箱"><el-input v-model="applicationForm.contactEmail" /></el-form-item>
        <el-form-item label="官网地址"><el-input v-model="applicationForm.website" /></el-form-item>
        <el-form-item label="申请说明"><el-input v-model="applicationForm.applyReason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applicationVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitApplication">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="审核厂商申请" width="500px" destroy-on-close>
      <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="90px">
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="reviewForm.status">
            <el-radio :value="2">审核通过</el-radio>
            <el-radio :value="3">审核驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核说明" prop="reason"><el-input v-model="reviewForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitReview">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusVisible" title="变更厂商状态" width="460px" destroy-on-close>
      <el-form ref="statusFormRef" :model="statusForm" label-width="90px">
        <el-form-item label="目标状态">
          <el-select v-model="statusForm.status" style="width: 100%">
            <el-option v-for="value in statusOptionsForCurrent" :key="value" :label="statusLabel(value)" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更说明"><el-input v-model="statusForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitStatus">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定厂商用户" width="460px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="90px">
        <el-form-item label="用户ID" prop="userId"><el-input v-model="bindForm.userId" placeholder="请输入系统用户ID" /></el-form-item>
        <el-form-item label="厂商角色" prop="role">
          <el-select v-model="bindForm.role" style="width: 100%">
            <el-option label="开发者" value="DEVELOPER" />
            <el-option label="查看者" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitBind">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="厂商详情" width="800px" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="厂商名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码">{{ detail.qualificationNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ detail.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ detail.contactEmail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="官网地址">{{ detail.website || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核说明" :span="2">{{ detail.reviewInfo || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>关联用户</h4>
        <el-table :data="detail.users || []" size="small" :empty-text="'暂无关联用户'">
          <el-table-column label="用户ID" prop="userId" />
          <el-table-column label="用户名" min-width="150"><template #default="{ row }">{{ row.userName || row.username || '-' }}</template></el-table-column>
          <el-table-column label="角色" prop="role" />
          <el-table-column label="状态"><template #default="{ row }">{{ row.status === 0 ? '正常' : '停用' }}</template></el-table-column>
        </el-table>
        <h4>关联应用</h4>
        <el-table :data="detail.apps || []" size="small" :empty-text="'暂无关联应用'">
          <el-table-column label="应用ID" prop="appId" />
          <el-table-column label="应用名称" prop="appName" min-width="160" />
          <el-table-column label="应用类型" prop="appType" />
          <el-table-column label="生命周期"><template #default="{ row }">{{ lifecycleLabel(row.lifecycleStatus) }}</template></el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无详情数据" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { formatDate } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import {
  bindOpenVendorUser,
  getOpenVendor,
  listOpenVendorApplications,
  listOpenVendor,
  reviewOpenVendorApplication,
  submitOpenVendorApplication,
  updateOpenVendorStatus,
  type OpenVendor,
  type OpenVendorApplicationForm,
  type OpenVendorApplication,
  type OpenVendorApplicationQuery,
  type OpenVendorQuery
} from '@/api/open/vendor'

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

const userStore = useUserStore()
const canList = computed(() => userStore.hasPermission('open:vendor:list'))
const canQuery = computed(() => userStore.hasPermission('open:vendor:query'))
const canApply = computed(() => userStore.hasPermission('open:vendor:apply'))
const canReview = computed(() => userStore.hasPermission('open:vendor:review'))
const canManage = computed(() => userStore.hasPermission('open:vendor:manage'))
const loading = ref(false)
const submitLoading = ref(false)
const vendors = ref<OpenVendor[]>([])
const total = ref(0)
const query = reactive<OpenVendorQuery>({ pageNum: 1, pageSize: 10, name: '', status: undefined })
const applicationLoading = ref(false)
const applications = ref<OpenVendorApplication[]>([])
const applicationTotal = ref(0)
const applicationQuery = reactive<OpenVendorApplicationQuery>({ pageNum: 1, pageSize: 10, status: undefined })

const statusOptions = [
  { value: 0, label: '待提交' }, { value: 1, label: '待验证' }, { value: 2, label: '待审核' },
  { value: 3, label: '补充材料' }, { value: 4, label: '审核通过' }, { value: 5, label: '审核驳回' },
  { value: 6, label: '暂停' }, { value: 7, label: '注销' }
]
const vendorTransitions: Record<number, number[]> = { 0: [1], 1: [2], 2: [3, 4, 5], 3: [2, 5], 4: [6, 7], 5: [2], 6: [4, 7], 7: [] }

const applicationVisible = ref(false)
const applicationFormRef = ref<FormInstance>()
const applicationForm = reactive<OpenVendorApplicationForm>({ name: '', qualificationNo: '', industry: '', contactName: '', contactPhone: '', contactEmail: '', website: '', applyReason: '' })
const applicationRules: FormRules = {
  name: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  qualificationNo: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }]
}

const reviewVisible = ref(false)
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive({ id: '', status: 4, reason: '' })
const reviewRules: FormRules = { status: [{ required: true, message: '请选择审核结果', trigger: 'change' }] }

const statusVisible = ref(false)
const statusFormRef = ref<FormInstance>()
const statusForm = reactive({ vendorId: '', currentStatus: 0, status: 0, reason: '' })
const statusOptionsForCurrent = computed(() => vendorTransitions[statusForm.currentStatus] || [])

const bindVisible = ref(false)
const bindFormRef = ref<FormInstance>()
const bindForm = reactive({ vendorId: '', userId: '', role: 'DEVELOPER' })
const bindRules: FormRules = {
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
  role: [{ required: true, message: '请选择厂商角色', trigger: 'change' }]
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OpenVendor>()

onMounted(async () => { await getList(); await loadApplications() })

async function getList() {
  if (!canList.value) return
  loading.value = true
  try {
    const response = await listOpenVendor(query)
    const data = (response as any).data
    vendors.value = data?.rows || data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    vendors.value = []
    total.value = 0
    notifyError(error, '加载厂商列表失败')
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  if (!canList.value) return
  applicationLoading.value = true
  try {
    const response = await listOpenVendorApplications(applicationQuery)
    const data = (response as any).data
    applications.value = data?.rows || data?.records || []
    applicationTotal.value = data?.total || 0
  } catch (error) {
    applications.value = []
    applicationTotal.value = 0
    notifyError(error, '加载入驻申请失败')
  } finally { applicationLoading.value = false }
}

function handleApplicationQuery() { applicationQuery.pageNum = 1; loadApplications() }

function handleQuery() { query.pageNum = 1; getList() }
function resetQuery() { query.name = ''; query.status = undefined; handleQuery() }

function openApplication() {
  Object.assign(applicationForm, { name: '', qualificationNo: '', industry: '', contactName: '', contactPhone: '', contactEmail: '', website: '', applyReason: '' })
  applicationVisible.value = true
}

async function submitApplication() {
  if (!(await applicationFormRef.value?.validate())) return
  submitLoading.value = true
  try {
    await submitOpenVendorApplication(applicationForm)
    ElMessage.success('入驻申请已提交')
    applicationVisible.value = false
    await Promise.all([getList(), loadApplications()])
  } catch (error) { notifyError(error, '提交入驻申请失败') } finally { submitLoading.value = false }
}

async function openDetail(row: OpenVendor) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  try { detail.value = (await getOpenVendor(row.id) as any).data }
  catch (error) { notifyError(error, '加载厂商详情失败') }
  finally { detailLoading.value = false }
}

function openReview(row: OpenVendorApplication) {
  Object.assign(reviewForm, { id: String(row.applicationId), status: 2, reason: '' })
  reviewVisible.value = true
}

async function submitReview() {
  if (!(await reviewFormRef.value?.validate())) return
  submitLoading.value = true
  try {
    await reviewOpenVendorApplication(reviewForm.id, reviewForm.status, reviewForm.reason || undefined)
    ElMessage.success('审核完成')
    reviewVisible.value = false
    await Promise.all([getList(), loadApplications()])
  } catch (error) { notifyError(error, '审核厂商申请失败') } finally { submitLoading.value = false }
}

function openStatus(row: OpenVendor) {
  const options = nextStatuses(row)
  if (!options.length) return
  Object.assign(statusForm, { vendorId: String(row.id), currentStatus: row.status, status: options[0], reason: '' })
  statusVisible.value = true
}

async function submitStatus() {
  if (!statusForm.vendorId) return
  submitLoading.value = true
  try {
    await updateOpenVendorStatus(statusForm.vendorId, statusForm.status, statusForm.reason || undefined)
    ElMessage.success('状态变更成功')
    statusVisible.value = false
    await getList()
  } catch (error) { notifyError(error, '变更厂商状态失败') } finally { submitLoading.value = false }
}

function openBind(row: OpenVendor) {
  Object.assign(bindForm, { vendorId: String(row.id), userId: '', role: 'DEVELOPER' })
  bindVisible.value = true
}

async function submitBind() {
  if (!(await bindFormRef.value?.validate())) return
  submitLoading.value = true
  try {
    await bindOpenVendorUser(bindForm.vendorId, bindForm.userId, bindForm.role)
    ElMessage.success('用户绑定成功')
    bindVisible.value = false
    if (detailVisible.value) await openDetail({ id: bindForm.vendorId, name: '', status: 0 })
  } catch (error) { notifyError(error, '绑定厂商用户失败') } finally { submitLoading.value = false }
}

function nextStatuses(row: OpenVendor) { return vendorTransitions[row.status] || [] }
function statusLabel(status?: number) { return statusOptions.find(item => item.value === status)?.label || '未知' }
function statusTagType(status?: number): TagType {
  if (status === 4) return 'success'
  if (status === 5 || status === 7) return 'danger'
  if (status === 6) return 'warning'
  if (status === 2 || status === 3) return 'primary'
  return 'info'
}
function lifecycleLabel(status?: number) {
  return ['草稿', '待审核', '沙箱开通', '调测中', '生产待审', '生产开通', '暂停', '撤销'][status ?? -1] || '未知'
}
function applicationStatusLabel(status?: number) { return ({ 0: '待提交', 1: '待审核', 2: '审核通过', 3: '审核驳回' } as Record<number, string>)[status ?? -1] || '未知' }
function applicationStatusTagType(status?: number): TagType { return status === 2 ? 'success' : status === 3 ? 'danger' : status === 1 ? 'warning' : 'info' }
function notifyError(error: unknown, fallback: string) {
  const message = error instanceof Error && error.message && error.message !== '请求失败' ? error.message : fallback
  ElMessage.error(message)
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.application-card { margin-top: 16px; }
.inline-filter { margin-bottom: -18px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
h4 { margin: 20px 0 10px; }
</style>
