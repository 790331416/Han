<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="queryParams.dictName" placeholder="请输入字典名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="queryParams.dictType" placeholder="请输入字典类型" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="请选择状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="Number(item.value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>字典类型列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增字典</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        class="mb-16"
        title="字典类型只维护“类型头”。真正的字典值列表请点“数据项”进入维护页。"
      />

      <el-table v-loading="loading" :data="dictList">
        <el-table-column label="字典名称" prop="dictName" min-width="200" show-overflow-tooltip />
        <el-table-column label="字典类型" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="handleViewData(row)">{{ row.dictType }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column
          label="创建时间"
          prop="createTime"
          min-width="180"
          :formatter="(_row: any, _col: any, value: any) => $formatDate(value)"
        />
        <el-table-column label="操作" min-width="240">
          <template #default="{ row }">
            <el-button type="success" link @click="handleViewData(row)">数据项</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="45%" class="dialog-sm" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="form.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="form.dictType" placeholder="请输入字典类型" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in statusOptions" :key="item.value" :value="Number(item.value)">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <div v-if="form.id" class="dict-data-preview">
        <div class="preview-header">
          <span>当前字典值预览</span>
          <el-button type="primary" link @click="handleViewData(form as DictType)">查看全部 / 维护数据项</el-button>
        </div>
        <el-table
          v-if="dictDataPreview.length > 0"
          v-loading="dictDataPreviewLoading"
          :data="dictDataPreview"
          size="small"
          border
        >
          <el-table-column label="标签" prop="dictLabel" min-width="140" show-overflow-tooltip />
          <el-table-column label="键值" prop="dictValue" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else :image-size="64" description="暂无数据项，请点击维护数据项添加真实参数值" />
      </div>

      <el-alert
        type="info"
        :closable="false"
        class="dialog-tip"
        title="如果要维护这个字典的值列表，请保存后回到表格点击“数据项”。"
      />

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="form.id && form.dictType" type="success" plain @click="handleViewData(form as DictType)">
          维护数据项
        </el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addDictType,
  deleteDictType,
  listDictData,
  getDictType,
  listDictType,
  type DictData,
  type DictType,
  type DictTypeQuery,
  type DictTypeForm,
  updateDictType
} from '@/api/system/dict'
import {
  findDictLabel,
  loadDictOptions,
  SYS_NORMAL_DISABLE_DICT,
  type DictOption
} from '@/utils/dict-options'
import { resolvePageResult } from '@/utils/page-result'

const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const dictList = ref<DictType[]>([])
const dictDataPreview = ref<DictData[]>([])
const dictDataPreviewLoading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const statusOptions = ref<DictOption[]>([
  { label: '正常', value: '0' },
  { label: '停用', value: '1' }
])

/**
 * 字典类型页只维护“字典头信息”。
 *
 * <p>真实的可选值列表在“数据项”页维护，这里负责类型级筛选、增删改和跳转入口。
 */
const queryParams = reactive<DictTypeQuery>({
  dictName: '',
  dictType: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

const form = reactive<DictTypeForm>({
  id: undefined,
  dictName: '',
  dictType: '',
  status: 0,
  remark: ''
})

const rules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }]
}

function getStatusLabel(status?: number | string) {
  return findDictLabel(statusOptions.value, status, '未知')
}

function getStatusTagType(status?: number | string) {
  return String(status) === '0' ? 'success' : 'danger'
}

onMounted(async () => {
  statusOptions.value = await loadDictOptions(SYS_NORMAL_DISABLE_DICT, statusOptions.value)
  await getList()
})

async function getList() {
  loading.value = true
  try {
    const res = await listDictType(queryParams)
    const pageResult = resolvePageResult<DictType>((res as any).data)
    dictList.value = pageResult.rows
    total.value = pageResult.total
  } catch (error) {
    console.error('加载字典类型失败:', error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.dictName = ''
  queryParams.dictType = ''
  queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增字典类型'
  dialogVisible.value = true
}

async function handleEdit(row: DictType) {
  resetForm()
  dialogTitle.value = '编辑字典类型'
  try {
    const res = await getDictType(row.id)
    const data = (res as any).data
    Object.assign(form, {
      id: data.id,
      dictName: data.dictName,
      dictType: data.dictType,
      status: data.status,
      remark: data.remark
    })
    await loadDictDataPreview(data.dictType)
  } catch (error) {
    console.error('加载字典类型详情失败:', error)
  }
  dialogVisible.value = true
}

async function loadDictDataPreview(dictType: string) {
  dictDataPreview.value = []
  if (!dictType) {
    return
  }

  dictDataPreviewLoading.value = true
  try {
    const res = await listDictData({ dictType, pageNum: 1, pageSize: 5 })
    const pageResult = resolvePageResult<DictData>((res as any).data)
    dictDataPreview.value = pageResult.rows
  } catch (error) {
    console.error('加载字典值预览失败:', error)
  } finally {
    dictDataPreviewLoading.value = false
  }
}

async function handleDelete(row: DictType) {
  try {
    await ElMessageBox.confirm(`确认删除字典类型“${row.dictName}”吗？`, '提示', { type: 'warning' })
    await deleteDictType(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch {
    // 用户取消时不提示
  }
}

function handleViewData(row: Pick<DictType, 'dictType' | 'dictName'>) {
  dialogVisible.value = false
  router.push({ path: '/system/dict-data', query: { dictType: row.dictType, dictName: row.dictName } })
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.id) {
      await updateDictType(form)
      ElMessage.success('修改成功')
    } else {
      await addDictType(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.id = undefined
  form.dictName = ''
  form.dictType = ''
  form.status = 0
  form.remark = ''
  dictDataPreview.value = []
  dictDataPreviewLoading.value = false
}
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 16px;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.dialog-tip {
  margin-top: 8px;
}

.dict-data-preview {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
