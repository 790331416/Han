<template>
  <div class="operation-page">
    <section class="page-header"><div><h2>数据范围授权</h2><p>为有管理端权限的教师配置可管理的教育组织和区域。</p></div></section>
    <el-card shadow="never" class="selector-card">
      <el-alert type="info" :closable="false" show-icon class="hint">勾选教育局或区域时可覆盖下级节点；取消全部勾选即撤销该人员全部教育数据权限。</el-alert>
      <el-form inline @submit.prevent>
        <el-form-item label="管理人员"><el-select v-model="userId" filterable clearable :disabled="!isSuperAdmin" placeholder="选择有管理端权限的教师" class="user-select" @change="loadScopes"><el-option v-for="item in people" :key="item.userId" :value="String(item.userId)" :label="`${item.personName}（${item.phone || item.userId}）`" /></el-select></el-form-item>
        <el-form-item><el-button :icon="Refresh" :loading="loadingPeople" :disabled="!isSuperAdmin" @click="loadPeople">刷新人员</el-button></el-form-item>
      </el-form>
      <el-alert v-if="!isSuperAdmin" type="warning" :closable="false" show-icon title="仅超级管理员可以配置教育数据范围，请使用超级管理员账号登录。" />
    </el-card>
    <el-empty v-if="!userId" description="请选择要授权的人员" :image-size="80" />
    <template v-else>
      <div class="scope-options"><el-checkbox v-model="includeChildren">覆盖所选节点的全部下级组织或区域</el-checkbox><span class="selection-summary">已选教育组织 {{ organizationCount }} 个，区域 {{ regionCount }} 个</span></div>
      <div class="scope-grid">
        <el-card shadow="never" class="scope-panel">
          <template #header><div class="panel-header"><span>教育组织</span><el-tag size="small" type="primary" effect="plain">{{ organizationCount }} 已选</el-tag></div></template>
          <div class="tree-scroll"><el-tree ref="organizationTreeRef" v-loading="loadingScopes" :data="organizations" node-key="id" show-checkbox check-strictly default-expand-all :props="{ label: 'schoolName', children: 'children' }" @check="refreshSelectionCounts"><template #default="{ data }"><span class="node"><el-tag size="small" :type="data.orgType === 'EDU_BUREAU' ? 'warning' : 'success'">{{ data.orgType === 'EDU_BUREAU' ? '教育局' : '学校' }}</el-tag>{{ data.schoolName }}</span></template></el-tree><el-empty v-if="!loadingScopes && !organizations.length" description="暂无教育组织" :image-size="64" /></div>
        </el-card>
        <el-card shadow="never" class="scope-panel">
          <template #header><div class="panel-header"><span>区域</span><el-tag size="small" type="primary" effect="plain">{{ regionCount }} 已选</el-tag></div></template>
          <div class="tree-scroll"><el-tree ref="regionTreeRef" v-loading="loadingScopes" :data="regions" node-key="id" lazy show-checkbox check-strictly :load="loadRegionNode" :props="{ label: 'regionName', children: 'children', isLeaf: 'isLeaf' }" @check="refreshSelectionCounts" /><el-empty v-if="!loadingScopes && !regions.length" description="暂无区域" :image-size="64" /></div>
        </el-card>
      </div>
      <div class="actions"><el-button type="primary" :loading="saving" :disabled="!isSuperAdmin" @click="save">保存授权</el-button></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type TreeInstance } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listEducation, listEducationScopes, listOrganizationTree, listRegionChildren, listPersonRoles, replaceEducationScopes, type EducationOrganizationNode, type EducationRecord, type EduRegionOption, type EducationScopeItem } from '@/api/education'
import { useUserStore } from '@/stores/user'

const people = ref<EducationRecord[]>([])
const organizations = ref<EducationOrganizationNode[]>([])
type RegionTreeOption = EduRegionOption & { isLeaf: boolean }
const regions = ref<RegionTreeOption[]>([])
const userId = ref<string>()
const includeChildren = ref(true)
const organizationTreeRef = ref<TreeInstance>()
const regionTreeRef = ref<TreeInstance>()
const loadingPeople = ref(false)
const loadingScopes = ref(false)
const saving = ref(false)
const organizationCount = ref(0)
const regionCount = ref(0)
const userStore = useUserStore()
const isSuperAdmin = computed(() => String(userStore.userId) === '1' || userStore.hasRole('admin') || userStore.hasRole('tenantAdmin'))

onMounted(async () => {
  if (!isSuperAdmin.value) return
  await Promise.all([loadPeople(), loadOrganizations(), loadRegions()])
})

async function loadPeople() {
  loadingPeople.value = true
  try {
    const response = await listEducation('people', { pageNum: 1, pageSize: 100, status: 0, personType: 'TEACHER' })
    const candidates = (response.data?.rows || []).filter(item => item.userId && item.personType === 'TEACHER')
    const roleResults = await Promise.all(candidates.map(item => listPersonRoles(item.id!)))
    people.value = candidates.filter((_item, index) => (roleResults[index].data || []).length > 0)
  } finally { loadingPeople.value = false }
}

async function loadOrganizations() {
  try {
    const response = await listOrganizationTree(0)
    organizations.value = response.data || []
  } catch (_error) {
    organizations.value = []
  }
}

async function loadRegions() {
  try {
    const response = await listRegionChildren(undefined, 0)
    regions.value = (response.data || []).map(withRegionLeaf)
  } catch (_error) {
    regions.value = []
  }
}

async function loadRegionNode(node: any, resolve: (data: RegionTreeOption[]) => void) {
  try {
    const response = await listRegionChildren(node.data?.id, 0)
    resolve((response.data || []).map(withRegionLeaf))
  } catch (_error) {
    resolve([])
  }
}

function withRegionLeaf(region: EduRegionOption): RegionTreeOption {
  return { ...region, isLeaf: (region.nodeLevel ?? 0) >= 3 }
}

async function loadScopes() {
  organizationTreeRef.value?.setCheckedKeys([])
  regionTreeRef.value?.setCheckedKeys([])
  refreshSelectionCounts()
  if (!userId.value) return
  loadingScopes.value = true
  try {
    const response = await listEducationScopes(userId.value)
    const scopes = response.data || []
    await nextTick()
    organizationTreeRef.value?.setCheckedKeys(scopes.filter(item => item.scopeType === 'ORG').map(item => item.scopeId))
    regionTreeRef.value?.setCheckedKeys(scopes.filter(item => item.scopeType === 'REGION').map(item => item.scopeId))
    includeChildren.value = !scopes.some(item => item.includeChildren === 0)
    refreshSelectionCounts()
  } finally { loadingScopes.value = false }
}

function refreshSelectionCounts() {
  organizationCount.value = organizationTreeRef.value?.getCheckedKeys(false).length || 0
  regionCount.value = regionTreeRef.value?.getCheckedKeys(false).length || 0
}

async function save() {
  if (!isSuperAdmin.value || !userId.value) return
  const organizationIds = (organizationTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>
  const regionIds = (regionTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>
  if (!organizationIds.length && !regionIds.length) {
    await ElMessageBox.confirm('未选择任何组织，将撤销该人员全部教育数据范围，确认继续吗？', '撤销授权', { type: 'warning' })
  }
  saving.value = true
  try {
    const items: EducationScopeItem[] = [
      ...organizationIds.map(scopeId => ({ scopeType: 'ORG' as const, scopeId, includeChildren: includeChildren.value ? 1 : 0 })),
      ...regionIds.map(scopeId => ({ scopeType: 'REGION' as const, scopeId, includeChildren: includeChildren.value ? 1 : 0 }))
    ]
    await replaceEducationScopes(userId.value, items)
    ElMessage.success(items.length ? '授权已保存' : '已撤销全部授权')
    await loadScopes()
  } finally { saving.value = false }
}
</script>

<style scoped>
.operation-page { min-height: 100%; padding: 20px; background: #f7f8fa; }.page-header { margin-bottom: 16px; }.page-header h2 { margin: 0; color: #111827; font-size: 20px; line-height: 28px; }.page-header p { margin: 4px 0 0; color: #667085; font-size: 13px; }.selector-card,.scope-panel { border-color: #e4e7ec; border-radius: 8px; box-shadow: 0 2px 8px rgb(16 24 40 / 4%); }.selector-card :deep(.el-card__body) { padding: 16px 20px 4px; }.hint { margin-bottom: 16px; }.user-select { width: 360px; }.scope-options { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 2px 12px; }.selection-summary { color: #667085; font-size: 13px; }.scope-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #1d2939; font-size: 15px; font-weight: 600; }.scope-panel :deep(.el-card__header) { padding: 16px 20px; border-bottom-color: #edf0f5; }.scope-panel :deep(.el-card__body) { padding: 0; }.tree-scroll { min-height: 320px; max-height: min(56vh, 560px); overflow: auto; padding: 12px 20px 20px; }.node { display: inline-flex; align-items: center; gap: 8px; color: #344054; }.actions { display: flex; justify-content: flex-end; padding: 16px 0 4px; }@media (max-width: 900px) { .operation-page { padding: 12px; }.scope-grid { grid-template-columns: 1fr; }.user-select { width: min(100%, 360px); }.scope-options { align-items: flex-start; flex-direction: column; }.tree-scroll { max-height: 420px; } }
</style>
