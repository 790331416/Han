<template>
  <div class="operation-page">
    <section class="page-header">
      <div><h2>区域管理</h2><p>维护教育局与学校归属区域，区域编码由系统按名称生成。</p></div>
      <el-button type="primary" :icon="Plus" @click="openAdd()">新增根区域</el-button>
    </section>
    <el-alert type="info" :closable="false" show-icon class="hint">区域用于教育局、学校的归属和区域管理员授权；全国基准区域不允许修改。</el-alert>
    <section class="region-panel">
      <div class="panel-toolbar">
        <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索已加载节点" class="filter-input" @keyup.enter="loadTree" />
        <div class="toolbar-right"><el-tag type="info" effect="plain">根区域 {{ nodes.length }} 个</el-tag><el-button :icon="Refresh" @click="loadTree()">刷新</el-button></div>
      </div>
      <div class="tree-scroll">
        <el-tree ref="treeRef" v-loading="loading" :data="nodes" node-key="id" lazy :load="loadChildren" :expand-on-click-node="false" :props="treeProps" :filter-node-method="filterNode">
          <template #default="{ data }">
            <div class="tree-node">
              <div class="node-main"><span class="node-name">{{ data.regionName }}</span><el-tag v-if="data.sourceSystem === 'NATIONAL'" size="small" type="info">全国基准</el-tag><el-tag v-else-if="data.status !== 0" size="small" type="info">停用</el-tag></div>
              <div class="node-actions">
                <el-tooltip content="新增下级" placement="top"><el-button text type="primary" :icon="Plus" aria-label="新增下级" @click.stop="openAdd(data)" /></el-tooltip>
                <el-tooltip v-if="data.sourceSystem !== 'NATIONAL'" content="编辑" placement="top"><el-button text type="primary" :icon="Edit" aria-label="编辑" @click.stop="openEdit(data)" /></el-tooltip>
                <el-tooltip v-if="data.sourceSystem !== 'NATIONAL'" content="删除" placement="top"><el-button text type="danger" :icon="Delete" aria-label="删除" @click.stop="remove(data)" /></el-tooltip>
              </div>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!loading && !nodes.length" description="暂无区域，请先新增根区域" :image-size="72" />
      </div>
    </section>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级区域"><el-input :model-value="parentName || '无（根节点）'" disabled /></el-form-item>
        <el-form-item label="区域名称" prop="regionName"><el-input v-model="form.regionName" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="区域层级" prop="regionLevel"><el-select v-model="form.regionLevel" allow-create filterable default-first-option style="width: 100%"><el-option label="省" value="PROVINCE" /><el-option label="市" value="CITY" /><el-option label="区县" value="DISTRICT" /><el-option label="项目区域" value="PROJECT" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" style="width: 100%"><el-option label="正常" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type LoadFunction, type TreeInstance } from 'element-plus'
import { addRegion, listRegionChildren, removeRegions, updateRegion, type EduRegionOption, type EducationRegionForm } from '@/api/education'

const treeRef = ref<TreeInstance>()
const formRef = ref<FormInstance>()
type LazyRegionNode = EduRegionOption & { children?: LazyRegionNode[]; leaf: boolean }
const nodes = ref<LazyRegionNode[]>([])
const keyword = ref('')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const parentName = ref('')
const treeProps = { label: 'regionName', children: 'children', isLeaf: 'leaf' }
const form = reactive<EducationRegionForm>(emptyForm())
const rules: FormRules = { regionName: [{ required: true, message: '请输入区域名称', trigger: 'blur' }], regionLevel: [{ required: true, message: '请选择区域层级', trigger: 'change' }] }

watch(keyword, value => treeRef.value?.filter(value))
loadTree()

function emptyForm(): EducationRegionForm { return { regionName: '', regionLevel: 'PROJECT', sort: 0, status: 0, remark: '' } }
function toLazyNodes(items: EduRegionOption[]): LazyRegionNode[] { return items.map(item => ({ ...item, leaf: (item.nodeLevel || 0) >= 3 })) }
function filterNode(value: string, data: unknown) { const node = data as LazyRegionNode; return !value || node.regionName.includes(value) }
async function loadTree(selectedId?: string | number) {
  loading.value = true
  try { const response = await listRegionChildren(); nodes.value = toLazyNodes(response.data || []); await nextTick(); if (selectedId) treeRef.value?.setCurrentKey(selectedId) } finally { loading.value = false }
}
const loadChildren: LoadFunction = (node, resolve) => {
  if (node.level === 0) { resolve(nodes.value); return }
  const data = node.data as LazyRegionNode
  void listRegionChildren(data.id).then(response => resolve(toLazyNodes(response.data || []))).catch(() => resolve([]))
}
function openAdd(parent?: LazyRegionNode) { Object.assign(form, emptyForm(), { parentId: parent?.id }); delete form.id; parentName.value = parent?.regionName || ''; dialogTitle.value = parent ? `新增「${parent.regionName}」的下级区域` : '新增根区域'; dialogVisible.value = true }
function openEdit(node: LazyRegionNode) { Object.assign(form, emptyForm(), node); parentName.value = findName(node.parentId) || ''; dialogTitle.value = `编辑「${node.regionName}」`; dialogVisible.value = true }
function findName(id?: string | number | null): string | undefined { if (id == null) return undefined; const stack = [...nodes.value]; while (stack.length) { const node = stack.pop()!; if (String(node.id) === String(id)) return node.regionName; stack.push(...(node.children || [])) } return undefined }
async function submit() { if (!formRef.value) return; await formRef.value.validate(); submitting.value = true; try { const response = form.id ? await updateRegion(form) : await addRegion(form); ElMessage.success(form.id ? '修改成功' : '新增成功'); dialogVisible.value = false; await loadTree(response.data || form.id) } finally { submitting.value = false } }
async function remove(node: LazyRegionNode) { await ElMessageBox.confirm(`确认删除区域「${node.regionName}」吗？如存在下级区域、教育组织或数据范围授权，系统会拒绝删除。`, '删除确认', { type: 'warning' }); await removeRegions([node.id]); ElMessage.success('删除成功'); await loadTree() }
</script>

<style scoped>
.operation-page { min-height: 100%; padding: 20px; background: #f7f8fa; }.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }.page-header h2 { margin: 0; color: #111827; font-size: 20px; line-height: 28px; }.page-header p { margin: 4px 0 0; color: #667085; font-size: 13px; }.hint { margin-bottom: 16px; border-color: #dbeafe; }.region-panel { overflow: hidden; border: 1px solid #e4e7ec; border-radius: 8px; background: #fff; box-shadow: 0 2px 8px rgb(16 24 40 / 4%); }.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 20px; border-bottom: 1px solid #edf0f5; }.filter-input { width: min(360px, 100%); }.toolbar-right { display: flex; align-items: center; gap: 12px; }.tree-scroll { min-height: 360px; max-height: calc(100vh - 300px); overflow: auto; padding: 12px 20px 20px; }.tree-node { display: flex; width: min(100%, 760px); min-width: 0; align-items: center; justify-content: space-between; gap: 16px; min-height: 34px; padding: 2px 8px; border-radius: 4px; }.tree-node:hover { background: #f5f8ff; }.node-main { display: inline-flex; min-width: 0; align-items: center; gap: 8px; }.node-name { overflow: hidden; color: #344054; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.node-actions { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 2px; }.node-actions :deep(.el-button) { width: 28px; height: 28px; margin-left: 0; }.tree-scroll :deep(.el-tree-node__content) { min-height: 38px; }.tree-scroll :deep(.el-tree-node__expand-icon) { color: #98a2b3; }@media (max-width: 700px) { .operation-page { padding: 12px; }.page-header { align-items: flex-start; flex-direction: column; }.panel-toolbar { align-items: stretch; flex-direction: column; }.toolbar-right { justify-content: space-between; }.tree-scroll { max-height: calc(100vh - 350px); padding: 10px 12px 16px; }.tree-node { width: 100%; }.node-name { max-width: 48vw; } }
</style>
