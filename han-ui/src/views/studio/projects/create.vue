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

        <el-form-item label="风格基调">
          <el-input v-model="form.defaultStyle" placeholder="如电影感、写实、强反差光影" />
        </el-form-item>

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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { addAivideoProject, ratioOptions, type AivideoProjectForm } from '@/api/aivideo'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<AivideoProjectForm>({
  projectName: '',
  topicType: '',
  targetPlatform: '',
  defaultRatio: '9:16',
  defaultStyle: '',
  defaultShotDuration: 5,
  candidateImageCount: 2,
  previewMode: '1',
  sourceType: 'TEXT',
  rawText: ''
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

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
