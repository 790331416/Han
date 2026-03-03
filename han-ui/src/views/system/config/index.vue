<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="queryParams.configName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="queryParams.configKey" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="系统内置" prop="configType">
          <el-select v-model="queryParams.configType">
            <el-option label="全部" value="" />
            <el-option label="是" value="Y" />
            <el-option label="否" value="N" />
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
          <span>参数配置列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增参数</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="configList">
        <el-table-column label="参数ID" prop="id" width="80" align="center" />
        <el-table-column label="参数名称" prop="configName" width="200" show-overflow-tooltip />
        <el-table-column label="参数键名" prop="configKey" width="220" show-overflow-tooltip />
        <el-table-column label="参数键值" prop="configValue" min-width="200" show-overflow-tooltip />
        <el-table-column label="系统内置" prop="configType" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'Y' ? 'danger' : 'info'">{{ row.configType === 'Y' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" width="150" show-overflow-tooltip />
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="form.configKey" placeholder="请输入参数键名" />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数键值" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-radio-group v-model="form.configType">
            <el-radio value="Y">是</el-radio>
            <el-radio value="N">否</el-radio>
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
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listConfig, getConfig, addConfig, updateConfig, deleteConfig, type Config, type ConfigForm } from '@/api/system/config'

const loading = ref(false)
const submitLoading = ref(false)
const configList = ref<Config[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ configName: '', configKey: '', configType: '' as string | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<ConfigForm>({ configName: '', configKey: '', configValue: '', configType: 'N', remark: '' })

const rules: FormRules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入参数键名', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数键值', trigger: 'blur' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listConfig(queryParams)
    const data = (res as any).data
    configList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.configName = ''; queryParams.configKey = ''; queryParams.configType = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增参数'
  dialogVisible.value = true
}

async function handleEdit(row: Config) {
  resetForm()
  dialogTitle.value = '编辑参数'
  try {
    const res = await getConfig(row.id)
    const d = (res as any).data
    Object.assign(form, { id: d.id, configName: d.configName, configKey: d.configKey, configValue: d.configValue, configType: d.configType, remark: d.remark })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Config) {
  try {
    await ElMessageBox.confirm(`确认删除参数"${row.configName}"？`, '提示', { type: 'warning' })
    await deleteConfig(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.id) {
      await updateConfig(form)
      ElMessage.success('修改成功')
    } else {
      await addConfig(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.id = undefined; form.configName = ''; form.configKey = ''; form.configValue = ''; form.configType = 'N'; form.remark = ''
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
