<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="字典类型">
          <el-tag>{{ dictTypeName || '未命名字典' }}（{{ dictTypeCode || '未指定' }}）</el-tag>
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入字典标签" clearable @keyup.enter="handleQuery" />
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
          <span>字典数据列表</span>
          <div class="header-actions">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增数据</el-button>
            <el-button type="info" :icon="Back" @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="字典标签" prop="dictLabel" min-width="200" show-overflow-tooltip />
        <el-table-column label="字典键值" prop="dictValue" min-width="160" show-overflow-tooltip />
        <el-table-column label="排序" prop="dictSort" width="80" align="center" />
        <el-table-column label="列表样式" prop="listClass" width="120" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column
          label="创建时间"
          prop="createTime"
          min-width="180"
          :formatter="(_row: any, _col: any, value: any) => $formatDate(value)"
        />
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
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
        <el-form-item label="字典类型">
          <el-input :model-value="dictTypeCode" disabled />
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="form.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="form.dictValue" placeholder="请输入字典键值" />
        </el-form-item>
        <el-form-item label="排序" prop="dictSort">
          <el-input-number v-model="form.dictSort" :min="0" />
        </el-form-item>
        <el-form-item label="列表样式">
          <el-select v-model="form.listClass" clearable placeholder="请选择列表样式">
            <el-option label="默认(default)" value="" />
            <el-option label="主要(primary)" value="primary" />
            <el-option label="成功(success)" value="success" />
            <el-option label="警告(warning)" value="warning" />
            <el-option label="危险(danger)" value="danger" />
            <el-option label="信息(info)" value="info" />
          </el-select>
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
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addDictData,
  deleteDictData,
  getDictData,
  listDictData,
  type DictData,
  type DictDataQuery,
  type DictDataForm,
  updateDictData
} from '@/api/system/dict'
import {
  findDictLabel,
  loadDictOptions,
  SYS_NORMAL_DISABLE_DICT,
  type DictOption
} from '@/utils/dict-options'
import { resolvePageResult } from '@/utils/page-result'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const dataList = ref<DictData[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const statusOptions = ref<DictOption[]>([
  { label: '正常', value: '0' },
  { label: '停用', value: '1' }
])

const dictTypeCode = ref((route.query.dictType as string) || '')
const dictTypeName = ref((route.query.dictName as string) || '')

/**
 * 字典数据页维护某一个字典类型下的全部可选值。
 *
 * <p>标签、键值、排序、状态和展示样式都在这里统一收口，供系统其他模块直接复用。
 */
const queryParams = reactive<DictDataQuery>({
  dictType: dictTypeCode.value,
  dictLabel: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

const form = reactive<DictDataForm>({
  id: undefined,
  dictLabel: '',
  dictValue: '',
  dictType: dictTypeCode.value,
  dictSort: 0,
  listClass: '',
  status: 0,
  remark: ''
})

const rules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
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
    const res = await listDictData(queryParams)
    const pageResult = resolvePageResult<DictData>((res as any).data)
    dataList.value = pageResult.rows
    total.value = pageResult.total
  } catch (error) {
    console.error('加载字典值失败:', error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.dictLabel = ''
  queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增字典数据'
  dialogVisible.value = true
}

async function handleEdit(row: DictData) {
  resetForm()
  dialogTitle.value = '编辑字典数据'
  try {
    const res = await getDictData(row.id)
    const data = (res as any).data
    Object.assign(form, {
      id: data.id,
      dictLabel: data.dictLabel,
      dictValue: data.dictValue,
      dictType: data.dictType,
      dictSort: data.dictSort,
      listClass: data.listClass || '',
      cssClass: data.cssClass,
      isDefault: data.isDefault,
      status: data.status,
      remark: data.remark
    })
  } catch (error) {
    console.error('加载字典值详情失败:', error)
  }
  dialogVisible.value = true
}

async function handleDelete(row: DictData) {
  try {
    await ElMessageBox.confirm(`确认删除字典数据“${row.dictLabel}”吗？`, '提示', { type: 'warning' })
    await deleteDictData(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch {
    // 用户取消时不提示
  }
}

function handleBack() {
  router.push('/system/dict')
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.id) {
      await updateDictData(form)
      ElMessage.success('修改成功')
    } else {
      await addDictData(form)
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
  form.dictLabel = ''
  form.dictValue = ''
  form.dictType = dictTypeCode.value
  form.dictSort = 0
  form.listClass = ''
  form.status = 0
  form.remark = ''
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

.header-actions {
  display: flex;
  gap: 12px;
}

.search-form {
  margin-bottom: 16px;
}

.mt-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
