<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>短剧基础配置</span>
          <el-button type="primary" :icon="Check" :loading="submitting" @click="handleSubmit">保存</el-button>
        </div>
      </template>

      <el-form ref="formRef" v-loading="loading" :model="form" label-width="140px" class="setting-form">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="默认文本模型">
              <el-input v-model="form.textModelId" placeholder="模型ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认图片模型">
              <el-input v-model="form.imageModelId" placeholder="模型ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认视频模型">
              <el-input v-model="form.videoModelId" placeholder="模型ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="素材访问策略">
              <el-select v-model="form.mediaAccessPolicy">
                <el-option label="登录可见" value="PRIVATE" />
                <el-option label="公开可见" value="PUBLIC" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="默认画幅">
              <el-select v-model="form.defaultRatio">
                <el-option v-for="item in ratioOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认清晰度">
              <el-select v-model="form.defaultResolution">
                <el-option v-for="item in resolutionOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="镜头秒数">
              <el-input-number v-model="form.defaultShotDuration" :min="1" :max="30" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="图片候选数">
              <el-input-number v-model="form.imageCandidateCount" :min="1" :max="4" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="视频候选数">
              <el-input-number v-model="form.videoCandidateCount" :min="1" :max="3" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="预览模式">
              <el-switch v-model="form.previewMode" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="内容审核">
              <el-switch v-model="form.contentAuditEnabled" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="场景图 Prompt">
              <el-input v-model="form.sceneImagePromptTemplateId" placeholder="Prompt模板ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="配置说明" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getAivideoSetting, resolutionOptions, ratioOptions, updateAivideoSetting, type AivideoSetting } from '@/api/aivideo'

const loading = ref(false)
const submitting = ref(false)

const form = reactive<AivideoSetting>({
  defaultRatio: '9:16',
  defaultResolution: '720p',
  imageCandidateCount: 2,
  videoCandidateCount: 1,
  defaultShotDuration: 5,
  previewMode: '1',
  contentAuditEnabled: '1',
  mediaAccessPolicy: 'PRIVATE',
  remark: ''
})

async function loadSetting() {
  loading.value = true
  try {
    const res = await getAivideoSetting()
    Object.assign(form, res.data || {})
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    await updateAivideoSetting(form)
    ElMessage.success('保存成功')
    await loadSetting()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadSetting()
})
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

.setting-form {
  max-width: 1180px;
}
</style>
