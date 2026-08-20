<template>
  <div class="operation-page">
    <section class="page-header">
      <div>
        <h2>学校管理</h2>
        <p>教育局、学校和校区使用同一棵组织树；学校编号创建后保持稳定。</p>
      </div>
      <el-button v-if="userStore.hasPermission('education:school:add')" type="primary" :icon="Plus" @click="openAdd()">新增组织</el-button>
    </section>

    <div class="operation-split">
      <el-card shadow="never" class="tree-card">
        <template #header><span class="card-title">教育组织</span></template>
        <el-input v-model="treeKeyword" clearable placeholder="搜索树节点" :prefix-icon="Search" class="tree-search" />
        <el-tree ref="treeRef" v-loading="loading" :data="nodes" node-key="id" :props="treeProps" :filter-node-method="filterNode" :expand-on-click-node="false" highlight-current default-expand-all @node-click="selectNode">
          <template #default="{ data }">
            <div class="tree-node" :class="{ active: String(selectedId) === String(data.id) }">
              <span class="tree-node-name">{{ data.schoolName }}</span>
              <el-tag size="small" :type="data.orgType === 'EDU_BUREAU' ? 'warning' : 'success'">{{ organizationLabel(data.orgType) }}</el-tag>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!loading && !nodes.length" description="暂无教育组织，请先新增组织" :image-size="72" />
      </el-card>

      <div class="content-stack">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="关键字"><el-input v-model="listKeyword" clearable placeholder="名称" /></el-form-item>
            <el-form-item><el-button type="primary" :icon="Search">搜索</el-button><el-button @click="listKeyword = ''">重置</el-button></el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="list-header"><span class="card-title">{{ listTitle }}</span><div><el-tag type="primary" effect="light">共 {{ filteredRows.length }} 条</el-tag><el-button text :icon="Refresh" @click="loadTree(selectedId)">刷新</el-button></div></div>
          </template>
          <el-table v-loading="loading" :data="filteredRows" height="500">
            <el-table-column prop="schoolName" label="组织名称" min-width="180" />
            <el-table-column label="机构类型" min-width="110"><template #default="{ row }">{{ organizationLabel(row.orgType) }}</template></el-table-column>
            <el-table-column label="学校类型" min-width="120"><template #default="{ row }">{{ schoolTypeLabel(row.schoolManageType) }}</template></el-table-column>
            <el-table-column prop="regionName" label="区域关联" min-width="130"><template #default="{ row }">{{ regionLabel(row.regionId, row.regionCode, row.regionName) }}</template></el-table-column>
            <el-table-column label="状态" min-width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button v-if="userStore.hasPermission('education:school:add')" link type="primary" @click="openAdd(row)">新增下级</el-button><el-button v-if="userStore.hasPermission('education:school:edit')" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="userStore.hasPermission('education:school:remove')" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
          </el-table>
          <div class="table-footer">共 {{ filteredRows.length }} 条记录</div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="上级组织"><el-input :model-value="parentName || '无（根组织）'" disabled /></el-form-item>
        <el-form-item label="组织名称" prop="schoolName"><el-input v-model="form.schoolName" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="机构类型" prop="orgType"><el-select v-model="form.orgType" :disabled="Boolean(form.id)" style="width: 100%"><el-option label="教育局" value="EDU_BUREAU" /><el-option label="学校" value="SCHOOL" /></el-select></el-form-item>
        <el-form-item v-if="form.orgType === 'SCHOOL'" label="学校类型"><el-select v-model="form.schoolManageType" clearable style="width: 100%"><el-option label="中心校" value="CENTER" /><el-option label="校区" value="CAMPUS" /><el-option label="独立学校" value="INDEPENDENT" /></el-select></el-form-item>
        <el-form-item v-if="form.orgType === 'SCHOOL'" label="办学学制"><el-select v-model="form.schoolProperty" clearable style="width: 100%"><el-option v-for="item in schoolPropertyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="区域关联" prop="regionId"><EducationRegionSelector v-model="form.regionId" style="width: 100%" /></el-form-item>
        <el-form-item v-if="form.orgType === 'SCHOOL'" label="允许自动升级"><el-switch v-model="form.autoUpgradeEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-select v-model="form.status" style="width: 100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { addOrganization, listOrganizationTree, removeOrganizations, updateOrganization, type EducationOrganizationForm, type EducationOrganizationNode, type OrganizationType } from '@/api/education'
import EducationRegionSelector from '@/components/education/EducationRegionSelector.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const treeRef = ref<TreeInstance>()
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const treeKeyword = ref('')
const listKeyword = ref('')
const selectedId = ref<string | number>()
const selectedName = ref('')
const nodes = ref<EducationOrganizationNode[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const parentName = ref('')
const treeProps = { label: 'schoolName', children: 'children' }
const schoolPropertyOptions = [{ label: '幼儿园', value: '1' }, { label: '小学', value: '2' }, { label: '初中', value: '3' }, { label: '高中', value: '4' }, { label: '九年制', value: '5' }, { label: '小学附属幼儿园', value: '6' }, { label: '完全中学', value: '7' }, { label: '十二年制学校', value: '8' }, { label: '完全学校（幼到高）', value: '9' }, { label: '幼小初', value: '10' }]
const rules: FormRules = { schoolName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }], orgType: [{ required: true, message: '请选择机构类型', trigger: 'change' }], regionId: [{ validator: (_rule, value, callback) => form.orgType !== 'SCHOOL' || value ? callback() : callback(new Error('请选择区域关联')), trigger: 'change' }] }
const form = reactive<EducationOrganizationForm>(emptyForm())
const flatRows = computed(() => flatten(nodes.value))
const selectedRows = computed(() => selectedId.value === undefined ? flatRows.value : flatten(findSubtree(nodes.value, selectedId.value)))
const filteredRows = computed(() => { const key = listKeyword.value.trim(); return key ? selectedRows.value.filter(item => item.schoolName.includes(key)) : selectedRows.value })
const listTitle = computed(() => selectedName.value ? `${selectedName.value} · 组织列表` : '学校列表')

watch(treeKeyword, value => treeRef.value?.filter(value))
void loadTree()

function emptyForm(): EducationOrganizationForm { return { schoolName: '', orgType: 'SCHOOL', schoolManageType: '', schoolProperty: '', regionId: undefined, autoUpgradeEnabled: 1, status: 0, remark: '' } }
function flatten(source: EducationOrganizationNode[]) { const result: EducationOrganizationNode[] = []; const stack = [...source].reverse(); while (stack.length) { const node = stack.pop()!; result.push(node); stack.push(...(node.children || []).slice().reverse()) }; return result }
function findSubtree(source: EducationOrganizationNode[], id: string | number): EducationOrganizationNode[] { const node = flatten(source).find(item => String(item.id) === String(id)); return node ? [node] : [] }
function organizationLabel(type: OrganizationType) { return type === 'EDU_BUREAU' ? '教育局' : '学校' }
function schoolTypeLabel(value?: string) { return ({ CENTER: '中心校', CAMPUS: '校区', INDEPENDENT: '独立学校' } as Record<string, string>)[value || ''] || '—' }
function regionLabel(_id?: string | number | null, _code?: string, name?: string) { return name || '—' }
function filterNode(value: string, data: unknown) { const node = data as EducationOrganizationNode; return !value || node.schoolName.includes(value) }
function selectNode(node: EducationOrganizationNode) { selectedId.value = node.id; selectedName.value = node.schoolName }
async function loadTree(currentId?: string | number) { loading.value = true; try { const response = await listOrganizationTree(); nodes.value = response.data || []; await nextTick(); if (currentId) treeRef.value?.setCurrentKey(currentId) } finally { loading.value = false } }
function openAdd(parent?: EducationOrganizationNode) { Object.assign(form, emptyForm(), { parentId: parent?.id }); delete form.id; parentName.value = parent?.schoolName || ''; if (parent?.orgType === 'SCHOOL') form.orgType = 'SCHOOL'; dialogTitle.value = parent ? `新增「${parent.schoolName}」的下级组织` : '新增组织'; dialogVisible.value = true }
function openEdit(node: EducationOrganizationNode) { Object.assign(form, emptyForm(), { id: node.id, parentId: node.parentId, schoolName: node.schoolName, orgType: node.orgType, schoolManageType: node.schoolManageType, schoolProperty: node.schoolProperty, regionId: node.regionId, autoUpgradeEnabled: node.autoUpgradeEnabled, status: node.status }); parentName.value = findName(node.parentId) || ''; dialogTitle.value = `编辑「${node.schoolName}」`; dialogVisible.value = true }
function findName(id?: string | number | null): string | undefined { if (id === null || id === undefined) return undefined; return flatRows.value.find(item => String(item.id) === String(id))?.schoolName }
async function submit() { if (!formRef.value) return; await formRef.value.validate(); submitting.value = true; try { const response = form.id ? await updateOrganization(form) : await addOrganization(form); ElMessage.success(form.id ? '修改成功' : '新增成功'); dialogVisible.value = false; await loadTree(response.data || form.id) } finally { submitting.value = false } }
async function remove(node: EducationOrganizationNode) { await ElMessageBox.confirm(`确认删除「${node.schoolName}」吗？若存在下级组织、班级、人员、场所或设备，系统会拒绝删除。`, '删除确认', { type: 'warning' }); await removeOrganizations([node.id]); ElMessage.success('删除成功'); await loadTree() }
</script>

<style scoped>
.operation-page { min-height: 100%; padding: 20px; background: #f7f8fa; }.page-header,.list-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.page-header { margin-bottom: 16px; }.page-header h2 { margin: 0; color: #111827; font-size: 20px; line-height: 28px; }.page-header p { margin: 4px 0 0; color: #667085; font-size: 13px; }.operation-split { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; align-items: start; }.content-stack { display: flex; min-width: 0; flex-direction: column; gap: 16px; }.tree-card { min-height: 560px; }.tree-card,.filter-card,.list-card { border-color: #edf0f5; border-radius: 12px; box-shadow: 0 2px 8px rgba(16, 24, 40, .04); }.card-title { color: #1d2939; font-size: 15px; font-weight: 600; }.tree-card :deep(.el-card__header),.list-card :deep(.el-card__header) { padding: 16px 20px; border-bottom-color: #edf0f5; }.tree-card :deep(.el-card__body) { padding: 14px; }.tree-search { margin-bottom: 12px; }.tree-node { display: flex; width: 100%; min-width: 0; align-items: center; justify-content: space-between; gap: 8px; padding: 0 6px; border-radius: 4px; }.tree-node.active,.tree-node:hover { background: #eff6ff; }.tree-node-name { overflow: hidden; color: #344054; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.filter-card :deep(.el-card__body) { padding: 18px 20px 4px; }.filter-card :deep(.el-form-item) { margin-bottom: 14px; }.filter-card :deep(.el-input) { width: 220px; }.list-header > div { display: flex; align-items: center; gap: 8px; }.list-card :deep(.el-card__body) { padding: 0 20px 12px; }.list-card :deep(.el-table th.el-table__cell) { color: #475467; background: #f8fafc; font-weight: 600; }.table-footer { padding-top: 14px; color: #98a2b3; font-size: 13px; text-align: right; }@media (max-width: 900px) { .operation-page { padding: 12px; }.operation-split { grid-template-columns: 1fr; }.tree-card { min-height: auto; }.page-header { align-items: flex-start; flex-direction: column; }.filter-card :deep(.el-input) { width: 100%; } }
</style>
