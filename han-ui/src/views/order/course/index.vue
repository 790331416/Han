<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="听讲班ID">
          <el-input v-model="query.listenClassId" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="主讲班ID">
          <el-input v-model="query.lectureClassId" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="学期ID">
          <el-input v-model="query.semesterId" clearable @keyup.enter="handleQuery" />
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
        <el-table-column label="听讲班" prop="listenClassId" min-width="170" show-overflow-tooltip />
        <el-table-column label="主讲班" prop="lectureClassId" min-width="170" show-overflow-tooltip />
        <el-table-column label="学期" prop="semesterId" min-width="170" show-overflow-tooltip />
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
        <el-form-item label="听讲班ID" prop="listenClassId">
          <el-input v-model="form.listenClassId" clearable />
        </el-form-item>
        <el-form-item label="听讲教室ID">
          <el-input v-model="form.listenRoomId" clearable placeholder="留空则听课记录的场所为空" />
        </el-form-item>
        <el-form-item label="听讲端设备ID">
          <el-input v-model="form.listenDeviceId" clearable placeholder="留空则听讲端无法凭设备编码进课堂" />
        </el-form-item>
        <el-form-item label="主讲班ID" prop="lectureClassId">
          <el-input v-model="form.lectureClassId" clearable />
        </el-form-item>
        <el-form-item label="学期ID" prop="semesterId">
          <el-input v-model="form.semesterId" clearable />
        </el-form-item>
        <el-form-item label="授权粒度" prop="grantScope">
          <el-radio-group v-model="form.grantScope">
            <el-radio value="WHOLE_CLASS">整班打包</el-radio>
            <el-radio value="BY_SUBJECT">按科目</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.grantScope === 'BY_SUBJECT'" label="科目ID" prop="subjectIdsText">
          <el-input v-model="subjectIdsText" placeholder="多个科目 ID 用英文逗号分隔" />
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
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

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
const subjectIdsText = ref('')
const query = reactive<OrderQuery>({ status: '', pageNum: 1, pageSize: 20 })
const form = reactive<CreateOrderForm>(emptyForm())

const rules = computed<FormRules>(() => ({
  listenClassId: [{ required: true, message: '请输入听讲班 ID', trigger: 'blur' }],
  lectureClassId: [{ required: true, message: '请输入主讲班 ID', trigger: 'blur' }],
  semesterId: [{ required: true, message: '请输入学期 ID', trigger: 'blur' }]
}))

onMounted(getList)

function emptyForm(): CreateOrderForm {
  return { grantScope: 'WHOLE_CLASS', draft: false, remark: '' }
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
  subjectIdsText.value = ''
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
    const ids = subjectIdsText.value.split(',').map(item => item.trim()).filter(Boolean)
    if (ids.length === 0) {
      ElMessage.error('按科目订购至少要填一个科目 ID')
      return
    }
    payload.subjectIds = ids
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
