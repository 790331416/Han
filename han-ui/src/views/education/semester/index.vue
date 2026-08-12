<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="编码或名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="学期阶段">
          <el-select v-model="query.lifecycleStatus" clearable style="width: 140px">
            <el-option v-for="item in lifecycleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
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
          <span>学期列表</span>
          <el-button
            v-if="userStore.hasPermission('education:semester:add')"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >新增学期</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records">
        <el-table-column label="学期编码" prop="semesterCode" min-width="140" show-overflow-tooltip />
        <el-table-column label="学期名称" prop="semesterName" min-width="180" show-overflow-tooltip />
        <el-table-column label="开始日期" prop="beginDate" min-width="120" />
        <el-table-column label="结束日期" prop="endDate" min-width="120" />
        <el-table-column label="学期阶段" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="lifecycleTagType(row.lifecycleStatus)">{{ lifecycleLabel(row.lifecycleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前学期" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.currentFlag === 1" type="warning">当前</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission('education:semester:edit')"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon class="hint">
        学期阶段由系统按日期自动推进，不需要也不能手工设置；这里的「状态」表示这条学期记录本身是否启用。
      </el-alert>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="学期编码" prop="semesterCode">
          <el-input v-model="form.semesterCode" clearable />
        </el-form-item>
        <el-form-item label="学期名称" prop="semesterName">
          <el-input v-model="form.semesterName" clearable />
        </el-form-item>
        <el-form-item label="开始日期" prop="beginDate">
          <el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="当前学期" prop="currentFlag">
          <el-switch v-model="form.currentFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
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
import { onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  addSemester,
  listSemesters,
  updateSemester,
  type Semester,
  type SemesterLifecycle,
  type SemesterQuery
} from '@/api/education'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const lifecycleOptions: Array<{ value: SemesterLifecycle; label: string }> = [
  { value: 'NOT_STARTED', label: '未开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'FINISHED', label: '已结束' }
]

const loading = ref(false)
const submitLoading = ref(false)
const records = ref<Semester[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Semester>(emptyForm())
const query = reactive<SemesterQuery>({ keyword: '', status: '', lifecycleStatus: '', pageNum: 1, pageSize: 20 })

const rules: FormRules = {
  semesterCode: [{ required: true, message: '请输入学期编码', trigger: 'blur' }],
  semesterName: [{ required: true, message: '请输入学期名称', trigger: 'blur' }],
  beginDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

onMounted(getList)

function emptyForm(): Semester {
  return {
    semesterCode: '',
    semesterName: '',
    beginDate: '',
    endDate: '',
    currentFlag: 0,
    status: 0,
    remark: ''
  }
}

function lifecycleLabel(value?: SemesterLifecycle) {
  return lifecycleOptions.find(item => item.value === value)?.label || '未知'
}

function lifecycleTagType(value?: SemesterLifecycle) {
  if (value === 'IN_PROGRESS') return 'success'
  if (value === 'FINISHED') return 'info'
  return 'warning'
}

async function getList() {
  loading.value = true
  try {
    const response = await listSemesters(query)
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
  query.keyword = ''
  query.status = ''
  query.lifecycleStatus = ''
  handleQuery()
}

function handleAdd() {
  Object.assign(form, emptyForm())
  delete form.id
  dialogTitle.value = '新增学期'
  dialogVisible.value = true
}

function handleEdit(row: Semester) {
  Object.assign(form, emptyForm(), row)
  dialogTitle.value = '编辑学期'
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (form.endDate < form.beginDate) {
    ElMessage.error('结束日期不能早于开始日期')
    return
  }
  submitLoading.value = true
  try {
    if (form.id) await updateSemester(form)
    else await addSemester(form)
    ElMessage.success(form.id ? '修改成功' : '新增成功')
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
.hint { margin-bottom: 16px; }
</style>
