<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="学年名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="学校">
          <EducationSchoolSelector v-model="query.schoolId" :nodes="organizations" clearable style="width: 220px" @change="handleQuery" />
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
          <span>学年列表</span>
          <el-button v-if="userStore.hasPermission('education:academic-year:add')" type="primary" :icon="Plus" @click="openAdd">新增学年</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon class="hint">
        每所学校只能有一个启用中的学年；关闭后保留班级、人员归属和订单历史，不会重写历史数据。
      </el-alert>
      <el-table v-loading="loading" :data="records">
        <el-table-column label="学校" min-width="180"><template #default="{ row }">{{ schoolName(row.schoolId) }}</template></el-table-column>
        <el-table-column label="学年名称" prop="yearName" min-width="160" />
        <el-table-column label="开始日期" prop="beginDate" min-width="120" />
        <el-table-column label="结束日期" prop="endDate" min-width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPermission('education:academic-year:edit')" type="primary" link :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPermission('education:academic-year:remove')" type="danger" link :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" :total="total"
        layout="total, sizes, prev, pager, next, jumper" class="pagination" @size-change="getList" @current-change="getList"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="学校" prop="schoolId"><EducationSchoolSelector v-model="form.schoolId" :nodes="organizations" :disabled="Boolean(form.id && form.schoolId)" style="width:100%" /></el-form-item>
        <el-form-item label="学年编码" prop="yearCode"><el-input v-model="form.yearCode" placeholder="例如：2026-2027" /></el-form-item>
        <el-form-item label="学年名称" prop="yearName"><el-input v-model="form.yearName" placeholder="例如：2026-2027 学年" /></el-form-item>
        <el-form-item label="开始日期" prop="beginDate"><el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addAcademicYear, listAcademicYears, listOrganizationTree, removeAcademicYears, updateAcademicYear,
  type AcademicYear, type AcademicYearQuery, type AcademicYearStatus, type EducationOrganizationNode
} from '@/api/education'
import { useUserStore } from '@/stores/user'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'
import { schoolOptions, type SchoolOption } from '@/utils/education-school-tree'

const userStore = useUserStore()
const statusOptions: Array<{ label: string; value: AcademicYearStatus }> = [
  { label: '草稿', value: 'DRAFT' }, { label: '启用', value: 'ACTIVE' }, { label: '已关闭', value: 'CLOSED' }
]
const loading = ref(false)
const submitting = ref(false)
const records = ref<AcademicYear[]>([])
const organizations = ref<EducationOrganizationNode[]>([])
const schools = ref<SchoolOption[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const query = reactive<AcademicYearQuery>({ keyword: '', schoolId: undefined, status: '', pageNum: 1, pageSize: 20 })
const form = reactive<AcademicYear>(emptyForm())
const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  yearCode: [{ required: true, pattern: /^\d{4}-\d{4}$/, message: '请输入 YYYY-YYYY 格式的学年编码', trigger: 'blur' }],
  yearName: [{ required: true, message: '请输入学年名称', trigger: 'blur' }],
  beginDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(async () => { const response = await listOrganizationTree(0); organizations.value = response.data || []; schools.value = schoolOptions(organizations.value); if (schools.value.length === 1) query.schoolId = schools.value[0].id; await getList() })

function emptyForm(): AcademicYear {
  return { schoolId: query.schoolId || '', yearCode: '', yearName: '', beginDate: '', endDate: '', status: 'DRAFT', remark: '' }
}
function schoolName(id?: string | number) { return schools.value.find(item => String(item.id) === String(id))?.schoolName || '—' }
function statusLabel(value: AcademicYearStatus) { return statusOptions.find(item => item.value === value)?.label || value }
function statusTag(value: AcademicYearStatus) { return value === 'ACTIVE' ? 'success' : value === 'CLOSED' ? 'info' : 'warning' }
async function getList() {
  loading.value = true
  try {
    const response = await listAcademicYears(query)
    records.value = response.data?.rows || []
    total.value = response.data?.total || 0
  } finally { loading.value = false }
}
function handleQuery() { query.pageNum = 1; getList() }
function resetQuery() { query.keyword = ''; query.status = ''; query.schoolId = undefined; handleQuery() }
function openAdd() { Object.assign(form, emptyForm()); delete form.id; dialogTitle.value = '新增学年'; dialogVisible.value = true }
function openEdit(row: AcademicYear) { Object.assign(form, emptyForm(), row); dialogTitle.value = '编辑学年'; dialogVisible.value = true }
async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (form.endDate < form.beginDate) return ElMessage.error('结束日期不能早于开始日期')
  submitting.value = true
  try {
    if (form.id) await updateAcademicYear(form); else await addAcademicYear(form)
    ElMessage.success(form.id ? '修改成功' : '新增成功'); dialogVisible.value = false; await getList()
  } finally { submitting.value = false }
}
async function remove(row: AcademicYear) {
  await ElMessageBox.confirm(`确认删除学年「${row.yearName}」吗？关联班级、学期或升级批次时系统会拒绝删除。`, '删除确认', { type: 'warning' })
  await removeAcademicYears([row.id!]); ElMessage.success('删除成功'); await getList()
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.hint { margin-bottom: 16px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
