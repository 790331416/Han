<template>
  <div class="app-container">
    <el-card shadow="never" class="setup-card">
        <template #header><span>学年升级</span></template>
      <el-alert type="warning" :closable="false" show-icon class="hint">
        系统不会按班级名称自动推断升级关系。请逐班明确选择“升入目标班”或“毕业”；提交预览不改变人员归属，确认后才执行。
      </el-alert>
      <el-form inline>
        <el-form-item label="学校">
          <EducationSchoolSelector v-model="schoolId" :nodes="organizations" clearable style="width: 260px" @change="resetSelection" />
        </el-form-item>
        <el-form-item label="来源学年">
          <el-select v-model="sourceAcademicYearId" filterable clearable placeholder="请选择来源学年" style="width: 180px" @change="loadMappings">
            <el-option v-for="item in academicYears" :key="item.id" :value="String(item.id)" :label="item.yearName" :disabled="item.status === 'DRAFT'" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标学年">
          <el-select v-model="targetAcademicYearId" filterable clearable placeholder="请选择目标学年" style="width: 180px" @change="loadMappings">
            <el-option v-for="item in academicYears" :key="item.id" :value="String(item.id)" :label="item.yearName" :disabled="item.status === 'CLOSED'" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button :icon="Refresh" :loading="loading" @click="loadMappings">刷新</el-button></el-form-item>
      </el-form>

      <el-empty v-if="!ready" description="请先选择学校、来源学年和目标学年" />
      <template v-else>
        <el-alert type="info" :closable="false" show-icon class="hint">
          来源学年共 {{ mappings.length }} 个有效行政班。预览时会校验每个班都已配置，且仅处理学生；历史归班缺少学年的数据需先完成校准。
        </el-alert>
        <el-table v-loading="loading" :data="mappings" border>
          <el-table-column label="来源行政班" min-width="220">
            <template #default="{ row }">{{ classLabel(row.sourceClassId, sourceClasses) }}</template>
          </el-table-column>
          <el-table-column label="处理方式" width="150">
            <template #default="{ row }">
              <el-select v-model="row.action" @change="row.action === 'GRADUATE' && (row.targetClassId = undefined)">
                <el-option label="升入目标班" value="PROMOTE" />
                <el-option label="毕业" value="GRADUATE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标行政班" min-width="260">
            <template #default="{ row }">
              <EducationOptionSelector v-model="row.targetClassId" :options="targetClassOptions" :disabled="row.action === 'GRADUATE'" placeholder="请选择目标班级" />
            </template>
          </el-table-column>
        </el-table>
        <el-form class="remark-form"><el-form-item label="本次说明"><el-input v-model="remark" maxlength="500" show-word-limit /></el-form-item></el-form>
        <el-button v-if="userStore.hasPermission('education:promotion:preview')" type="primary" :loading="previewing" @click="preview">创建预览</el-button>
        <el-button v-if="activeBatch && canConfirm && userStore.hasPermission('education:promotion:confirm')" type="danger" :loading="confirming" @click="confirm">确认执行本次升级</el-button>
        <el-tag v-if="activeBatch" class="batch-status" :type="batchTag(activeBatch.status)">当前批次：{{ batchLabel(activeBatch.status) }}（{{ activeBatch.totalCount }} 人）</el-tag>
      </template>
    </el-card>

    <el-card shadow="never">
      <template #header><span>本校升级批次</span></template>
      <el-empty v-if="!schoolId" description="选择学校后显示批次记录" />
      <el-table v-else v-loading="loadingBatches" :data="batches">
        <el-table-column label="创建时间" prop="createTime" min-width="170" />
        <el-table-column label="来源学年" min-width="140"><template #default="{ row }">{{ yearLabel(row.sourceAcademicYearId) }}</template></el-table-column>
        <el-table-column label="目标学年" min-width="140"><template #default="{ row }">{{ yearLabel(row.targetAcademicYearId) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="batchTag(row.status)">{{ batchLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="处理结果" min-width="150"><template #default="{ row }">共 {{ row.totalCount }} 人，成功 {{ row.successCount || 0 }}，失败 {{ row.failedCount || 0 }}</template></el-table-column>
        <el-table-column label="确认时间" prop="confirmedAt" min-width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  confirmPromotion, listAcademicYears, listClassTree, listOrganizationTree, listPromotionBatches, previewPromotion,
  type AcademicYear, type EducationClassTreeNode, type EducationOrganizationNode, type PromotionBatch, type PromotionMapping
} from '@/api/education'
import { useUserStore } from '@/stores/user'
import EducationOptionSelector, { type EducationSelectOption } from '@/components/education/EducationOptionSelector.vue'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'

const userStore = useUserStore()
const organizations = ref<EducationOrganizationNode[]>([])
const academicYears = ref<AcademicYear[]>([])
const sourceClasses = ref<EducationClassTreeNode[]>([])
const targetClasses = ref<EducationClassTreeNode[]>([])
const mappings = ref<PromotionMapping[]>([])
const batches = ref<PromotionBatch[]>([])
const activeBatch = ref<PromotionBatch>()
const schoolId = ref<string | number>()
const sourceAcademicYearId = ref<string | number>()
const targetAcademicYearId = ref<string | number>()
const remark = ref('')
const loading = ref(false)
const loadingBatches = ref(false)
const previewing = ref(false)
const confirming = ref(false)
const ready = computed(() => !!schoolId.value && !!sourceAcademicYearId.value && !!targetAcademicYearId.value && sourceAcademicYearId.value !== targetAcademicYearId.value)
const canConfirm = computed(() => activeBatch.value?.status === 'DRAFT' || activeBatch.value?.status === 'PARTIAL')
const targetClassOptions = computed<EducationSelectOption[]>(() => targetClasses.value.map(item => ({ value: item.id, label: classLabel(item.id, targetClasses.value) })))

onMounted(async () => {
  const [organizationResponse, yearResponse] = await Promise.all([
    listOrganizationTree(0),
    listAcademicYears({ pageNum: 1, pageSize: 100, status: '' })
  ])
  organizations.value = organizationResponse.data || []
  academicYears.value = yearResponse.data?.rows || []
})

function resetSelection() {
  sourceAcademicYearId.value = undefined
  targetAcademicYearId.value = undefined
  sourceClasses.value = []
  targetClasses.value = []
  mappings.value = []
  activeBatch.value = undefined
  if (schoolId.value) loadBatches()
}

async function loadMappings() {
  activeBatch.value = undefined
  if (!ready.value) {
    sourceClasses.value = []
    targetClasses.value = []
    mappings.value = []
    return
  }
  loading.value = true
  try {
    const [sourceResponse, targetResponse] = await Promise.all([
      listClassTree({ schoolId: schoolId.value!, academicYearId: sourceAcademicYearId.value!, status: 0 }),
      listClassTree({ schoolId: schoolId.value!, academicYearId: targetAcademicYearId.value!, status: 0 })
    ])
    sourceClasses.value = flattenClasses(sourceResponse.data || [])
    targetClasses.value = flattenClasses(targetResponse.data || [])
    mappings.value = sourceClasses.value.map(item => ({ sourceClassId: item.id, action: 'PROMOTE' }))
    await loadBatches()
  } finally { loading.value = false }
}

async function loadBatches() {
  if (!schoolId.value) return
  loadingBatches.value = true
  try {
    const response = await listPromotionBatches(schoolId.value)
    batches.value = response.data || []
  } finally { loadingBatches.value = false }
}

async function preview() {
  if (!ready.value || !mappings.value.length) return ElMessage.error('请先加载来源学年的行政班')
  const missingTarget = mappings.value.some(item => item.action === 'PROMOTE' && !item.targetClassId)
  if (missingTarget) return ElMessage.error('每个“升入目标班”的来源班都必须选择目标班级')
  previewing.value = true
  try {
    const response = await previewPromotion({
      schoolId: schoolId.value!, sourceAcademicYearId: sourceAcademicYearId.value!, targetAcademicYearId: targetAcademicYearId.value!,
      mappings: mappings.value, remark: remark.value || undefined
    })
    activeBatch.value = response.data
    await loadBatches()
    ElMessage.success(activeBatch.value?.status === 'CONFIRMED' ? '该映射已执行，无需重复确认' : '预览已创建，请核对人数后再确认执行')
  } finally { previewing.value = false }
}

async function confirm() {
  if (!activeBatch.value || !canConfirm.value) return
  await ElMessageBox.confirm(`确认执行本批 ${activeBatch.value.totalCount} 名学生的学年升级吗？确认后会保留来源学年归班历史，并写入目标学年归班关系。`, '二次确认', { type: 'warning', confirmButtonText: '确认执行' })
  confirming.value = true
  try {
    const response = await confirmPromotion(activeBatch.value.id)
    activeBatch.value = response.data
    await loadBatches()
    ElMessage.success(activeBatch.value?.status === 'CONFIRMED' ? '升级已完成' : '升级部分完成，请根据失败项修复数据后重试')
  } finally { confirming.value = false }
}

function flattenClasses(nodes: EducationClassTreeNode[]): EducationClassTreeNode[] {
  return nodes.flatMap(node => [node, ...flattenClasses(node.children || [])]).filter(node => node.nodeType === 'CLASS')
}
function classLabel(id: string | number, values: EducationClassTreeNode[]) {
  const item = values.find(value => String(value.id) === String(id))
  return item?.className || '—'
}
function yearLabel(id: string | number) { return academicYears.value.find(item => String(item.id) === String(id))?.yearName || id }
function batchLabel(status: PromotionBatch['status']) { return ({ DRAFT: '待确认', EXECUTING: '执行中', CONFIRMED: '已完成', PARTIAL: '部分完成' } as const)[status] || status }
function batchTag(status: PromotionBatch['status']) { return status === 'CONFIRMED' ? 'success' : status === 'PARTIAL' ? 'danger' : status === 'DRAFT' ? 'warning' : 'info' }
</script>

<style scoped>
.app-container { padding: 20px; }
.setup-card { margin-bottom: 16px; }
.hint { margin-bottom: 16px; }
.remark-form { margin-top: 16px; }
.remark-form :deep(.el-form-item) { width: 100%; }
.remark-form :deep(.el-form-item__content) { flex: 1; }
.batch-status { margin-left: 12px; }
</style>
