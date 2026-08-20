<template>
  <div class="operation-page">
    <section class="page-header"><div><h2>教室管理</h2><p>按学校维护建筑、楼层和场所；设备只挂接到最末级场所。</p></div><div><el-button :disabled="!schoolId" @click="openBatch">批量创建楼层</el-button><el-button type="primary" :icon="Plus" :disabled="!schoolId" @click="openAdd()">新增建筑</el-button></div></section>
    <div class="operation-split">
      <el-card shadow="never" class="tree-card">
        <template #header><span class="card-title">校园场所树</span></template>
        <el-input v-model="treeKeyword" clearable placeholder="搜索树节点" :prefix-icon="Search" class="tree-search" />
        <el-tree ref="treeRef" v-loading="loading" :data="treeData" node-key="id" :props="{ label: 'roomName', children: 'children' }" :filter-node-method="filterNode" default-expand-all :expand-on-click-node="false" highlight-current @node-click="selectNode">
          <template #default="{ data }"><div class="tree-node" :class="{ active: String(selectedId) === String(data.id) }"><span class="tree-node-name">{{ data.roomName }}</span><el-tag size="small" :type="tag(data.nodeType)">{{ label(data.nodeType) }}</el-tag></div></template>
        </el-tree>
        <el-empty v-if="schoolId && !loading && !nodes.length" description="暂无建筑，点击“新增建筑”创建默认教学楼" :image-size="72" />
        <el-empty v-if="!schoolId && !loading" description="暂无可管理学校" :image-size="72" />
      </el-card>
      <div class="content-stack">
        <el-card shadow="never" class="filter-card"><el-form :inline="true" @submit.prevent><el-form-item label="学校"><EducationSchoolSelector v-model="schoolId" :nodes="organizations" @change="load" /></el-form-item><el-form-item label="关键字"><el-input v-model="listKeyword" clearable placeholder="名称 / 别名" /></el-form-item><el-form-item><el-button type="primary" :icon="Search" @click="load">搜索</el-button><el-button @click="listKeyword = ''; load()">重置</el-button></el-form-item></el-form></el-card>
        <el-card shadow="never" class="list-card">
          <template #header><div class="list-header"><span class="card-title">{{ listTitle }}</span><div><el-tag type="primary" effect="light">共 {{ filteredRows.length }} 条</el-tag><el-button text :icon="Refresh" @click="load">刷新</el-button></div></div></template>
          <el-table v-loading="loading" :data="filteredRows" height="500"><el-table-column prop="roomName" label="场所名称" min-width="220" /><el-table-column label="节点类型" min-width="100"><template #default="{ row }">{{ label(row.nodeType) }}</template></el-table-column><el-table-column prop="sort" label="排序" width="80" /><el-table-column prop="aliasName" label="别名" min-width="130"><template #default="{ row }">{{ row.aliasName || '—' }}</template></el-table-column><el-table-column prop="roomType" label="场所类型" min-width="120"><template #default="{ row }">{{ roomTypeLabel(row.roomType) }}</template></el-table-column><el-table-column label="状态" min-width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="row.nodeType === 'PLACE'" @click="openAdd(row)">新增下级</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="userStore.hasPermission('education:room:remove')" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
          <div class="table-footer">共 {{ filteredRows.length }} 条记录</div>
        </el-card>
      </div>
    </div>
    <el-dialog v-model="visible" :title="title" width="540px" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-width="100px"><el-form-item label="上级节点"><el-input :model-value="parentName" disabled /></el-form-item><el-form-item label="名称" prop="roomName"><el-input v-model="form.roomName" maxlength="128" show-word-limit /></el-form-item><el-form-item label="节点类型" prop="nodeType"><el-select v-model="form.nodeType" :disabled="Boolean(form.id)" style="width:100%"><el-option label="建筑" value="BUILDING" /><el-option label="楼层" value="FLOOR" /><el-option label="场所" value="PLACE" /></el-select></el-form-item><template v-if="form.nodeType === 'PLACE'"><el-form-item label="别名"><el-input v-model="form.aliasName" placeholder="例如：音乐教室" /></el-form-item><el-form-item label="场所类型"><el-select v-model="form.roomType" clearable style="width:100%"><el-option v-for="item in types" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="容纳人数"><el-input-number v-model="form.capacity" :min="1" controls-position="right" /></el-form-item></template><el-form-item label="排序值" prop="sort"><el-input-number v-model="form.sort" :min="0" controls-position="right" style="width:100%" /></el-form-item><el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确定</el-button></template></el-dialog>
    <el-dialog v-model="batchVisible" title="批量创建楼层" width="480px" destroy-on-close><el-form :model="batchForm" label-width="100px"><el-form-item label="学校"><EducationSchoolSelector v-model="batchForm.schoolId" :nodes="organizations" disabled /></el-form-item><el-form-item label="建筑" required><EducationPlaceSelector v-model="batchForm.buildingId" :school-id="batchForm.schoolId" :selectable-types="['BUILDING']" /></el-form-item><el-form-item label="楼层范围" required><el-input-number v-model="batchForm.startNo" :min="1" controls-position="right" /><span class="range-separator">至</span><el-input-number v-model="batchForm.endNo" :min="batchForm.startNo" controls-position="right" /></el-form-item><el-form-item label="状态"><el-select v-model="batchForm.status" style="width:100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item></el-form><template #footer><el-button @click="batchVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitBatch">确定创建</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { addPlaceTreeNode, batchCreateFloors, listOrganizationTree, listPlaceTree, removePlaceTreeNodes, updatePlaceTreeNode, type EducationFloorRange, type EducationOrganizationNode, type EducationPlaceTreeForm, type EducationPlaceTreeNode, type PlaceNodeType } from '@/api/education'
import { useUserStore } from '@/stores/user'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'
import EducationPlaceSelector from '@/components/education/EducationPlaceSelector.vue'
import { schoolOptions, type SchoolOption } from '@/utils/education-school-tree'

type SchoolRootNode = { id: string; schoolId: string | number; roomName: string; roomCode: string; nodeType: 'SCHOOL'; status: number; children: EducationPlaceTreeNode[]; isSchoolRoot: true }
const userStore = useUserStore()
const organizations = ref<EducationOrganizationNode[]>([])
const schools = ref<SchoolOption[]>([])
const nodes = ref<EducationPlaceTreeNode[]>([])
const schoolId = ref<string | number>()
const treeKeyword = ref('')
const listKeyword = ref('')
const selectedId = ref<string | number>()
const selectedName = ref('')
const treeRef = ref<TreeInstance>()
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const batchVisible = ref(false)
const title = ref('')
const parentName = ref('')
const formRef = ref<FormInstance>()
const types = ['普通教室', '专用教室', '公共区域', '会议室', '办公室', '食堂', '大门', '保安室', '其它']
const rules: FormRules = { roomName: [{ required: true, message: '请输入名称', trigger: 'blur' }], nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }] }
const form = reactive<EducationPlaceTreeForm>(emptyForm())
const batchForm = reactive<EducationFloorRange>(emptyBatchForm())
const currentSchool = computed(() => schools.value.find(item => String(item.id) === String(schoolId.value)))
const treeData = computed<SchoolRootNode[]>(() => schoolId.value && currentSchool.value ? [{ id: `school-${schoolId.value}`, schoolId: schoolId.value, roomName: currentSchool.value.schoolName || '当前学校', roomCode: currentSchool.value.schoolCode || '', nodeType: 'SCHOOL', status: 0, children: nodes.value, isSchoolRoot: true }] : [])
const flatRows = computed(() => flatten(nodes.value))
const selectedRows = computed(() => {
  if (selectedId.value === undefined || String(selectedId.value).startsWith('school-')) return flatRows.value
  return flatten(findSubtree(nodes.value, selectedId.value))
})
const filteredRows = computed(() => { const key = listKeyword.value.trim(); return key ? selectedRows.value.filter(item => `${item.roomName}${item.aliasName || ''}`.includes(key)) : selectedRows.value })
const listTitle = computed(() => selectedName.value ? `${selectedName.value} · 场所列表` : '场所列表')

watch(treeKeyword, value => treeRef.value?.filter(value))
onMounted(async () => { const response = await listOrganizationTree(0); organizations.value = response.data || []; schools.value = schoolOptions(organizations.value); if (schools.value.length === 1) { schoolId.value = schools.value[0].id; await load() } })

function emptyForm(): EducationPlaceTreeForm { return { schoolId: schoolId.value || '', roomName: '', nodeType: 'BUILDING', aliasName: '', roomType: '', sort: 0, status: 0 } }
function emptyBatchForm(): EducationFloorRange { return { schoolId: schoolId.value || '', buildingId: '', startNo: 1, endNo: 1, status: 0 } }
function flatten(source: EducationPlaceTreeNode[]) { const result: EducationPlaceTreeNode[] = []; const stack = [...source].reverse(); while (stack.length) { const node = stack.pop()!; result.push(node); stack.push(...(node.children || []).slice().reverse()) }; return result }
function findSubtree(source: EducationPlaceTreeNode[], id: string | number): EducationPlaceTreeNode[] { const node = flatten(source).find(item => String(item.id) === String(id)); return node ? [node] : [] }
function label(value: PlaceNodeType | 'SCHOOL') { return value === 'SCHOOL' ? '学校' : value === 'BUILDING' ? '建筑' : value === 'FLOOR' ? '楼层' : '场所' }
function roomTypeLabel(value?: string) { return ({ LIVE: '直播教室', ATTEND: '听讲教室', CLASSROOM: '普通教室' } as Record<string, string>)[value || ''] || value || '—' }
function tag(value: PlaceNodeType | 'SCHOOL'): 'primary' | 'warning' | 'success' | undefined { return value === 'SCHOOL' ? 'primary' : value === 'BUILDING' ? 'warning' : value === 'FLOOR' ? 'success' : undefined }
function filterNode(value: string, data: unknown) { const node = data as SchoolRootNode | EducationPlaceTreeNode; return !value || node.roomName.includes(value) }
function selectNode(node: SchoolRootNode | EducationPlaceTreeNode) { selectedId.value = node.id; selectedName.value = node.roomName }
async function load() { if (!schoolId.value) { nodes.value = []; selectedId.value = undefined; selectedName.value = ''; return }; loading.value = true; try { const response = await listPlaceTree({ schoolId: schoolId.value }); nodes.value = response.data || []; if (selectedId.value !== undefined && !String(selectedId.value).startsWith('school-') && !flatRows.value.some(item => String(item.id) === String(selectedId.value))) { selectedId.value = undefined; selectedName.value = '' } } finally { loading.value = false } }
function openAdd(parent?: EducationPlaceTreeNode) { const nodeType: PlaceNodeType = parent?.nodeType === 'BUILDING' ? 'FLOOR' : parent ? 'PLACE' : 'BUILDING'; Object.assign(form, emptyForm(), { schoolId: schoolId.value!, parentId: parent?.id, roomName: parent ? '' : '教学楼', nodeType }); delete form.id; parentName.value = parent?.roomName || currentSchool.value?.schoolName || '当前学校'; title.value = parent ? `新增「${parent.roomName}」的下级` : `新增「${parentName.value}」的建筑`; visible.value = true }
function openEdit(node: EducationPlaceTreeNode) { Object.assign(form, emptyForm(), { id: node.id, schoolId: node.schoolId, parentId: node.parentId, roomName: node.roomName, nodeType: node.nodeType, aliasName: node.aliasName, roomType: node.roomType, capacity: node.capacity, sort: node.sort, status: node.status }); parentName.value = findName(node.parentId) || currentSchool.value?.schoolName || '当前学校'; title.value = `编辑「${node.roomName}」`; visible.value = true }
function findName(id?: string | number | null) { return id === null || id === undefined ? undefined : flatRows.value.find(node => String(node.id) === String(id))?.roomName }
function openBatch() { Object.assign(batchForm, emptyBatchForm(), { schoolId: schoolId.value! }); batchVisible.value = true }
async function submitBatch() { if (!batchForm.buildingId) { ElMessage.warning('请先选择建筑'); return }; if (batchForm.startNo < 1 || batchForm.endNo < batchForm.startNo) { ElMessage.warning('请填写从 1 开始的递增楼层范围'); return }; saving.value = true; try { const response = await batchCreateFloors(batchForm); ElMessage.success(`已创建 ${response.data || 0} 个楼层`); batchVisible.value = false; await load() } finally { saving.value = false } }
async function save() { if (!formRef.value) return; await formRef.value.validate(); saving.value = true; try { if (form.id) await updatePlaceTreeNode(form); else await addPlaceTreeNode(form); ElMessage.success(form.id ? '修改成功' : '新增成功'); visible.value = false; await load() } finally { saving.value = false } }
async function remove(node: EducationPlaceTreeNode) { await ElMessageBox.confirm(`确认删除「${node.roomName}」吗？若仍有下级节点或设备，系统会拒绝删除。`, '删除确认', { type: 'warning' }); await removePlaceTreeNodes([node.id]); ElMessage.success('删除成功'); await load() }
</script>

<style scoped>
.operation-page { min-height:100%; padding:20px; background:#f7f8fa; }.page-header,.list-header { display:flex; align-items:center; justify-content:space-between; gap:16px; }.page-header { margin-bottom:16px; }.page-header > div:last-child { display:flex; gap:8px; }.page-header h2 { margin:0; color:#111827; font-size:20px; line-height:28px; }.page-header p { margin:4px 0 0; color:#667085; font-size:13px; }.operation-split { display:grid; grid-template-columns:260px minmax(0,1fr); gap:16px; align-items:start; }.content-stack { display:flex; min-width:0; flex-direction:column; gap:16px; }.tree-card { min-height:560px; }.tree-card,.filter-card,.list-card { border-color:#edf0f5; border-radius:12px; box-shadow:0 2px 8px rgba(16,24,40,.04); }.card-title { color:#1d2939; font-size:15px; font-weight:600; }.tree-card :deep(.el-card__header),.list-card :deep(.el-card__header) { padding:16px 20px; border-bottom-color:#edf0f5; }.tree-card :deep(.el-card__body) { padding:14px; }.tree-search { margin-bottom:12px; }.tree-node { display:flex; width:100%; min-width:0; align-items:center; justify-content:space-between; gap:8px; padding:0 6px; border-radius:4px; }.tree-node.active,.tree-node:hover { background:#eff6ff; }.tree-node-name { overflow:hidden; color:#344054; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }.filter-card :deep(.el-card__body) { padding:18px 20px 4px; }.filter-card :deep(.el-form-item) { margin-bottom:14px; }.filter-card :deep(.el-select),.filter-card :deep(.el-input) { width:200px; }.range-separator { margin:0 8px; color:#667085; }.list-header > div { display:flex; align-items:center; gap:8px; }.list-card :deep(.el-card__body) { padding:0 20px 12px; }.list-card :deep(.el-table th.el-table__cell) { color:#475467; background:#f8fafc; font-weight:600; }.table-footer { padding-top:14px; color:#98a2b3; font-size:13px; text-align:right; }@media(max-width:900px) { .operation-page { padding:12px; }.operation-split { grid-template-columns:1fr; }.tree-card { min-height:auto; }.page-header { align-items:flex-start; flex-direction:column; }.filter-card :deep(.el-select),.filter-card :deep(.el-input) { width:100%; } }
</style>
