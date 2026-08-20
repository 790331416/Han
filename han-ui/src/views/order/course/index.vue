<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="听讲班">
          <EducationOptionSelector v-model="query.listenClassId" :options="allClasses" placeholder="全部" style="width: 200px" />
        </el-form-item>
        <el-form-item label="主讲班">
          <EducationOptionSelector v-model="query.lectureClassId" :options="allClasses" placeholder="全部" style="width: 200px" />
        </el-form-item>
        <el-form-item label="学期">
          <EducationSemesterSelector v-model="query.semesterId" placeholder="全部" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 140px">
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
          <span>订购单列表</span>
          <el-button
            v-if="userStore.hasPermission('order:course:add')"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >新增订购单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records">
        <el-table-column label="单号" prop="orderNo" min-width="200" show-overflow-tooltip />
        <!-- 显示名称，ID 挂在 title 上备查：光给一串雪花 ID 在界面上没法用。 -->
        <el-table-column label="听讲班" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="String(row.listenClassId ?? '')">{{ classLabel(row.listenClassId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="主讲班" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="String(row.lectureClassId ?? '')">{{ classLabel(row.lectureClassId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="学期" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="String(row.semesterId ?? '')">{{ semesterLabel(row.semesterId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="粒度" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.grantScope === 'WHOLE_CLASS' ? 'success' : 'primary'">
              {{ row.grantScope === 'WHOLE_CLASS' ? '整班打包' : '按科目' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" prop="effectiveTime" min-width="170" />
        <el-table-column label="失效时间" prop="expireTime" min-width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' && userStore.hasPermission('order:course:submit')"
              type="primary" link @click="act(row, 'submit')"
            >提交</el-button>
            <el-button
              v-if="row.status === 'ACTIVE' && userStore.hasPermission('order:course:freeze')"
              type="warning" link @click="askReason(row, 'freeze')"
            >冻结</el-button>
            <el-button
              v-if="row.status === 'FROZEN' && userStore.hasPermission('order:course:freeze')"
              type="success" link @click="act(row, 'unfreeze')"
            >恢复</el-button>
            <el-button
              v-if="canSync(row) && userStore.hasPermission('order:course:sync')"
              type="primary" link @click="act(row, 'sync')"
            >同步授权</el-button>
            <el-button
              v-if="!isTerminal(row) && userStore.hasPermission('order:course:cancel')"
              type="danger" link @click="askReason(row, 'cancel')"
            >取消</el-button>
            <el-button type="info" link @click="viewGrants(row)">台账</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增订购单" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="业务单号">
          <el-input v-model="form.orderNo" clearable placeholder="留空自动生成；填写后重复提交将幂等返回原单" />
        </el-form-item>
        <!-- 学校本身不提交，只用于把班级、场所、设备候选收敛到正确学校。 -->
        <el-form-item label="听讲学校">
          <EducationSchoolSelector v-model="scope.listenSchoolId" :nodes="organizations" clearable placeholder="先选学校，再选下面的班级" @change="onListenSchoolChange" />
        </el-form-item>
        <el-form-item label="听讲班" prop="listenClassId">
          <EducationClassSelector v-model="form.listenClassId" :school-id="scope.listenSchoolId" placeholder="请选择听讲班" />
        </el-form-item>
        <el-form-item label="听讲教室">
          <EducationPlaceSelector v-model="form.listenRoomId" :school-id="scope.listenSchoolId" placeholder="留空则听课记录的场所为空" @change="onListenRoomChange" />
        </el-form-item>
        <el-form-item label="听讲端设备">
          <el-select
            v-model="form.listenDeviceId" clearable filterable
            :placeholder="scope.listenSchoolId ? '留空则听讲端无法凭设备编码进课堂' : '请先选听讲学校'"
            :disabled="!scope.listenSchoolId" style="width: 100%"
          >
            <el-option v-for="item in listenDevices" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主讲学校">
          <EducationSchoolSelector v-model="scope.lectureSchoolId" :nodes="organizations" clearable placeholder="先选学校，再选主讲班" @change="onLectureSchoolChange" />
        </el-form-item>
        <el-form-item label="主讲班" prop="lectureClassId">
          <EducationClassSelector v-model="form.lectureClassId" :school-id="scope.lectureSchoolId" placeholder="请选择主讲班" />
        </el-form-item>
        <el-form-item label="学期" prop="semesterId">
          <EducationSemesterSelector v-model="form.semesterId" placeholder="请选择学期" />
        </el-form-item>
        <el-form-item label="授权粒度" prop="grantScope">
          <el-radio-group v-model="form.grantScope">
            <el-radio value="WHOLE_CLASS">整班打包</el-radio>
            <el-radio value="BY_SUBJECT">按科目</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.grantScope === 'BY_SUBJECT'" label="科目" prop="subjectIds">
          <EducationSubjectSelector v-model="selectedSubjectIds" :school-id="scope.lectureSchoolId" multiple placeholder="请选择主讲学校的科目，可多选" />
        </el-form-item>
        <el-form-item label="存为草稿">
          <el-switch v-model="form.draft" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  cancelOrder,
  createOrder,
  freezeOrder,
  listOrders,
  submitOrder,
  syncOrder,
  unfreezeOrder,
  type CourseOrder,
  type CreateOrderForm,
  type OrderQuery,
  type OrderStatus,
  type SyncResult
} from '@/api/order'
import { listEducation, listOrganizationTree, listSemesters, type EducationOrganizationNode } from '@/api/education'
import { useUserStore } from '@/stores/user'
import EducationClassSelector from '@/components/education/EducationClassSelector.vue'
import EducationOptionSelector, { type EducationSelectOption } from '@/components/education/EducationOptionSelector.vue'
import EducationPlaceSelector from '@/components/education/EducationPlaceSelector.vue'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'
import EducationSemesterSelector from '@/components/education/EducationSemesterSelector.vue'
import EducationSubjectSelector from '@/components/education/EducationSubjectSelector.vue'

const userStore = useUserStore()
const router = useRouter()

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'
type Option = EducationSelectOption

const statusOptions: Array<{ value: OrderStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待生效' },
  { value: 'ACTIVE', label: '生效中' },
  { value: 'FROZEN', label: '已冻结' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'CANCELLED', label: '已取消' }
]

const loading = ref(false)
const submitLoading = ref(false)
const records = ref<CourseOrder[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const selectedSubjectIds = ref<string[]>([])
const query = reactive<OrderQuery>({ status: '', pageNum: 1, pageSize: 20 })
const form = reactive<CreateOrderForm>(emptyForm())

// 学校不是订购单的字段，服务端从班级反推；这里只用来收敛下面几个下拉的候选范围。
const scope = reactive<{ listenSchoolId?: string | number; lectureSchoolId?: string | number }>({})

const organizations = ref<EducationOrganizationNode[]>([])
const semesters = ref<Option[]>([])
const listenDevices = ref<Option[]>([])
/** 列表用的班级全集：列表里的单子可能跨学校，不能只拿某一所的班级来解释名称。 */
const allClasses = ref<Option[]>([])

const rules = computed<FormRules>(() => ({
  listenClassId: [{ required: true, message: '请选择听讲班', trigger: 'change' }],
  lectureClassId: [{ required: true, message: '请选择主讲班', trigger: 'change' }],
  semesterId: [{ required: true, message: '请选择学期', trigger: 'change' }]
}))

onMounted(async () => {
  await Promise.all([getList(), loadStaticOptions()])
})

function emptyForm(): CreateOrderForm {
  return { grantScope: 'WHOLE_CLASS', draft: false, remark: '' }
}

/** 列表展示只需班级、学期名称；新增表单由通用选择器按学校动态读取。 */
async function loadStaticOptions() {
  const [organizationRes, semesterRes, classRes] = await Promise.all([
    listOrganizationTree(0),
    listSemesters({ pageNum: 1, pageSize: 100 }),
    listEducation('classes', { pageNum: 1, pageSize: 200 })
  ])
  organizations.value = organizationRes.data || []
  semesters.value = (semesterRes.data?.rows || []).map(item => ({
    label: `${item.semesterName}（${item.semesterCode}）`, value: String(item.id)
  }))
  allClasses.value = (classRes.data?.rows || []).map(item => ({
    label: `${item.className}（${item.classCode}）`, value: String(item.id)
  }))
}

async function onListenSchoolChange(value?: string | number | Array<string | number>) {
  const schoolId = Array.isArray(value) ? undefined : value
  // 换学校要清掉旧选择，否则会留下一个属于别的学校的班级 ID，提交时才报错。
  form.listenClassId = undefined
  form.listenRoomId = undefined
  form.listenDeviceId = undefined
  listenDevices.value = []
  if (!schoolId) return
  const deviceRes = await listEducation('devices', { pageNum: 1, pageSize: 100, schoolId })
  listenDevices.value = (deviceRes.data?.rows || []).map(item => ({
    label: `${item.deviceName}（${item.deviceCode}）`, value: String(item.id)
  }))
}

/** 选了教室就把设备再收窄到该教室，没选教室则回到全校设备。 */
async function onListenRoomChange(value?: string | number | Array<string | number>) {
  const roomId = Array.isArray(value) ? undefined : value
  form.listenDeviceId = undefined
  const schoolId = scope.listenSchoolId
  if (!schoolId) return
  const response = await listEducation('devices',
    roomId ? { pageNum: 1, pageSize: 100, schoolId, roomId } : { pageNum: 1, pageSize: 100, schoolId })
  listenDevices.value = (response.data?.rows || []).map(item => ({
    label: `${item.deviceName}（${item.deviceCode}）`, value: String(item.id)
  }))
}

async function onLectureSchoolChange(_value?: string | number | Array<string | number>) {
  form.lectureClassId = undefined
  selectedSubjectIds.value = []
}

function classLabel(id?: string | number) {
  if (id === undefined || id === null || id === '') return '-'
  return allClasses.value.find(item => item.value === String(id))?.label || String(id)
}

function semesterLabel(id?: string | number) {
  if (id === undefined || id === null || id === '') return '-'
  return semesters.value.find(item => item.value === String(id))?.label || String(id)
}

function statusLabel(value?: OrderStatus) {
  return statusOptions.find(item => item.value === value)?.label || '未知'
}

function statusTagType(value?: OrderStatus): TagType {
  if (value === 'ACTIVE') return 'success'
  if (value === 'FROZEN') return 'warning'
  if (value === 'CANCELLED') return 'danger'
  if (value === 'EXPIRED') return 'info'
  return 'primary'
}

function isTerminal(row: CourseOrder) {
  return row.status === 'EXPIRED' || row.status === 'CANCELLED'
}

function canSync(row: CourseOrder) {
  return row.status === 'ACTIVE'
}

async function getList() {
  loading.value = true
  try {
    const response = await listOrders(query)
    records.value = response.data?.rows || []
    total.value = response.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  query.pageNum = 1
  getList()
}

function resetQuery() {
  query.listenClassId = undefined
  query.lectureClassId = undefined
  query.semesterId = undefined
  query.status = ''
  handleQuery()
}

function handleAdd() {
  Object.assign(form, emptyForm())
  selectedSubjectIds.value = []
  scope.listenSchoolId = undefined
  scope.lectureSchoolId = undefined
  listenDevices.value = []
  dialogVisible.value = true
}

function viewGrants(row: CourseOrder) {
  router.push({ path: '/order/grant', query: { orderId: String(row.id) } })
}

async function act(row: CourseOrder, action: 'submit' | 'unfreeze' | 'sync') {
  if (!row.id) return
  if (action === 'submit') {
    await submitOrder(row.id)
    ElMessage.success('已提交')
  } else if (action === 'unfreeze') {
    reportSync((await unfreezeOrder(row.id)).data)
  } else {
    reportSync((await syncOrder(row.id)).data)
  }
  await getList()
}

function reportSync(result?: SyncResult) {
  if (!result) {
    ElMessage.success('操作成功')
    return
  }
  const message = `新增物化 ${result.materialized}，已存在 ${result.alreadyMaterialized}，`
    + `撤销 ${result.revoked}，失败 ${result.failed}`
  if (result.failed > 0) ElMessage.warning(message)
  else ElMessage.success(message)
}

async function askReason(row: CourseOrder, action: 'freeze' | 'cancel') {
  if (!row.id) return
  const title = action === 'freeze' ? '冻结订购单' : '取消订购单'
  const tip = action === 'freeze'
    ? '冻结后该单授权的课程将暂时无法进入，恢复时会补齐冻结期间新增的课程。'
    : '取消后未开始的课程会撤销授权，已结束的课程保留回放。'
  const { value } = await ElMessageBox.prompt(tip, title, {
    inputPlaceholder: '请填写原因',
    inputValidator: (input: string) => (input && input.trim() ? true : '原因不能为空')
  })
  if (action === 'freeze') await freezeOrder(row.id, value)
  else await cancelOrder(row.id, value)
  ElMessage.success('操作成功')
  await getList()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  const payload: CreateOrderForm = { ...form }
  if (form.grantScope === 'BY_SUBJECT') {
    if (selectedSubjectIds.value.length === 0) {
      ElMessage.error('按科目订购至少要选一个科目')
      return
    }
    payload.subjectIds = [...selectedSubjectIds.value]
  } else {
    payload.subjectIds = undefined
  }
  submitLoading.value = true
  try {
    await createOrder(payload)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
