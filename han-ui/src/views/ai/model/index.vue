<template>
  <div class="app-container" data-testid="ai-model-page">
    <el-card shadow="never" class="search-form">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="模型名称" prop="modelName">
          <el-input
            v-model="queryParams.modelName"
            placeholder="请输入模型名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="queryParams.modelType" placeholder="请选择" clearable>
            <el-option
              v-for="item in modelTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-select v-model="queryParams.provider" placeholder="请选择" clearable>
            <el-option
              v-for="item in providerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
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
          <span>AI 模型列表</span>
          <el-button
            type="primary"
            :icon="Plus"
            data-testid="ai-model-add-button"
            @click="handleAdd"
          >
            新增模型
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="modelList" data-testid="ai-model-table">
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="模型类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelType === 'LLM' ? 'primary' : 'info'">
              {{ getModelTypeLabel(row.modelType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="130" align="center">
          <template #default="{ row }">
            <el-tag type="warning">{{ getProviderLabel(row.provider) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型标识" prop="modelCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="Base URL" prop="baseUrl" min-width="220" show-overflow-tooltip />
        <el-table-column label="凭证状态" width="180" align="center">
          <template #default="{ row }">
            <div class="credential-cell">
              <el-tag
                :type="row.credentialConfigured ? 'success' : 'info'"
                size="small"
                :data-testid="`ai-model-credential-status-${row.modelId}`"
              >
                {{ row.credentialConfigured ? '已配置' : '未配置' }}
              </el-tag>
              <el-tag
                size="small"
                effect="plain"
                :type="getCredentialSourceType(row.credentialSource)"
                :data-testid="`ai-model-credential-source-${row.modelId}`"
              >
                {{ getCredentialSourceLabel(row.credentialSource) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }">
            <el-button
              type="success"
              link
              :data-testid="`ai-model-test-button-${row.modelId}`"
              @click="handleTest(row)"
            >
              测试
            </el-button>
            <el-button
              type="primary"
              link
              :icon="Edit"
              :data-testid="`ai-model-edit-button-${row.modelId}`"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              link
              :icon="Delete"
              :data-testid="`ai-model-delete-button-${row.modelId}`"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.modelId ? '编辑模型' : '新增模型'"
      width="760px"
      class="dialog-lg"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" data-testid="ai-model-form">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="form-alert"
          :title="credentialAlertTitle"
          :description="credentialAlertDescription"
        />

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input
                v-model="form.modelName"
                data-testid="ai-model-name-input"
                placeholder="请输入模型名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-select
                v-model="form.provider"
                data-testid="ai-model-provider-select"
                placeholder="请选择供应商"
                @change="handleProviderChange"
              >
                <el-option
                  v-for="item in providerOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select
                v-model="form.modelType"
                data-testid="ai-model-type-select"
                placeholder="请选择模型类型"
                @change="handleModelTypeChange"
              >
                <el-option
                  v-for="item in modelTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型标识" prop="modelCode">
              <el-input
                v-model="form.modelCode"
                data-testid="ai-model-code-input"
                :placeholder="currentModelCodePlaceholder"
              />
              <div v-if="form.provider === 'volcengine'" class="field-tip">
                {{ volcengineModelCodeTip }}
              </div>
              <div v-if="currentSuggestions.length" class="suggestion-row">
                <span class="suggestion-label">推荐模型</span>
                <el-tag
                  v-for="suggestion in currentSuggestions"
                  :key="suggestion"
                  class="suggestion-tag"
                  effect="plain"
                  @click="applySuggestedModelCode(suggestion)"
                >
                  {{ suggestion }}
                </el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="API Base URL" prop="baseUrl">
          <el-input
            v-model="form.baseUrl"
            data-testid="ai-model-base-url-input"
            :placeholder="currentBaseUrlPlaceholder"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            data-testid="ai-model-api-key-input"
            :type="apiKeyInputType"
            :rows="apiKeyInputRows"
            :show-password="apiKeyInputType === 'password'"
            :placeholder="apiKeyPlaceholder"
          />
          <div class="field-tip">
            {{ apiKeyFieldTip }}
          </div>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大 Token" prop="maxTokens">
              <el-input-number
                v-model="form.maxTokens"
                data-testid="ai-model-max-tokens-input"
                :min="256"
                :max="128000"
                :step="256"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度" prop="temperature">
              <el-slider
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                show-input
                :show-input-controls="false"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status" data-testid="ai-model-status-group">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            data-testid="ai-model-remark-input"
            type="textarea"
            :rows="2"
            placeholder="备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitLoading"
          data-testid="ai-model-submit-button"
          @click="handleSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  addAiModel,
  deleteAiModel,
  getAiModel,
  listAiModel,
  modelTypeOptions,
  providerOptions,
  testAiModel,
  updateAiModel,
  type AiModel,
  type AiModelQuery
} from '@/api/ai'

interface ProviderPreset {
  baseUrl: string
  defaultModelCode: string
  suggestions: string[]
  credentialEnv: string
  apiKeyPlaceholder?: string
  apiKeyTip?: string
}

const providerPresets: Record<string, ProviderPreset> = {
  openai: {
    baseUrl: 'https://api.openai.com/v1',
    defaultModelCode: 'gpt-4.1',
    suggestions: ['gpt-4.1', 'gpt-4.1-mini', 'text-embedding-3-large'],
    credentialEnv: 'OPENAI_API_KEY'
  },
  volcengine: {
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    defaultModelCode: '',
    suggestions: [],
    credentialEnv: 'VOLCENGINE_ARK_API_KEY'
  },
  deepseek: {
    baseUrl: 'https://api.deepseek.com/v1',
    defaultModelCode: 'deepseek-chat',
    suggestions: ['deepseek-chat', 'deepseek-reasoner'],
    credentialEnv: 'DEEPSEEK_API_KEY'
  },
  qwen: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModelCode: 'qwen-plus',
    suggestions: ['qwen-plus', 'qwen-coder-turbo-0919', 'qvq-max-2025-03-25', 'qwen3-vl-235b-a22b-thinking'],
    credentialEnv: 'DASHSCOPE_API_KEY'
  },
  zhipu: {
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    defaultModelCode: 'glm-4-plus',
    suggestions: ['glm-4-plus', 'glm-4-air', 'embedding-3'],
    credentialEnv: 'ZHIPU_API_KEY'
  },
  ollama: {
    baseUrl: 'http://localhost:11434/v1',
    defaultModelCode: 'qwen2.5:7b',
    suggestions: ['qwen2.5:7b', 'deepseek-r1:8b', 'nomic-embed-text'],
    credentialEnv: 'OLLAMA_API_KEY'
  },
  azure: {
    baseUrl: 'https://your-resource.openai.azure.com/openai/deployments/your-deployment',
    defaultModelCode: 'gpt-4.1',
    suggestions: ['gpt-4.1', 'gpt-4.1-mini'],
    credentialEnv: 'AZURE_OPENAI_API_KEY'
  },
  anthropic: {
    baseUrl: 'https://api.anthropic.com/v1',
    defaultModelCode: 'claude-3-7-sonnet-latest',
    suggestions: ['claude-3-7-sonnet-latest', 'claude-3-5-haiku-latest'],
    credentialEnv: 'ANTHROPIC_API_KEY'
  },
  siliconflow: {
    baseUrl: 'https://api.siliconflow.cn/v1',
    defaultModelCode: 'deepseek-ai/DeepSeek-V3',
    suggestions: ['deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-72B-Instruct'],
    credentialEnv: 'SILICONFLOW_API_KEY'
  }
}

const modelTypePresets: Record<string, ProviderPreset> = {
  TTS: {
    baseUrl: 'https://openspeech.bytedance.com/api/v1/tts',
    defaultModelCode: 'volc-tts',
    suggestions: ['volc-tts'],
    credentialEnv: 'AIVIDEO_TTS_VOLC_APP_ID / AIVIDEO_TTS_VOLC_ACCESS_TOKEN',
    apiKeyPlaceholder:
      '{"appId":"火山语音AppID","accessToken":"火山语音AccessToken","cluster":"volcano_tts","defaultVoiceType":"BV001_24k_streaming"}',
    apiKeyTip:
      '语音合成建议填写 JSON：appId、accessToken、cluster、defaultVoiceType；也兼容原 AIVIDEO_TTS_VOLC_* 环境变量。'
  },
  VIDEO_EDIT: {
    baseUrl: 'https://vod.volcengineapi.com',
    defaultModelCode: 'vod-direct-edit',
    suggestions: ['vod-direct-edit'],
    credentialEnv: 'VOLCENGINE_VOD_ACCESS_KEY_ID / VOLCENGINE_VOD_SECRET_ACCESS_KEY / AIVIDEO_VOD_SPACE',
    apiKeyPlaceholder:
      '{"accessKey":"火山AK","secretKey":"火山SK","space":"space-s54no5","application":"VideoTrackHighlight","region":"cn-north-1"}',
    apiKeyTip:
      '视频剪辑合成建议填写 JSON：accessKey、secretKey、space、application、region；也兼容原 VOLCENGINE_VOD_* / AIVIDEO_VOD_* 环境变量。'
  }
}

const credentialSourceLabelMap: Record<string, string> = {
  env: '环境变量',
  database: '数据库',
  none: '未配置'
}

const loading = ref(false)
const submitLoading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const modelList = ref<AiModel[]>([])
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<AiModelQuery>({
  pageNum: 1,
  pageSize: 10
})

const defaultForm = (): Partial<AiModel> => ({
  modelId: undefined,
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

const form = reactive<Partial<AiModel>>(defaultForm())

const rules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  modelCode: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 API Base URL', trigger: 'blur' }]
}

const currentPreset = computed(() => providerPresets[form.provider || ''] || null)
const currentIntegrationPreset = computed(() => {
  return form.provider === 'volcengine' ? modelTypePresets[form.modelType || ''] || null : null
})
const currentEffectivePreset = computed(() => currentIntegrationPreset.value || currentPreset.value)
const currentSuggestions = computed(() => currentEffectivePreset.value?.suggestions || [])
const currentCredentialEnv = computed(() => currentEffectivePreset.value?.credentialEnv || 'HAN_AI_PROVIDER_<PROVIDER>_API_KEY')
const currentModelCodePlaceholder = computed(() => {
  if (currentIntegrationPreset.value?.defaultModelCode) {
    return `如: ${currentIntegrationPreset.value.defaultModelCode}`
  }
  return form.provider === 'volcengine' ? '请输入火山方舟推理接入点 ID，如 ep-...' : '如: qwen-plus'
})
const currentBaseUrlPlaceholder = computed(() => {
  return currentEffectivePreset.value?.baseUrl ? `如: ${currentEffectivePreset.value.baseUrl}` : '如: https://api.example.com/v1'
})
const apiKeyInputType = computed(() => currentIntegrationPreset.value ? 'textarea' : 'password')
const apiKeyInputRows = computed(() => currentIntegrationPreset.value ? 5 : 1)
const apiKeyPlaceholder = computed(() => {
  return currentIntegrationPreset.value?.apiKeyPlaceholder || '请输入 API Key，留空表示保留原值'
})
const apiKeyFieldTip = computed(() => {
  return currentIntegrationPreset.value?.apiKeyTip
    || `当前供应商建议优先使用环境变量 ${currentCredentialEnv.value}，数据库值仅作为回退；编辑已有模型时留空会保留原值。`
})
const volcengineModelCodeTip = computed(() => {
  if (form.modelType === 'TTS') {
    return '火山语音合成建议填写 volc-tts，具体语音参数放在 API Key JSON 中。'
  }
  if (form.modelType === 'VIDEO_EDIT') {
    return '火山 VOD 剪辑合成建议填写 vod-direct-edit，space/application/region 放在 API Key JSON 中。'
  }
  return '火山方舟请填写控制台中的推理接入点 ID，通常以 ep- 开头。'
})
const credentialAlertTitle = computed(() => {
  return form.modelId ? '编辑模型时将保留原始密钥' : '新增模型建议优先走环境变量'
})
const credentialAlertDescription = computed(() => {
  const pieces = [
    `当前供应商建议环境变量: ${currentCredentialEnv.value}`,
    '运行时会优先读取环境变量，其次才回退到数据库中的已保存值',
    '已存在模型在编辑时如果 API Key 留空，会自动保留原值'
  ]
  return pieces.join('；')
})

function getModelTypeLabel(value: string) {
  return modelTypeOptions.find((item) => item.value === value)?.label || value
}

function getProviderLabel(value: string) {
  return providerOptions.find((item) => item.value === value)?.label || value
}

function getCredentialSourceLabel(value?: string) {
  return credentialSourceLabelMap[value || 'none'] || value || '未配置'
}

function getCredentialSourceType(value?: string) {
  if (value === 'env') {
    return 'success'
  }
  if (value === 'database') {
    return 'warning'
  }
  return 'info'
}

function resetForm() {
  Object.assign(form, defaultForm())
}

function applySuggestedModelCode(modelCode: string) {
  form.modelCode = modelCode
}

function handleProviderChange(provider: string) {
  const preset = provider === 'volcengine' ? currentEffectivePreset.value : providerPresets[provider]
  if (!preset) {
    return
  }
  if (!form.baseUrl) {
    form.baseUrl = preset.baseUrl
  }
  if (!form.modelCode) {
    form.modelCode = preset.defaultModelCode
  }
}

function handleModelTypeChange() {
  const preset = modelTypePresets[form.modelType || '']
  if (!preset) {
    return
  }
  form.provider = 'volcengine'
  form.baseUrl = preset.baseUrl
  form.modelCode = preset.defaultModelCode
}

async function getList() {
  loading.value = true
  try {
    const res = await listAiModel(queryParams)
    modelList.value = res.data.rows
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryFormRef.value?.resetFields()
  handleQuery()
}

function handleAdd() {
  resetForm()
  handleProviderChange(String(form.provider || ''))
  dialogVisible.value = true
}

async function handleEdit(row: AiModel) {
  const res = await getAiModel(row.modelId)
  Object.assign(form, res.data)
  form.apiKey = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (!valid) {
    return
  }
  submitLoading.value = true
  try {
    if (form.modelId) {
      await updateAiModel(form as AiModel)
      ElMessage.success('修改成功')
    } else {
      await addAiModel(form as AiModel)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: AiModel) {
  await ElMessageBox.confirm(`确定删除模型“${row.modelName}”吗？`, '提示', { type: 'warning' })
  await deleteAiModel(row.modelId)
  ElMessage.success('删除成功')
  await getList()
}

async function handleTest(row: AiModel) {
  ElMessage.info('正在测试模型连通性...')
  const res = await testAiModel(row.modelId)
  ElMessage.success(res.data || '测试完成')
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.form-alert {
  margin-bottom: 20px;
}

.field-tip {
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.credential-cell {
  display: flex;
  gap: 6px;
  justify-content: center;
  flex-wrap: wrap;
}

.suggestion-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  align-items: center;
}

.suggestion-label {
  color: #6b7280;
  font-size: 12px;
}

.suggestion-tag {
  cursor: pointer;
}
</style>
