<template>
  <div class="workbench-page">
    <div class="workbench-header">
      <div>
        <el-button :icon="ArrowLeft" text @click="router.push('/studio/projects')">项目列表</el-button>
        <h2>{{ detail.project?.projectName || '短剧项目' }}</h2>
      </div>
      <el-tag type="primary" effect="plain">{{ getStageLabel(detail.project?.currentStage) }}</el-tag>
    </div>

    <div class="workbench-grid">
      <aside class="flow-panel">
        <el-steps direction="vertical" :active="activeStep" finish-status="success">
          <el-step v-for="item in flowSteps" :key="item.value" :title="item.label" />
        </el-steps>
      </aside>

      <section class="result-panel" v-loading="loading">
        <div class="section-head">
          <h3>项目素材</h3>
          <el-button :icon="Refresh" @click="loadDetail">刷新</el-button>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="原文" name="document">
            <el-empty v-if="!documents.length" description="暂无原文" />
            <div v-for="doc in documents" :key="doc.documentId" class="doc-block">
              <div class="doc-meta">
                <el-tag>{{ doc.sourceType || 'TEXT' }}</el-tag>
                <span>{{ doc.charCount || 0 }} 字</span>
                <span>{{ doc.createTime || '-' }}</span>
              </div>
              <pre>{{ doc.rawText || doc.parsedText || '' }}</pre>
            </div>
          </el-tab-pane>

          <el-tab-pane label="人物/场景/分镜" name="assets">
            <div class="placeholder-grid">
              <div v-for="item in assetBlocks" :key="item.title" class="placeholder-block">
                <el-icon><component :is="item.icon" /></el-icon>
                <strong>{{ item.title }}</strong>
                <span>{{ item.status }}</span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="生成任务" name="task">
            <el-descriptions v-if="detail.latestTask" :column="2" border>
              <el-descriptions-item label="任务ID">{{ detail.latestTask.taskId }}</el-descriptions-item>
              <el-descriptions-item label="任务类型">{{ detail.latestTask.taskType }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ detail.latestTask.taskStatus }}</el-descriptions-item>
              <el-descriptions-item label="进度">{{ detail.latestTask.progress || 0 }}%</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无生成任务" />
          </el-tab-pane>
        </el-tabs>
      </section>

      <aside class="params-panel">
        <h3>生成参数</h3>
        <el-form label-position="top">
          <el-form-item label="默认画幅">
            <el-select v-model="params.defaultRatio" disabled>
              <el-option v-for="item in ratioOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认清晰度">
            <el-input v-model="params.defaultResolution" disabled />
          </el-form-item>
          <el-form-item label="镜头秒数">
            <el-input-number v-model="params.defaultShotDuration" disabled />
          </el-form-item>
          <el-form-item label="图片候选数">
            <el-input-number v-model="params.imageCandidateCount" disabled />
          </el-form-item>
          <el-form-item label="预览模式">
            <el-switch v-model="params.previewMode" active-value="1" inactive-value="0" disabled />
          </el-form-item>
        </el-form>
        <div class="action-stack">
          <el-button type="primary" :icon="MagicStick" disabled>润色原文</el-button>
          <el-button :icon="Picture" disabled>生成候选图</el-button>
          <el-button :icon="VideoCamera" disabled>生成单镜头视频</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Film, MagicStick, Picture, Refresh, UserFilled, VideoCamera } from '@element-plus/icons-vue'
import {
  aivideoProjectStageOptions,
  getAivideoProject,
  ratioOptions,
  type AivideoProjectDetail,
  type AivideoSourceDocument
} from '@/api/aivideo'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const activeTab = ref('document')
const detail = reactive<AivideoProjectDetail>({})
const documents = ref<AivideoSourceDocument[]>([])

const params = reactive({
  defaultRatio: '9:16',
  defaultResolution: '720p',
  defaultShotDuration: 5,
  imageCandidateCount: 3,
  previewMode: '1'
})

const flowSteps = [
  { label: '原文', value: 'DOCUMENT_SAVED' },
  { label: '润色', value: 'POLISH_CONFIRMED' },
  { label: '剧本', value: 'SCRIPT_CONFIRMED' },
  { label: '人物/场景/分镜', value: 'ASSET_CONFIRMED' },
  { label: '单镜头视频', value: 'VIDEO_GENERATING' },
  { label: '确认成片', value: 'VIDEO_CONFIRMED' }
]

const assetBlocks = [
  { title: '人物', status: 'MVP 1 接入提取与候选图', icon: UserFilled },
  { title: '场景', status: 'MVP 1 接入场景图', icon: Picture },
  { title: '分镜', status: 'MVP 1 接入镜头列表', icon: Film }
]

const activeStep = computed(() => {
  const stage = detail.project?.currentStage
  const index = flowSteps.findIndex((item) => item.value === stage)
  return index < 0 ? 0 : index + 1
})

function getStageLabel(value?: string) {
  return aivideoProjectStageOptions.find((item) => item.value === value)?.label || value || '草稿'
}

async function loadDetail() {
  const projectId = String(route.params.id)
  loading.value = true
  try {
    const res = await getAivideoProject(projectId)
    Object.assign(detail, res.data || {})
    documents.value = res.data.documents || []
    Object.assign(params, {
      defaultRatio: res.data.setting?.defaultRatio || res.data.project?.defaultRatio || '9:16',
      defaultResolution: res.data.setting?.defaultResolution || '720p',
      defaultShotDuration: res.data.setting?.defaultShotDuration || res.data.project?.defaultShotDuration || 5,
      imageCandidateCount: res.data.setting?.imageCandidateCount || res.data.project?.candidateImageCount || 3,
      previewMode: res.data.setting?.previewMode || res.data.project?.previewMode || '1'
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style lang="scss" scoped>
.workbench-page {
  padding: 20px;
}

.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 8px 0 0;
    font-size: 22px;
  }
}

.workbench-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 300px;
  gap: 16px;
  min-height: calc(100vh - 138px);
}

.flow-panel,
.result-panel,
.params-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  h3 {
    margin: 0;
  }
}

.doc-block {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;

  pre {
    margin: 12px 0 0;
    white-space: pre-wrap;
    word-break: break-word;
    color: #374151;
    line-height: 1.7;
  }
}

.doc-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #6b7280;
}

.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.placeholder-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 140px;
  padding: 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #4b5563;

  .el-icon {
    font-size: 28px;
    color: #409eff;
  }
}

.params-panel h3 {
  margin: 0 0 16px;
}

.action-stack {
  display: grid;
  gap: 10px;
  margin-top: 16px;

  .el-button {
    width: 100%;
    margin-left: 0;
  }
}

@media (max-width: 1200px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
}
</style>
