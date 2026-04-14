<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="queryParams.modelName" placeholder="请输入模型名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="queryParams.modelType" placeholder="请选择" clearable>
            <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-select v-model="queryParams.provider" placeholder="请选择" clearable>
            <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>AI模型列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增模型</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="modelList">
        <el-table-column label="模型名称" prop="modelName" min-width="150" show-overflow-tooltip />
        <el-table-column label="模型类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelType === 'LLM' ? 'primary' : 'info'">
              {{ getModelTypeLabel(row.modelType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="warning">{{ getProviderLabel(row.provider) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型标识" prop="modelCode" min-width="160" show-overflow-tooltip />
        <el-table-column label="Base URL" prop="baseUrl" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button type="success" link @click="handleTest(row)">测试</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.modelId ? '编辑模型' : '新增模型'" width="65%" class="dialog-lg" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="请输入模型名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择供应商" @change="handleProviderChange">
                <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="form.modelType" placeholder="请选择">
                <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型标识" prop="modelCode">
              <el-input v-model="form.modelCode" placeholder="如: deepseek-chat" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如: https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入API Key" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大Token数" prop="maxTokens">
              <el-input-number v-model="form.maxTokens" :min="256" :max="128000" :step="256" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度" prop="temperature">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input :show-input-controls="false" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  listAiModel, getAiModel, addAiModel, updateAiModel, deleteAiModel, testAiModel,
  modelTypeOptions, providerOptions,
  type AiModel, type AiModelQuery
} from '@/api/ai'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const modelList = ref<AiModel[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<AiModelQuery>({ pageNum: 1, pageSize: 10 })

const defaultForm = (): Partial<AiModel> => ({
  modelId: undefined as any,
  modelName: '',
  modelType: 'LLM',
  provider: 'deepseek',
  modelCode: '',
  baseUrl: '',
  apiKey: '',
  maxTokens: 4096,
  temperature: 0.7,
  status: '0',
  remark: ''
})

const form = reactive<any>(defaultForm())

const rules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  modelCode: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入API Base URL', trigger: 'blur' }]
}

const getModelTypeLabel = (v: string) => modelTypeOptions.find(i => i.value === v)?.label || v
const getProviderLabel = (v: string) => providerOptions.find(i => i.value === v)?.label || v

const providerDefaults: Record<string, string> = {
  openai: 'https://api.openai.com/v1',
  deepseek: 'https://api.deepseek.com/v1',
  qwen: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  zhipu: 'https://open.bigmodel.cn/api/paas/v4',
  ollama: 'http://localhost:11434/v1',
  azure: 'https://your-resource.openai.azure.com/openai/deployments/your-deployment',
  anthropic: 'https://api.anthropic.com/v1',
  siliconflow: 'https://api.siliconflow.cn/v1'
}

const handleProviderChange = (provider: string) => {
  if (providerDefaults[provider] && !form.baseUrl) {
    form.baseUrl = providerDefaults[provider]
  }
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listAiModel(queryParams)
    modelList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }

const handleAdd = () => { Object.assign(form, defaultForm()); dialogVisible.value = true }

const handleEdit = async (row: AiModel) => {
  const res = await getAiModel(row.modelId)
  Object.assign(form, res.data)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.modelId) {
      await updateAiModel(form)
      ElMessage.success('修改成功')
    } else {
      await addAiModel(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch { /* 接口不可用 */ } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: AiModel) => {
  try {
    await ElMessageBox.confirm(`确定删除模型"${row.modelName}"吗?`, '提示', { type: 'warning' })
    await deleteAiModel(row.modelId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 接口不可用 */ }
}

const handleTest = async (row: AiModel) => {
  try {
    ElMessage.info('正在测试连接...')
    const res = await testAiModel(row.modelId)
    ElMessage.success(res.data || '测试完成')
  } catch { /* 接口不可用 */ }
}

onMounted(() => getList())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
