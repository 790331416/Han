<template>
  <div class="operation-page">
    <section class="page-header"><div><h2>班级管理</h2><p>按学校和学年维护年级、专业分组、班级；不预置任何年级数据。</p></div><div><el-button :disabled="!schoolId" @click="openBatch">批量创建</el-button><el-button type="primary" :icon="Plus" :disabled="!schoolId" @click="openAdd()">新增年级</el-button></div></section>
    <div class="operation-split">
      <el-card shadow="never" class="tree-card">
        <template #header><span class="card-title">年级班级树</span></template>
        <el-input v-model="treeKeyword" clearable placeholder="搜索树节点" :prefix-icon="Search" class="tree-search" />
        <el-tree ref="treeRef" v-loading="loading" :data="treeData" node-key="id" :props="{ label: 'className', children: 'children' }" :filter-node-method="filterNode" default-expand-all :expand-on-click-node="false" highlight-current @node-click="selectNode">
          <template #default="{ data }"><div class="tree-node" :class="{ active: String(selectedId) === String(data.id) }"><span class="tree-node-name">{{ data.className }}</span><el-tag size="small" :type="tagType(data.nodeType)">{{ label(data.nodeType) }}</el-tag></div></template>
        </el-tree>
        <el-empty v-if="schoolId && !loading && !nodes.length" :description="academicYearId ? '当前学年暂无年级，点击“新增年级”开始维护' : '该学校暂未创建学年'" :image-size="72" />
        <el-empty v-if="!schoolId && !loading" description="请先选择校区或独立学校" :image-size="72" />
      </el-card>
      <div class="content-stack">
        <el-card shadow="never" class="filter-card"><el-form :inline="true" @submit.prevent><el-form-item label="学校"><EducationSchoolSelector v-model="schoolId" :nodes="organizations" @change="handleSchoolChange" /></el-form-item><el-form-item label="学年"><el-select v-model="academicYearId" clearable placeholder="全部学年" @change="loadTree"><el-option v-for="item in years" :key="item.id" :label="item.yearName" :value="item.id!" /></el-select></el-form-item><el-form-item label="关键字"><el-input v-model="listKeyword" clearable placeholder="名称" /></el-form-item><el-form-item><el-button type="primary" :icon="Search" @click="loadTree">搜索</el-button><el-button @click="listKeyword = ''; loadTree()">重置</el-button></el-form-item></el-form></el-card>
         <el-card shadow="never" class="list-card"><template #header><div class="list-header"><span class="card-title">{{ listTitle }}</span><div><el-tag type="primary" effect="light">共 {{ filteredRows.length }} 条</el-tag><el-button text :icon="Refresh" @click="loadTree">刷新</el-button></div></div></template><el-table v-loading="loading" :data="filteredRows" height="500"><el-table-column prop="className" label="节点名称" min-width="220" /><el-table-column label="节点类型" min-width="110"><template #default="{ row }">{{ label(row.nodeType) }}</template></el-table-column><el-table-column prop="sort" label="排序" width="80" /><el-table-column label="入学届别" min-width="100"><template #default="{ row }">{{ row.cohortYear ? `${row.cohortYear}年` : '—' }}</template></el-table-column><el-table-column label="状态" min-width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button v-if="userStore.hasPermission('education:class:add')" type="primary" link :disabled="row.nodeType === 'CLASS'" @click="openAdd(row)">新增下级</el-button><el-button v-if="userStore.hasPermission('education:class:edit')" type="primary" link @click="openEdit(row)">编辑</el-button><el-button v-if="userStore.hasPermission('education:class:remove')" type="danger" link @click="remove(row)">删除</el-button></template></el-table-column></el-table><div class="table-footer">共 {{ filteredRows.length }} 条记录</div></el-card>
      </div>
    </div>
    <el-dialog v-model="visible" :title="title" width="560px" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-width="110px"><el-form-item label="学校" prop="schoolId"><EducationSchoolSelector v-model="form.schoolId" :nodes="organizations" :disabled="Boolean(form.id)" style="width:100%" @change="syncDialogSchool" /></el-form-item><el-form-item label="所属学年" prop="academicYearId"><el-select v-model="form.academicYearId" style="width:100%"><el-option v-for="item in years" :key="item.id" :label="item.yearName" :value="item.id!" /></el-select></el-form-item><el-form-item label="上级节点"><el-input :model-value="parentName || '无（新增年级）'" disabled /></el-form-item><el-form-item label="节点名称" prop="className"><el-input v-model="form.className" maxlength="128" show-word-limit /></el-form-item><el-form-item label="节点类型" prop="nodeType"><el-select v-model="form.nodeType" :disabled="Boolean(form.id) || !form.parentId" style="width:100%" @change="handleNodeTypeChange"><el-option label="年级" value="GRADE" /><el-option label="专业/分组" value="MAJOR" /><el-option label="班级" value="CLASS" /></el-select></el-form-item><el-form-item v-if="form.nodeType === 'GRADE'" label="年级" prop="branchCode"><el-select v-model="form.branchCode" style="width:100%"><el-option v-for="item in grades" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item v-if="form.nodeType === 'GRADE'" label="入学届别" prop="cohortYear"><el-select v-model="form.cohortYear" filterable placeholder="默认当前年份，可输入搜索" style="width:100%"><el-option v-for="year in cohortYears" :key="year" :label="`${year}年`" :value="year" /></el-select></el-form-item><el-form-item label="排序值" prop="sort"><el-input-number v-model="form.sort" :min="0" controls-position="right" style="width:100%" /></el-form-item><el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确定</el-button></template></el-dialog>
    <el-dialog v-model="batchVisible" title="批量创建年级或班级" width="520px" destroy-on-close><el-form :model="batchForm" label-width="110px"><el-form-item label="创建类型"><el-select v-model="batchForm.nodeType" style="width:100%"><el-option label="年级（1 至 12）" value="GRADE" /><el-option label="班级" value="CLASS" /></el-select></el-form-item><el-form-item v-if="batchForm.nodeType === 'CLASS'" label="上级节点" required><EducationClassSelector v-model="batchForm.parentId" :school-id="schoolId" :academic-year-id="academicYearId" :selectable-types="['GRADE', 'MAJOR']" /></el-form-item><el-form-item v-if="batchForm.nodeType === 'GRADE'" label="入学届别"><el-select v-model="batchForm.cohortYear" filterable style="width:100%"><el-option v-for="year in cohortYears" :key="year" :label="`${year}年`" :value="year" /></el-select></el-form-item><el-form-item label="序号范围" required><el-input-number v-model="batchForm.startNo" :min="1" controls-position="right" /><span class="range-separator">至</span><el-input-number v-model="batchForm.endNo" :min="batchForm.startNo" controls-position="right" /></el-form-item><el-form-item label="状态"><el-select v-model="batchForm.status" style="width:100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item></el-form><template #footer><el-button @click="batchVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitBatch">确定创建</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { addClassTreeNode, batchCreateClassTreeNodes, listAcademicYears, listClassTree, listOrganizationTree, removeClassTreeNodes, updateClassTreeNode, type AcademicYear, type EducationClassTreeForm, type EducationClassTreeNode, type EducationClassTreeRange, type EducationOrganizationNode, type TeachingNodeType } from '@/api/education'
import { useUserStore } from '@/stores/user'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'
import EducationClassSelector from '@/components/education/EducationClassSelector.vue'
import { schoolOptions, type SchoolOption } from '@/utils/education-school-tree'

type VirtualTreeNode = { id: string; className: string; nodeType: 'SCHOOL' | 'ACADEMIC_YEAR'; classCode: string; branchCode: string; children: Array<VirtualTreeNode | EducationClassTreeNode> }
const organizations = ref<EducationOrganizationNode[]>([])
const schools = ref<SchoolOption[]>([])
const userStore = useUserStore()
const years = ref<AcademicYear[]>([])
const nodes = ref<EducationClassTreeNode[]>([])
const schoolId = ref<string | number>()
const academicYearId = ref<string | number>()
const treeKeyword = ref('')
const listKeyword = ref('')
const selectedId = ref<string | number>()
const selectedName = ref('')
const treeRef = ref<TreeInstance>()
const loading = ref(false)
const submitting = ref(false)
const visible = ref(false)
const batchVisible = ref(false)
const title = ref('')
const parentName = ref('')
const formRef = ref<FormInstance>()
const form = reactive<EducationClassTreeForm>(emptyForm())
const batchForm = reactive<EducationClassTreeRange>(emptyBatchForm())
const grades = [{ label: '小班', value: 'G001' }, { label: '中班', value: 'G002' }, { label: '大班', value: 'G003' }, { label: '一年级', value: 'G004' }, { label: '二年级', value: 'G005' }, { label: '三年级', value: 'G006' }, { label: '四年级', value: 'G007' }, { label: '五年级', value: 'G008' }, { label: '六年级', value: 'G009' }, { label: '七年级', value: 'G010' }, { label: '八年级', value: 'G011' }, { label: '九年级', value: 'G012' }, { label: '高一年级', value: 'G013' }, { label: '高二年级', value: 'G014' }, { label: '高三年级', value: 'G015' }, { label: '学前班', value: 'G920' }, { label: '毕业年级', value: 'G930' }, { label: '其他年级', value: 'G940' }]
const cohortYears = Array.from({ length: new Date().getFullYear() - 1899 }, (_, index) => new Date().getFullYear() + 1 - index)
const rules: FormRules = { schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }], academicYearId: [{ required: true, message: '请选择所属学年', trigger: 'change' }], className: [{ required: true, message: '请输入节点名称', trigger: 'blur' }], nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }], branchCode: [{ validator: (_rule, value, callback) => form.nodeType !== 'GRADE' || value ? callback() : callback(new Error('请选择年级')), trigger: 'change' }], cohortYear: [{ validator: (_rule, value, callback) => form.nodeType !== 'GRADE' || value ? callback() : callback(new Error('请选择入学届别')), trigger: 'change' }] }
const currentSchool = computed(() => schools.value.find(item => String(item.id) === String(schoolId.value)))
const currentYear = computed(() => years.value.find(item => String(item.id) === String(academicYearId.value)))
const treeData = computed<Array<VirtualTreeNode>>(() => {
  if (!currentSchool.value) return []
  const children: Array<VirtualTreeNode | EducationClassTreeNode> = currentYear.value
    ? [{ id: `year-${academicYearId.value}`, className: currentYear.value.yearName || '当前学年', nodeType: 'ACADEMIC_YEAR', classCode: '', branchCode: '', children: nodes.value }]
    : nodes.value
  return [{ id: `school-${schoolId.value}`, className: currentSchool.value.schoolName || '当前学校', nodeType: 'SCHOOL', classCode: currentSchool.value.schoolCode || '', branchCode: '', children }]
})
const flatRows = computed(() => flatten(nodes.value))
const filteredRows = computed(() => { const key = listKeyword.value.trim(); return key ? flatRows.value.filter(item => item.className.includes(key)) : flatRows.value })
const listTitle = computed(() => selectedName.value ? `${selectedName.value} · 班级列表` : '班级列表')

watch(treeKeyword, value => treeRef.value?.filter(value))
onMounted(async () => { const schoolResult = await listOrganizationTree(0); organizations.value = schoolResult.data || []; schools.value = schoolOptions(organizations.value); if (schools.value.length === 1) schoolId.value = schools.value[0].id; if (schoolId.value) await handleSchoolChange() })

function emptyForm(): EducationClassTreeForm { return { schoolId: schoolId.value || '', academicYearId: academicYearId.value, className: '', nodeType: 'GRADE', branchCode: '', cohortYear: new Date().getFullYear(), classRole: 'NORMAL', sort: 0, status: 0, remark: '' } }
function emptyBatchForm(): EducationClassTreeRange { return { schoolId: schoolId.value || '', academicYearId: academicYearId.value || '', nodeType: 'GRADE', cohortYear: new Date().getFullYear(), startNo: 1, endNo: 1, status: 0 } }
function flatten(source: EducationClassTreeNode[]) { const result: EducationClassTreeNode[] = []; const stack = [...source].reverse(); while (stack.length) { const node = stack.pop()!; result.push(node); stack.push(...(node.children || []).slice().reverse()) }; return result }
function label(value: TeachingNodeType | VirtualTreeNode['nodeType']) { return value === 'SCHOOL' ? '学校' : value === 'ACADEMIC_YEAR' ? '学年' : value === 'GRADE' ? '年级' : value === 'MAJOR' ? '专业/分组' : '班级' }
function tagType(value: TeachingNodeType | VirtualTreeNode['nodeType']): 'primary' | 'warning' | 'success' | undefined { return value === 'SCHOOL' || value === 'ACADEMIC_YEAR' ? 'primary' : value === 'GRADE' ? 'warning' : value === 'MAJOR' ? 'success' : undefined }
function filterNode(value: string, data: unknown) { const node = data as VirtualTreeNode | EducationClassTreeNode; return !value || node.className.includes(value) }
function selectNode(node: VirtualTreeNode | EducationClassTreeNode) { selectedId.value = node.id; selectedName.value = node.className }
async function loadTree() { if (!schoolId.value) { nodes.value = []; return }; loading.value = true; try { const response = await listClassTree({ schoolId: schoolId.value, academicYearId: academicYearId.value }); nodes.value = response.data || [] } finally { loading.value = false } }
async function loadYears() { if (!schoolId.value) { years.value = []; academicYearId.value = undefined; return }; const response = await listAcademicYears({ schoolId: schoolId.value, pageNum: 1, pageSize: 100 }); years.value = response.data?.rows || []; const active = years.value.find(item => item.status === 'ACTIVE') || years.value[0]; academicYearId.value = active?.id }
async function handleSchoolChange() { await loadYears(); await loadTree() }
function openAdd(parent?: EducationClassTreeNode) { if (!academicYearId.value) { ElMessage.warning('请先在学年管理为当前学校新增学年'); return }; const nodeType: TeachingNodeType = parent ? 'CLASS' : 'GRADE'; Object.assign(form, emptyForm(), { schoolId: schoolId.value!, academicYearId: academicYearId.value!, parentId: parent?.id, nodeType }); delete form.id; parentName.value = parent?.className || ''; title.value = parent ? `新增「${parent.className}」的下级` : '新增年级'; visible.value = true }
function openEdit(node: EducationClassTreeNode) { Object.assign(form, emptyForm(), { id: node.id, schoolId: node.schoolId, parentId: node.parentId, academicYearId: node.academicYearId, className: node.className, nodeType: node.nodeType, branchCode: node.branchCode, cohortYear: node.cohortYear, sort: node.sort, status: node.status }); parentName.value = findName(node.parentId) || ''; title.value = `编辑「${node.className}」`; visible.value = true }
function findName(id?: string | number | null) { return id === null || id === undefined ? undefined : flatRows.value.find(node => String(node.id) === String(id))?.className }
async function syncDialogSchool() { schoolId.value = form.schoolId; await handleSchoolChange(); form.academicYearId = academicYearId.value }
function handleNodeTypeChange(type: TeachingNodeType) { if (type !== 'GRADE') { form.branchCode = ''; form.cohortYear = undefined }; formRef.value?.clearValidate(['branchCode', 'cohortYear']) }
function openBatch() { if (!academicYearId.value) { ElMessage.warning('请先在学年管理为当前学校新增学年'); return }; Object.assign(batchForm, emptyBatchForm(), { schoolId: schoolId.value!, academicYearId: academicYearId.value! }); batchVisible.value = true }
async function submitBatch() { if (batchForm.nodeType === 'CLASS' && !batchForm.parentId) { ElMessage.warning('请先选择年级或专业分组'); return }; if (batchForm.startNo < 1 || batchForm.endNo < batchForm.startNo) { ElMessage.warning('请填写从 1 开始的递增序号范围'); return }; submitting.value = true; try { const response = await batchCreateClassTreeNodes(batchForm); ElMessage.success(`已创建 ${response.data || 0} 条`); batchVisible.value = false; await loadTree() } finally { submitting.value = false } }
async function submit() { if (!formRef.value) return; await formRef.value.validate(); submitting.value = true; try { if (form.id) await updateClassTreeNode(form); else await addClassTreeNode(form); ElMessage.success(form.id ? '修改成功' : '新增成功'); visible.value = false; await loadTree() } finally { submitting.value = false } }
async function remove(node: EducationClassTreeNode) { await ElMessageBox.confirm(`确认删除「${node.className}」吗？删除年级、专业分组等树节点前，必须先删除其下级班级及人员归属。`, '删除确认', { type: 'warning' }); await removeClassTreeNodes([node.id]); ElMessage.success('删除成功'); await loadTree() }
</script>

<style scoped>
.operation-page { min-height:100%; padding:20px; background:#f7f8fa; }.page-header,.list-header { display:flex; align-items:center; justify-content:space-between; gap:16px; }.page-header { margin-bottom:16px; }.page-header > div:last-child { display:flex; gap:8px; }.page-header h2 { margin:0; color:#111827; font-size:20px; line-height:28px; }.page-header p { margin:4px 0 0; color:#667085; font-size:13px; }.operation-split { display:grid; grid-template-columns:260px minmax(0,1fr); gap:16px; align-items:start; }.content-stack { display:flex; min-width:0; flex-direction:column; gap:16px; }.tree-card { min-height:560px; }.tree-card,.filter-card,.list-card { border-color:#edf0f5; border-radius:12px; box-shadow:0 2px 8px rgba(16,24,40,.04); }.card-title { color:#1d2939; font-size:15px; font-weight:600; }.tree-card :deep(.el-card__header),.list-card :deep(.el-card__header) { padding:16px 20px; border-bottom-color:#edf0f5; }.tree-card :deep(.el-card__body) { padding:14px; }.tree-search { margin-bottom:12px; }.tree-node { display:flex; width:100%; min-width:0; align-items:center; justify-content:space-between; gap:8px; padding:0 6px; border-radius:4px; }.tree-node.active,.tree-node:hover { background:#eff6ff; }.tree-node-name { overflow:hidden; color:#344054; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }.filter-card :deep(.el-card__body) { padding:18px 20px 4px; }.filter-card :deep(.el-form-item) { margin-bottom:14px; }.filter-card :deep(.el-select),.filter-card :deep(.el-input) { width:180px; }.range-separator { margin:0 8px; color:#667085; }.list-header > div { display:flex; align-items:center; gap:8px; }.list-card :deep(.el-card__body) { padding:0 20px 12px; }.list-card :deep(.el-table th.el-table__cell) { color:#475467; background:#f8fafc; font-weight:600; }.table-footer { padding-top:14px; color:#98a2b3; font-size:13px; text-align:right; }@media(max-width:900px) { .operation-page { padding:12px; }.operation-split { grid-template-columns:1fr; }.tree-card { min-height:auto; }.page-header { align-items:flex-start; flex-direction:column; }.filter-card :deep(.el-select),.filter-card :deep(.el-input) { width:100%; } }
</style>
