<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="字典类型">
          <el-tag>{{ dictTypeName }} ({{ dictTypeCode }})</el-tag>
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
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
          <div>
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增数据</el-button>
            <el-button type="info" :icon="Back" @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="字典编码" prop="dictCode" width="90" align="center" />
        <el-table-column label="字典标签" prop="dictLabel" width="200" />
        <el-table-column label="字典键值" prop="dictValue" width="150" />
        <el-table-column label="排序" prop="dictSort" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" width="200" fixed="right">
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
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
        <el-form-item label="回显样式">
          <el-select v-model="form.listClass" placeholder="请选择" clearable>
            <el-option label="默认(default)" value="" />
            <el-option label="成功(success)" value="success" />
            <el-option label="警告(warning)" value="warning" />
            <el-option label="危险(danger)" value="danger" />
            <el-option label="信息(info)" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Refresh, Plus, Edit, Delete, Back } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listDictData, getDictData, addDictData, updateDictData, deleteDictData, type DictData, type DictDataForm } from '@/api/system/dict'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const dataList = ref<DictData[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const dictTypeCode = ref((route.query.dictType as string) || '')
const dictTypeName = ref((route.query.dictName as string) || '')

const queryParams = reactive({ dictType: dictTypeCode.value, dictLabel: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<DictDataForm>({ dictLabel: '', dictValue: '', dictType: dictTypeCode.value, dictSort: 0, listClass: '', status: 0, remark: '' })

const rules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listDictData(queryParams)
    const data = (res as any).data
    dataList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.dictLabel = ''; queryParams.status = undefined
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
    const res = await getDictData(row.dictCode)
    const d = (res as any).data
    Object.assign(form, { dictCode: d.dictCode, dictLabel: d.dictLabel, dictValue: d.dictValue, dictType: d.dictType, dictSort: d.dictSort, listClass: d.listClass || '', cssClass: d.cssClass, isDefault: d.isDefault, status: d.status, remark: d.remark })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: DictData) {
  try {
    await ElMessageBox.confirm(`确认删除字典数据"${row.dictLabel}"？`, '提示', { type: 'warning' })
    await deleteDictData(row.dictCode)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

function handleBack() {
  router.push('/system/dict')
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.dictCode) {
      await updateDictData(form)
      ElMessage.success('修改成功')
    } else {
      await addDictData(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.dictCode = undefined; form.dictLabel = ''; form.dictValue = ''; form.dictType = dictTypeCode.value; form.dictSort = 0; form.listClass = ''; form.status = 0; form.remark = ''
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
