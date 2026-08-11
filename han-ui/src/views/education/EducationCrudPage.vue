<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="编码或名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="entity === 'people'" label="人员类型">
          <el-input v-model="query.personType" clearable placeholder="如 TEACHER" />
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
          <el-button
            v-if="userStore.hasPermission(`${config.permission}:add`)"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >新增{{ config.title }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records">
        <el-table-column
          v-for="column in config.columns"
          :key="column.key"
          :label="column.label"
          :prop="column.key"
          :min-width="column.width || 130"
          show-overflow-tooltip
        />
        <el-table-column label="来源" prop="sourceSystem" width="140">
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:edit`)"
              type="primary"
              link
              :icon="Edit"
              :disabled="row.sourceSystem !== 'HAN'"
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
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item
          v-for="field in config.fields"
          :key="field.key"
          :label="field.label"
          :prop="field.key"
          :rules="field.required ? [{ required: true, message: `请输入${field.label}`, trigger: 'blur' }] : []"
        >
          <el-select v-if="field.type === 'status'" v-model="form[field.key]" style="width: 100%">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
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
            :type="field.type === 'textarea' ? 'textarea' : 'text'"
            :rows="field.type === 'textarea' ? 3 : undefined"
            clearable
          />
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
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  addEducation,
  listEducation,
  updateEducation,
  type EducationEntity,
  type EducationQuery,
  type EducationRecord
} from '@/api/education'
import { useUserStore } from '@/stores/user'

type FieldType = 'text' | 'number' | 'textarea' | 'status'
interface Field { key: string; label: string; required?: boolean; type?: FieldType }
interface EntityConfig {
  title: string
  permission: string
  columns: Array<{ key: string; label: string; width?: number }>
  fields: Field[]
}

const props = defineProps<{ entity: EducationEntity }>()
const userStore = useUserStore()

const configs: Record<EducationEntity, EntityConfig> = {
  schools: {
    title: '学校', permission: 'education:school',
    columns: [{ key: 'schoolCode', label: '学校编码' }, { key: 'schoolName', label: '学校名称', width: 180 }, { key: 'schoolRole', label: '学校角色' }, { key: 'areaCode', label: '区域编码' }],
    fields: [{ key: 'parentId', label: '上级学校ID' }, { key: 'schoolCode', label: '学校编码', required: true }, { key: 'schoolName', label: '学校名称', required: true }, { key: 'schoolRole', label: '学校角色', required: true }, { key: 'areaCode', label: '区域编码' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  classes: {
    title: '班级', permission: 'education:class',
    columns: [{ key: 'classCode', label: '班级编码' }, { key: 'className', label: '班级名称', width: 180 }, { key: 'gradeCode', label: '年级编码' }, { key: 'schoolId', label: '学校ID', width: 180 }],
    fields: [{ key: 'schoolId', label: '学校ID', required: true }, { key: 'gradeCode', label: '年级编码' }, { key: 'classCode', label: '班级编码', required: true }, { key: 'className', label: '班级名称', required: true }, { key: 'classRole', label: '班级角色', required: true }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  people: {
    title: '人员', permission: 'education:person',
    columns: [{ key: 'personNo', label: '人员编号' }, { key: 'personName', label: '姓名', width: 160 }, { key: 'personType', label: '人员类型' }, { key: 'schoolId', label: '学校ID', width: 180 }, { key: 'phone', label: '手机号' }],
    fields: [{ key: 'userId', label: 'Han用户ID' }, { key: 'schoolId', label: '学校ID', required: true }, { key: 'personNo', label: '人员编号', required: true }, { key: 'personName', label: '姓名', required: true }, { key: 'personType', label: '人员类型', required: true }, { key: 'phone', label: '手机号' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  subjects: {
    title: '科目', permission: 'education:subject',
    columns: [{ key: 'subjectCode', label: '科目编码' }, { key: 'subjectName', label: '科目名称', width: 180 }, { key: 'sort', label: '排序' }],
    fields: [{ key: 'subjectCode', label: '科目编码', required: true }, { key: 'subjectName', label: '科目名称', required: true }, { key: 'sort', label: '排序', required: true, type: 'number' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  devices: {
    title: '设备', permission: 'education:device',
    columns: [{ key: 'deviceCode', label: '设备编码' }, { key: 'deviceName', label: '设备名称', width: 180 }, { key: 'deviceType', label: '设备类型' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态' }],
    fields: [{ key: 'schoolId', label: '学校ID', required: true }, { key: 'roomId', label: '教室ID' }, { key: 'deviceCode', label: '设备编码', required: true }, { key: 'deviceName', label: '设备名称', required: true }, { key: 'deviceType', label: '设备类型', required: true }, { key: 'model', label: '型号' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态', required: true }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  }
}

const config = computed(() => configs[props.entity])
const loading = ref(false)
const submitLoading = ref(false)
const records = ref<EducationRecord[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<EducationRecord>({})
const query = reactive<EducationQuery>({ keyword: '', status: '', personType: '', pageNum: 1, pageSize: 20 })

onMounted(getList)

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
    form[field.key] = field.type === 'status' || field.type === 'number' ? 0 : ''
  }
}

function handleAdd() {
  resetForm()
  dialogTitle.value = `新增${config.value.title}`
  dialogVisible.value = true
}

function handleEdit(row: EducationRecord) {
  if (row.sourceSystem !== 'HAN') return
  resetForm()
  Object.assign(form, row)
  dialogTitle.value = `编辑${config.value.title}`
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.id) await updateEducation(props.entity, form)
    else await addEducation(props.entity, form)
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
</style>
