<template>
  <div class="app-container">
    <el-card shadow="never" class="hint-card">
      <el-alert
        title="节次规则由校端预约和课表展示共用；调整时间会影响后续按时间匹配的课程，已有课程保留原节次编号。"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>课表节次</span>
          <div>
            <el-button v-if="canAdd" type="primary" :icon="Plus" @click="openAdd">新增节次</el-button>
            <el-button v-if="canRemove" type="danger" :icon="Delete" :disabled="selection.length === 0" @click="removeSelected()">删除</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" row-key="ruleId" @selection-change="selection = $event">
        <el-table-column type="selection" width="52" />
        <el-table-column prop="classSection" label="节次" width="90" align="center">
          <template #default="{ row }">第{{ row.classSection }}节</template>
        </el-table-column>
        <el-table-column prop="templateName" label="课表模板" min-width="160" />
        <el-table-column label="上课时间" width="140" align="center"><template #default="{ row }">{{ row.startTime.slice(0, 5) }}</template></el-table-column>
        <el-table-column label="下课时间" width="140" align="center"><template #default="{ row }">{{ row.endTime.slice(0, 5) }}</template></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canEdit" link :type="row.status === '0' ? 'warning' : 'success'" @click="toggleStatus(row)">{{ row.status === '0' ? '停用' : '启用' }}</el-button>
            <el-button v-if="canRemove" link type="danger" :icon="Delete" @click="removeOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rules.length === 0" description="暂无节次规则" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="课表模板" prop="templateName"><el-input v-model="form.templateName" maxlength="50" /></el-form-item>
        <el-form-item label="节次" prop="classSection"><el-input-number v-model="sectionNumber" :min="1" :max="99" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="开始时间" prop="startTime"><el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" placeholder="选择开始时间" style="width: 100%" /></el-form-item>
        <el-form-item label="结束时间" prop="endTime"><el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" placeholder="选择结束时间" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addCourseRule, listCourseRules, removeCourseRules, updateCourseRule, updateCourseRuleStatus,
  type EducationCourseRule, type EducationCourseRuleForm
} from '@/api/education'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canAdd = computed(() => userStore.hasPermission('education:course-rule:add'))
const canEdit = computed(() => userStore.hasPermission('education:course-rule:edit'))
const canRemove = computed(() => userStore.hasPermission('education:course-rule:remove'))
const loading = ref(false)
const submitting = ref(false)
const rules = ref<EducationCourseRule[]>([])
const selection = ref<EducationCourseRule[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<EducationCourseRuleForm>({ templateName: '默认作息', classSection: '1', startTime: '', endTime: '' })
const sectionNumber = computed({ get: () => Number(form.classSection || 1), set: (value: number) => { form.classSection = String(value) } })
const formRules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  classSection: [{ required: true, message: '请输入节次', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await listCourseRules()
    rules.value = (response.data || []).sort((a, b) => Number(a.classSection) - Number(b.classSection))
  } finally { loading.value = false }
}

function resetForm() { Object.assign(form, { id: undefined, templateName: '默认作息', classSection: '1', startTime: '', endTime: '' }) }
function openAdd() { resetForm(); dialogTitle.value = '新增节次'; dialogVisible.value = true }
function openEdit(row: EducationCourseRule) {
  Object.assign(form, { id: row.ruleId, templateName: row.templateName, classSection: row.classSection, startTime: row.startTime.slice(0, 5), endTime: row.endTime.slice(0, 5) })
  dialogTitle.value = '编辑节次'; dialogVisible.value = true
}
async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (form.endTime <= form.startTime) return ElMessage.warning('结束时间必须晚于开始时间')
  submitting.value = true
  try {
    if (form.id) await updateCourseRule(form); else await addCourseRule(form)
    ElMessage.success(form.id ? '修改成功' : '新增成功'); dialogVisible.value = false; await load()
  } finally { submitting.value = false }
}
async function toggleStatus(row: EducationCourseRule) {
  const next = row.status === '0' ? '1' : '0'
  await ElMessageBox.confirm(`${next === '0' ? '启用' : '停用'}第${row.classSection}节？`, '确认操作', { type: 'warning' })
  await updateCourseRuleStatus(row.ruleId, next); ElMessage.success('操作成功'); await load()
}
async function removeOne(row: EducationCourseRule) { await removeSelected([row]) }
async function removeSelected(rows = selection.value) {
  if (!rows.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 个节次？已有课程引用的节次只能停用。`, '删除确认', { type: 'warning' })
  await removeCourseRules(rows.map(row => row.ruleId)); ElMessage.success('删除成功'); selection.value = []; await load()
}
</script>

<style scoped>
.app-container { padding: 20px; }
.hint-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
