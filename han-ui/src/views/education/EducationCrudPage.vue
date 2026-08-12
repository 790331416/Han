<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="编码或名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="entity === 'people'" label="人员类型">
          <el-select v-model="query.personType" clearable style="width: 140px">
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
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
          <span>{{ config.title }}列表</span>
          <div>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:remove`)"
              type="danger"
              plain
              :icon="Delete"
              :disabled="selection.length === 0"
              @click="handleRemove()"
            >删除</el-button>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:add`)"
              type="primary"
              :icon="Plus"
              @click="handleAdd"
            >新增{{ config.title }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" :selectable="isLocalRow" />
        <el-table-column
          v-for="column in config.columns"
          :key="column.key"
          :label="column.label"
          :prop="column.key"
          :min-width="column.width || 130"
          show-overflow-tooltip
        />
        <el-table-column v-if="config.showSource !== false" label="来源" prop="sourceSystem" width="110">
          <template #default="{ row }">
            <el-tag :type="row.sourceSystem === 'HAN' ? 'success' : 'info'">
              {{ row.sourceSystem === 'HAN' ? '管理端' : '数字校园' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:edit`)"
              type="primary"
              link
              :icon="Edit"
              :disabled="!isLocalRow(row)"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:remove`)"
              type="danger"
              link
              :icon="Delete"
              :disabled="!isLocalRow(row)"
              @click="handleRemove(row)"
            >删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px">
        <template v-for="field in visibleFields" :key="field.key">
          <el-form-item
            :label="field.label"
            :prop="field.key"
            :rules="field.required ? [{ required: true, message: `请填写${field.label}`, trigger: 'blur' }] : []"
          >
            <el-select v-if="field.type === 'status'" v-model="form[field.key]" style="width: 100%">
              <el-option label="正常" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
            <el-select v-else-if="field.type === 'flag'" v-model="form[field.key]" style="width: 100%">
              <el-option label="否" :value="0" />
              <el-option label="是" :value="1" />
            </el-select>
            <el-switch v-else-if="field.type === 'switch'" v-model="form[field.key]" />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="form[field.key]"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="form[field.key]"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="option in options[field.key] || []"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-select
              v-else-if="field.type === 'multi'"
              v-model="form[field.key]"
              multiple
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="option in options[field.key] || []"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="form[field.key]"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-input
              v-else
              v-model="form[field.key]"
              :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'"
              :rows="field.type === 'textarea' ? 3 : undefined"
              :show-password="field.type === 'password'"
              clearable
            />
            <div v-if="field.hint" class="field-hint">{{ field.hint }}</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  addEducation,
  addPerson,
  listEducation,
  listPersonAssignments,
  listPersonMemberships,
  listPersonRoles,
  removeEducation,
  updateEducation,
  updatePerson,
  type EducationEntity,
  type EducationQuery,
  type EducationRecord
} from '@/api/education'
import { listAllRoles } from '@/api/system/role'
import { useUserStore } from '@/stores/user'

type FieldType = 'text' | 'number' | 'textarea' | 'status' | 'flag' | 'switch' | 'password' | 'date' | 'select' | 'multi'
type OptionSource = 'roles' | 'classes' | 'subjects' | 'schools' | 'rooms'
interface Field {
  key: string
  label: string
  required?: boolean
  type?: FieldType
  source?: OptionSource
  hint?: string
  visibleWhen?: (form: EducationRecord) => boolean
}
interface EntityConfig {
  title: string
  permission: string
  showSource?: boolean
  columns: Array<{ key: string; label: string; width?: number }>
  fields: Field[]
}

const props = defineProps<{ entity: EducationEntity }>()
const userStore = useUserStore()

const configs: Record<EducationEntity, EntityConfig> = {
  schools: {
    title: '学校', permission: 'education:school',
    columns: [{ key: 'schoolCode', label: '学校编码' }, { key: 'schoolName', label: '学校名称', width: 180 }, { key: 'schoolRole', label: '学校角色' }, { key: 'areaCode', label: '区域编码' }],
    fields: [{ key: 'parentId', label: '上级学校', type: 'select', source: 'schools' }, { key: 'schoolCode', label: '学校编码', required: true }, { key: 'schoolName', label: '学校名称', required: true }, { key: 'schoolRole', label: '学校角色', required: true }, { key: 'areaCode', label: '区域编码' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  classes: {
    title: '班级', permission: 'education:class',
    columns: [{ key: 'classCode', label: '班级编码' }, { key: 'className', label: '班级名称', width: 180 }, { key: 'gradeCode', label: '年级编码' }, { key: 'schoolId', label: '学校ID', width: 180 }],
    fields: [{ key: 'schoolId', label: '学校', required: true, type: 'select', source: 'schools' }, { key: 'gradeCode', label: '年级编码' }, { key: 'classCode', label: '班级编码', required: true }, { key: 'className', label: '班级名称', required: true }, { key: 'classRole', label: '班级角色', required: true }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  people: {
    title: '人员', permission: 'education:person',
    columns: [{ key: 'personNo', label: '人员编号' }, { key: 'personName', label: '姓名', width: 140 }, { key: 'personType', label: '人员类型' }, { key: 'userId', label: '登录账号ID', width: 170 }, { key: 'phone', label: '手机号' }],
    fields: [
      { key: 'schoolId', label: '学校', required: true, type: 'select', source: 'schools' },
      { key: 'personNo', label: '人员编号', required: true },
      { key: 'personName', label: '姓名', required: true },
      { key: 'personType', label: '人员类型', required: true, type: 'select' },
      { key: 'phone', label: '手机号' },
      { key: 'classIds', label: '所属班级', type: 'multi', source: 'classes', hint: '学生只能归属一个有效行政班' },
      { key: 'subjectIds', label: '任教科目', type: 'multi', source: 'subjects', visibleWhen: (form) => form.personType !== 'STUDENT' },
      { key: 'leaveFlag', label: '离校状态', type: 'flag', hint: '离校只影响教育身份，不改动登录账号的启用状态' },
      { key: 'loginEnabled', label: '启用登录', type: 'switch', hint: '启用后由系统建号，无需手工填写 Han 用户 ID' },
      { key: 'username', label: '登录名', type: 'text', visibleWhen: (form) => !!form.loginEnabled, hint: '字母开头，4~30 位' },
      { key: 'password', label: '初始密码', type: 'password', visibleWhen: (form) => !!form.loginEnabled, hint: '留空则由系统生成并要求首次登录修改' },
      { key: 'roleIds', label: '登录角色', type: 'multi', source: 'roles', visibleWhen: (form) => !!form.loginEnabled },
      { key: 'status', label: '状态', required: true, type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  subjects: {
    title: '科目', permission: 'education:subject',
    columns: [{ key: 'subjectCode', label: '科目编码' }, { key: 'subjectName', label: '科目名称', width: 180 }, { key: 'sort', label: '排序' }],
    fields: [{ key: 'subjectCode', label: '科目编码', required: true }, { key: 'subjectName', label: '科目名称', required: true }, { key: 'sort', label: '排序', required: true, type: 'number' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  devices: {
    title: '设备', permission: 'education:device',
    columns: [{ key: 'deviceCode', label: '设备编码' }, { key: 'deviceName', label: '设备名称', width: 180 }, { key: 'deviceType', label: '设备类型' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态' }],
    fields: [{ key: 'schoolId', label: '学校', required: true, type: 'select', source: 'schools' }, { key: 'roomId', label: '教室', type: 'select', source: 'rooms' }, { key: 'deviceCode', label: '设备编码', required: true }, { key: 'deviceName', label: '设备名称', required: true }, { key: 'deviceType', label: '设备类型', required: true }, { key: 'model', label: '型号' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态', required: true }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  semesters: {
    title: '学期', permission: 'education:semester', showSource: false,
    columns: [{ key: 'semesterCode', label: '学期编码' }, { key: 'semesterName', label: '学期名称', width: 180 }, { key: 'beginDate', label: '开始日期' }, { key: 'endDate', label: '结束日期' }, { key: 'currentFlag', label: '当前学期', width: 100 }],
    fields: [{ key: 'semesterCode', label: '学期编码', required: true }, { key: 'semesterName', label: '学期名称', required: true }, { key: 'beginDate', label: '开始日期', required: true, type: 'date' }, { key: 'endDate', label: '结束日期', required: true, type: 'date' }, { key: 'currentFlag', label: '当前学期', required: true, type: 'flag', hint: '置为“是”后，同租户其他学期的当前标记会自动取消' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  rooms: {
    title: '教室', permission: 'education:room',
    columns: [{ key: 'roomCode', label: '教室编码' }, { key: 'roomName', label: '教室名称', width: 180 }, { key: 'roomType', label: '教室类型' }, { key: 'schoolId', label: '学校ID', width: 180 }],
    fields: [{ key: 'schoolId', label: '学校', required: true, type: 'select', source: 'schools' }, { key: 'roomCode', label: '教室编码', required: true }, { key: 'roomName', label: '教室名称', required: true }, { key: 'roomType', label: '教室类型' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  }
}

const config = computed(() => configs[props.entity])
const loading = ref(false)
const submitLoading = ref(false)
const records = ref<EducationRecord[]>([])
const selection = ref<EducationRecord[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<EducationRecord>({})
const options = reactive<Record<string, Array<{ label: string; value: any }>>>({})
const query = reactive<EducationQuery>({ keyword: '', status: '', personType: '', pageNum: 1, pageSize: 20 })

const visibleFields = computed(() => config.value.fields.filter((field) => !field.visibleWhen || field.visibleWhen(form)))

onMounted(getList)

// 班级候选随所选学校变化，避免跨校选班。
watch(() => form.schoolId, (schoolId) => {
  if (dialogVisible.value && config.value.fields.some((field) => field.source === 'classes' || field.source === 'rooms')) {
    loadOptions('classes', schoolId)
    loadOptions('rooms', schoolId)
  }
})

async function getList() {
  loading.value = true
  try {
    const response = await listEducation(props.entity, query)
    const data = response.data
    records.value = data?.rows || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function isLocalRow(row: EducationRecord) {
  return config.value.showSource === false || row.sourceSystem === 'HAN'
}

function onSelectionChange(rows: EducationRecord[]) {
  selection.value = rows
}

function handleQuery() {
  query.pageNum = 1
  getList()
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.personType = ''
  handleQuery()
}

function resetForm() {
  for (const key of Object.keys(form)) delete form[key]
  for (const field of config.value.fields) {
    if (field.type === 'multi') form[field.key] = []
    else if (field.type === 'switch') form[field.key] = false
    else if (field.type === 'status' || field.type === 'flag' || field.type === 'number') form[field.key] = 0
    else form[field.key] = ''
  }
}

async function handleAdd() {
  resetForm()
  await loadFormOptions()
  dialogTitle.value = `新增${config.value.title}`
  dialogVisible.value = true
}

async function handleEdit(row: EducationRecord) {
  if (!isLocalRow(row)) return
  resetForm()
  Object.assign(form, row)
  form.loginEnabled = !!row.userId
  await loadFormOptions(row.schoolId)
  if (props.entity === 'people' && row.id) {
    const [memberships, assignments, roles] = await Promise.all([
      listPersonMemberships(row.id),
      listPersonAssignments(row.id),
      listPersonRoles(row.id)
    ])
    form.classIds = (memberships.data || []).map((item) => String(item.classId))
    form.subjectIds = (assignments.data || []).map((item) => String(item.subjectId))
    form.roleIds = (roles.data || []).map((item) => String(item))
  }
  dialogTitle.value = `编辑${config.value.title}`
  dialogVisible.value = true
}

async function handleRemove(row?: EducationRecord) {
  const targets = row ? [row] : selection.value
  if (targets.length === 0) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${targets.length} 条${config.value.title}？删除后业务编码可重新使用，历史记录按 ID 保留。`,
    '确认删除',
    { type: 'warning' }
  )
  const removed = await removeEducation(props.entity, targets.map((item) => item.id!))
  ElMessage.success(`已删除 ${removed.data ?? targets.length} 条`)
  selection.value = []
  await getList()
}

async function loadFormOptions(schoolId?: string | number) {
  const sources = new Set(config.value.fields.map((field) => field.source).filter(Boolean) as OptionSource[])
  await Promise.all([...sources].map((source) => loadOptions(source, schoolId ?? form.schoolId)))
  if (props.entity === 'people') {
    options.personType = [
      { label: '教师', value: 'TEACHER' },
      { label: '学生', value: 'STUDENT' }
    ]
  }
}

async function loadOptions(source: OptionSource, schoolId?: string | number) {
  const keys = config.value.fields.filter((field) => field.source === source).map((field) => field.key)
  if (keys.length === 0) return
  let items: Array<{ label: string; value: any }> = []
  if (source === 'roles') {
    const response = await listAllRoles()
    items = (response.data || [])
      .filter((role) => role.roleKey !== 'admin')
      .map((role) => ({ label: role.roleName!, value: String(role.id) }))
  } else if (source === 'schools') {
    const response = await listEducation('schools', { pageNum: 1, pageSize: 100 })
    items = (response.data?.rows || []).map((item) => ({ label: `${item.schoolName}（${item.schoolCode}）`, value: String(item.id) }))
  } else if (source === 'subjects') {
    const response = await listEducation('subjects', { pageNum: 1, pageSize: 100 })
    items = (response.data?.rows || []).map((item) => ({ label: `${item.subjectName}（${item.subjectCode}）`, value: String(item.id) }))
  } else if (source === 'classes') {
    if (!schoolId) { keys.forEach((key) => { options[key] = [] }); return }
    const response = await listEducation('classes', { pageNum: 1, pageSize: 100, schoolId })
    items = (response.data?.rows || []).map((item) => ({ label: `${item.className}（${item.classCode}）`, value: String(item.id) }))
  } else if (source === 'rooms') {
    if (!schoolId) { keys.forEach((key) => { options[key] = [] }); return }
    const response = await listEducation('rooms', { pageNum: 1, pageSize: 100, schoolId })
    items = (response.data?.rows || []).map((item) => ({ label: `${item.roomName}（${item.roomCode}）`, value: String(item.id) }))
  }
  keys.forEach((key) => { options[key] = items })
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (props.entity === 'people') await submitPerson()
    else if (form.id) await updateEducation(props.entity, form)
    else await addEducation(props.entity, form)
    if (props.entity !== 'people') ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function submitPerson() {
  const payload: EducationRecord = { ...form }
  if (!payload.loginEnabled) {
    delete payload.username
    delete payload.password
    delete payload.roleIds
  } else {
    if (!payload.password) delete payload.password
    // 空数组不代表"清空角色"：后端把缺省当作不改动，要清空需显式传 clearRoles。
    if (!payload.roleIds?.length) delete payload.roleIds
  }
  const response = form.id ? await updatePerson(payload) : await addPerson(payload)
  const result = response.data
  if (result?.initialPassword) {
    await ElMessageBox.alert(
      `登录名：${result.username}\n初始密码：${result.initialPassword}\n\n该密码只显示这一次，请立即转交本人，首次登录须修改。`,
      '账号创建成功',
      { type: 'success', confirmButtonText: '我已记录' }
    )
  } else {
    ElMessage.success(form.id ? '修改成功' : '新增成功')
  }
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.field-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
</style>
