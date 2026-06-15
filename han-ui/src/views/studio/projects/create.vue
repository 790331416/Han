<template>
  <div class="create-page">
    <el-page-header title="返回" content="新建短剧项目" @back="router.push('/studio/projects')" />

    <section class="create-panel">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="form.projectName" placeholder="请输入项目名称" maxlength="80" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="题材" prop="topicType">
              <el-input v-model="form.topicType" placeholder="如都市、玄幻" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="平台" prop="targetPlatform">
              <el-input v-model="form.targetPlatform" placeholder="如抖音、快手" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="默认画幅">
              <el-select v-model="form.defaultRatio">
                <el-option v-for="item in ratioOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="镜头秒数">
              <el-input-number v-model="form.defaultShotDuration" :min="5" :max="8" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="候选图数">
              <el-input-number v-model="form.candidateImageCount" :min="1" :max="4" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预览模式">
              <el-switch v-model="form.previewMode" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="视觉风格">
          <el-select
            v-model="form.defaultStyle"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入风格"
          >
            <el-option v-for="item in visualStyleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="角色造型">
          <el-select v-model="form.characterDesignType" placeholder="选择角色造型类型">
            <el-option v-for="item in characterDesignTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生成策略">
              <el-select v-model="form.generationStrategy">
                <el-option v-for="item in generationStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="声音模式">
              <el-select v-model="form.audioMode">
                <el-option v-for="item in audioModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="字幕模式">
              <el-select v-model="form.subtitleMode">
                <el-option v-for="item in subtitleModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="参考素材">
              <el-select v-model="form.referenceStrategy">
                <el-option v-for="item in referenceStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="动作强度">
              <el-select v-model="form.actionIntensity">
                <el-option v-for="item in actionIntensityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="连续性">
              <el-select v-model="form.continuityLevel">
                <el-option v-for="item in continuityLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="多角色">
              <el-select v-model="form.multiRoleStrategy">
                <el-option v-for="item in multiRoleStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="项目简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="可选，用于后续生成上下文" />
        </el-form-item>

        <el-form-item label="原文内容">
          <el-input
            v-model="form.rawText"
            type="textarea"
            :rows="12"
            placeholder="粘贴小说、文档或剧情梗概"
          />
        </el-form-item>

        <div class="form-actions">
          <el-button @click="router.push('/studio/projects')">取消</el-button>
          <el-button type="primary" :icon="Check" :loading="submitting" @click="handleSubmit">保存并进入工作台</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  addAivideoProject,
  type AivideoProjectForm
} from '@/api/aivideo'
import { createAivideoDictOptionState } from '@/utils/aivideo-dict-options'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

/**
 * 新建项目页直接复用系统字典，保证项目默认策略和后台基础配置同源。
 */
const {
  actionIntensityOptions,
  audioModeOptions,
  characterDesignTypeOptions,
  continuityLevelOptions,
  generationStrategyOptions,
  loadStrategyOptions,
  multiRoleStrategyOptions,
  ratioOptions,
  referenceStrategyOptions,
  subtitleModeOptions,
  visualStyleOptions
} = createAivideoDictOptionState()

const form = reactive<AivideoProjectForm>({
  projectName: '',
  topicType: '',
  targetPlatform: '',
  defaultRatio: '9:16',
  defaultStyle: '写实电影感',
  characterDesignType: 'AUTO',
  generationStrategy: 'AUTO',
  audioMode: 'SILENT',
  subtitleMode: 'NONE',
  referenceStrategy: 'CHARACTER_SCENE',
  actionIntensity: 'NORMAL',
  continuityLevel: 'STRICT',
  multiRoleStrategy: 'SINGLE_FIRST',
  defaultShotDuration: 5,
  candidateImageCount: 2,
  previewMode: '1',
  sourceType: 'TEXT',
  rawText: ''
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

onMounted(async () => {
  await loadStrategyOptions()
})

async function handleSubmit() {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitting.value = true
  try {
    const res = await addAivideoProject(form)
    ElMessage.success('项目已创建')
    router.push(`/studio/projects/${res.data}/workbench`)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.create-page {
  padding: 20px;
}

.create-panel {
  margin-top: 16px;
  padding: 20px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
