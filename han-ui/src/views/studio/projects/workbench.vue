<template>
  <div class="workbench-page">
    <div class="workbench-header">
      <div class="title-block">
        <el-button :icon="ArrowLeft" text @click="router.push('/studio/projects')">项目列表</el-button>
        <h2>{{ detail.project?.projectName || '短剧项目' }}</h2>
      </div>
      <div class="header-actions">
        <el-tag type="primary" effect="plain">{{ getStageLabel(detail.project?.currentStage) }}</el-tag>
        <el-button :icon="Refresh" @click="loadDetail">刷新</el-button>
      </div>
    </div>

    <div class="workbench-grid">
      <aside class="flow-panel">
        <button
          v-for="item in flowSteps"
          :key="item.name"
          class="flow-item"
          :class="{ active: activeTab === item.name }"
          type="button"
          @click="activeTab = item.name"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <small>{{ item.count }}</small>
        </button>
      </aside>

      <section class="result-panel" v-loading="loading">
        <div v-if="activeTab === 'document'" class="result-section">
          <div class="section-head">
            <h3>原文</h3>
            <el-button
              type="primary"
              :icon="DocumentChecked"
              :disabled="!latestDocument || latestDocument.confirmed === '1'"
              :loading="submitting"
              @click="handleConfirmDocument"
            >
              确认原文
            </el-button>
          </div>
          <div v-if="!documents.length" class="document-editor">
            <div class="editor-toolbar">
              <el-select v-model="sourceDraft.sourceType" class="source-type-select">
                <el-option label="纯文本" value="TEXT" />
                <el-option label="Markdown" value="MARKDOWN" />
              </el-select>
              <el-button :icon="Upload" @click="triggerSourceFileSelect">导入 TXT/Markdown</el-button>
              <input
                ref="sourceFileInputRef"
                class="source-file-input"
                type="file"
                accept=".txt,.md,.markdown,text/plain,text/markdown"
                @change="handleSourceFileChange"
              />
            </div>
            <el-input
              v-model="sourceDraft.rawText"
              type="textarea"
              :rows="18"
              maxlength="200000"
              show-word-limit
              placeholder="粘贴小说、文档、剧情梗概或 Markdown，保存后再确认原文。"
            />
            <div class="editor-actions">
              <span class="editor-tip">保存后会生成一条待确认原文，用于后续润色和剧本生成。</span>
              <div class="editor-buttons">
                <el-button :disabled="!hasSourceDraftText" :loading="submitting" @click="handleSaveDocument(false)">
                  保存原文
                </el-button>
                <el-button type="primary" :disabled="!hasSourceDraftText" :loading="submitting" @click="handleSaveDocument(true)">
                  保存并确认原文
                </el-button>
              </div>
            </div>
          </div>
          <article v-for="doc in documents" :key="doc.documentId" class="text-block">
            <div class="meta-line">
              <el-tag>{{ doc.sourceType || 'TEXT' }}</el-tag>
              <span>{{ doc.charCount || 0 }} 字</span>
              <span>{{ doc.confirmed === '1' ? '已确认' : '待确认' }}</span>
              <span>{{ doc.createTime || '-' }}</span>
            </div>
            <pre>{{ doc.parsedText || doc.rawText || '' }}</pre>
          </article>
        </div>

        <div v-if="activeTab === 'polish'" class="result-section">
          <div class="section-head">
            <h3>润色稿</h3>
            <el-button
              type="primary"
              :icon="MagicStick"
              :disabled="!latestDocument || polishStreaming"
              :loading="submitting || polishStreaming"
              @click="handleGeneratePolish"
            >
              {{ polishVersions.length ? '重新润色' : '生成润色' }}
            </el-button>
          </div>
          <div class="polish-compare-grid">
            <aside class="source-preview-panel">
              <div class="panel-title-row">
                <h4>待润色原文</h4>
                <el-tag v-if="latestDocument" :type="latestDocument.confirmed === '1' ? 'success' : 'warning'">
                  {{ latestDocument.confirmed === '1' ? '已确认' : '待确认' }}
                </el-tag>
              </div>
              <div v-if="latestDocument" class="meta-line">
                <el-tag>{{ latestDocument.sourceType || 'TEXT' }}</el-tag>
                <span>{{ latestDocument.charCount || latestSourceText.length }} 字</span>
                <span>{{ latestDocument.createTime || '-' }}</span>
              </div>
              <pre v-if="latestSourceText" class="source-preview-text">{{ latestSourceText }}</pre>
              <el-empty v-else description="暂无可润色原文" />
            </aside>

            <div class="polish-output-panel">
              <details class="prompt-preview">
                <summary>查看本次润色提示词</summary>
                <pre>{{ polishPromptPreviewText || '暂无可预览提示词' }}</pre>
              </details>
              <el-empty v-if="!polishVersions.length && !polishStreamText" description="暂无润色稿" />
              <article v-if="polishStreamText" class="text-block stream-block">
                <div class="meta-line">
                  <el-tag type="warning">{{ polishStreaming ? '生成中' : '最新生成' }}</el-tag>
                  <span v-if="polishStreamMeta.taskId">任务 {{ polishStreamMeta.taskId }}</span>
                  <span v-if="polishStreamMeta.modelCode">{{ polishStreamMeta.modelCode }}</span>
                </div>
                <pre>{{ polishStreamText }}</pre>
              </article>
              <article v-for="item in polishVersions" :key="item.versionId" class="text-block">
                <div class="meta-line">
                  <el-tag :type="item.selected === '1' ? 'success' : 'info'">{{ item.title || `润色稿 v${item.versionNo}` }}</el-tag>
                  <span>{{ item.confirmStatus || 'PENDING' }}</span>
                  <span>{{ item.createTime || '-' }}</span>
                  <el-button
                    size="small"
                    type="success"
                    :icon="Check"
                    :disabled="item.selected === '1'"
                    :loading="submitting"
                    @click="handleConfirmPolish(item.versionId)"
                  >
                    确认
                  </el-button>
                </div>
                <pre>{{ item.contentText }}</pre>
              </article>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'script'" class="result-section">
          <div class="section-head">
            <h3>短剧剧本</h3>
            <el-button
              type="primary"
              :icon="Tickets"
              :disabled="!selectedPolish"
              :loading="submitting"
              @click="handleGenerateScript"
            >
              {{ scriptVersions.length ? '重新生成剧本' : '生成剧本' }}
            </el-button>
          </div>
          <el-empty v-if="!scriptVersions.length" description="暂无短剧剧本" />
          <article v-for="item in scriptVersions" :key="item.versionId" class="text-block">
            <div class="meta-line">
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">{{ item.title || `剧本 v${item.versionNo}` }}</el-tag>
              <span>{{ item.confirmStatus || 'PENDING' }}</span>
              <span>{{ item.createTime || '-' }}</span>
              <el-button
                size="small"
                type="success"
                :icon="Check"
                :disabled="item.selected === '1'"
                :loading="submitting"
                @click="handleConfirmScript(item.versionId)"
              >
                确认
              </el-button>
            </div>
            <pre>{{ item.contentText }}</pre>
          </article>
        </div>

        <div v-if="activeTab === 'assets'" class="result-section">
          <div class="section-head">
            <h3>人物 / 场景 / 分镜</h3>
            <div class="section-actions">
              <el-button
                type="primary"
                :icon="Film"
                :disabled="!selectedScript"
                :loading="submitting"
                @click="handleExtractAssets"
              >
                {{ hasAssets ? '重新提取' : '提取资产' }}
              </el-button>
              <el-button
                type="success"
                :icon="Check"
                :disabled="!hasAssets"
                :loading="submitting"
                @click="handleConfirmAllAssets"
              >
                确认全部
              </el-button>
            </div>
          </div>

          <el-tabs model-value="characters">
            <el-tab-pane label="人物" name="characters">
              <el-table :data="characters" border>
                <el-table-column prop="characterName" label="人物" min-width="120" />
                <el-table-column prop="storyRole" label="角色定位" min-width="120" />
                <el-table-column prop="appearance" label="外观" min-width="220" show-overflow-tooltip />
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.confirmStatus === 'APPROVED'" @click="handleConfirmAsset('CHARACTER', row.characterId)">
                      确认
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="场景" name="scenes">
              <el-table :data="scenes" border>
                <el-table-column prop="sceneName" label="场景" min-width="140" />
                <el-table-column prop="atmosphere" label="氛围" min-width="160" />
                <el-table-column prop="visualFeatures" label="视觉特征" min-width="240" show-overflow-tooltip />
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.confirmStatus === 'APPROVED'" @click="handleConfirmAsset('SCENE', row.sceneId)">
                      确认
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="分镜" name="shots">
              <el-table :data="shots" border>
                <el-table-column prop="shotNo" label="镜头" width="90" />
                <el-table-column prop="durationSec" label="秒数" width="90" />
                <el-table-column prop="cameraMovement" label="运动" min-width="120" />
                <el-table-column prop="actionDesc" label="动作" min-width="240" show-overflow-tooltip />
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.confirmStatus === 'APPROVED'" @click="handleConfirmAsset('SHOT', row.shotId)">
                      确认
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>

        <div v-if="activeTab === 'task'" class="result-section">
          <div class="section-head">
            <h3>最近任务</h3>
          </div>
          <el-descriptions v-if="detail.latestTask" :column="2" border>
            <el-descriptions-item label="任务ID">{{ detail.latestTask.taskId }}</el-descriptions-item>
            <el-descriptions-item label="任务类型">{{ detail.latestTask.taskType }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.latestTask.taskStatus }}</el-descriptions-item>
            <el-descriptions-item label="进度">{{ detail.latestTask.progress || 0 }}%</el-descriptions-item>
            <el-descriptions-item label="错误" :span="2">{{ detail.latestTask.errorMessage || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无生成任务" />
        </div>
      </section>

      <aside class="params-panel">
        <h3>参数</h3>
        <el-form label-position="top">
          <el-form-item label="画幅">
            <el-select v-model="params.defaultRatio" disabled>
              <el-option v-for="item in ratioOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="清晰度">
            <el-input v-model="params.defaultResolution" disabled />
          </el-form-item>
          <el-form-item label="镜头秒数">
            <el-input-number v-model="params.defaultShotDuration" disabled />
          </el-form-item>
          <el-form-item label="图片候选数">
            <el-input-number v-model="params.imageCandidateCount" disabled />
          </el-form-item>
          <el-form-item label="补充提示词">
            <el-input v-model="customPrompt" type="textarea" :rows="8" maxlength="1200" show-word-limit />
          </el-form-item>
        </el-form>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, DocumentChecked, Film, MagicStick, Refresh, Tickets, Upload, UserFilled } from '@element-plus/icons-vue'
import {
  AIVIDEO_POLISH_STREAM_PATH,
  aivideoProjectStageOptions,
  confirmAivideoAsset,
  confirmAivideoDocument,
  confirmAivideoPolish,
  confirmAivideoScript,
  extractAivideoAssets,
  generateAivideoScript,
  getAivideoProject,
  previewAivideoPolishPrompt,
  ratioOptions,
  saveAivideoDocument,
  type AivideoProjectDetail
} from '@/api/aivideo'
import { requestAiStream, type AiStreamMetaPayload } from '@/utils/ai-stream'
import { useUserStore } from '@/stores/user'

type WorkbenchTab = 'document' | 'polish' | 'script' | 'assets' | 'task'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const polishStreaming = ref(false)
const polishStreamText = ref('')
const polishStreamMeta = ref<AiStreamMetaPayload>({})
const polishPromptPreviewText = ref('')
const activeTab = ref<WorkbenchTab>('document')
const customPrompt = ref('')
const sourceFileInputRef = ref<HTMLInputElement>()
const detail = reactive<AivideoProjectDetail>({})
let promptPreviewTimer: ReturnType<typeof setTimeout> | undefined

const sourceDraft = reactive({
  sourceType: 'TEXT',
  fileName: '',
  rawText: ''
})

const params = reactive({
  defaultRatio: '9:16',
  defaultResolution: '720p',
  defaultShotDuration: 5,
  imageCandidateCount: 3,
  previewMode: '1'
})

const projectId = computed(() => String(route.params.id))
const documents = computed(() => detail.documents || [])
const latestDocument = computed(() => documents.value[0])
const latestSourceText = computed(() => latestDocument.value?.parsedText || latestDocument.value?.rawText || '')
const hasSourceDraftText = computed(() => sourceDraft.rawText.trim().length > 0)
const contentVersions = computed(() => detail.contentVersions || [])
const polishVersions = computed(() => contentVersions.value.filter((item) => item.contentType === 'POLISH'))
const scriptVersions = computed(() => contentVersions.value.filter((item) => item.contentType === 'SCRIPT'))
const selectedPolish = computed(() => polishVersions.value.find((item) => item.selected === '1'))
const selectedScript = computed(() => scriptVersions.value.find((item) => item.selected === '1'))
const characters = computed(() => detail.characters || [])
const scenes = computed(() => detail.scenes || [])
const shots = computed(() => detail.shots || [])
const hasAssets = computed(() => characters.value.length > 0 || scenes.value.length > 0 || shots.value.length > 0)
const flowSteps = computed(() => [
  { label: '原文', name: 'document' as WorkbenchTab, icon: DocumentChecked, count: documents.value.length },
  { label: '润色', name: 'polish' as WorkbenchTab, icon: MagicStick, count: polishVersions.value.length },
  { label: '剧本', name: 'script' as WorkbenchTab, icon: Tickets, count: scriptVersions.value.length },
  { label: '资产', name: 'assets' as WorkbenchTab, icon: UserFilled, count: characters.value.length + scenes.value.length + shots.value.length },
  { label: '任务', name: 'task' as WorkbenchTab, icon: Film, count: detail.latestTask ? 1 : 0 }
])

function getStageLabel(value?: string) {
  return aivideoProjectStageOptions.find((item) => item.value === value)?.label || value || '草稿'
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getAivideoProject(projectId.value)
    Object.assign(detail, res.data || {})
    Object.assign(params, {
      defaultRatio: res.data.setting?.defaultRatio || res.data.project?.defaultRatio || '9:16',
      defaultResolution: res.data.setting?.defaultResolution || '720p',
      defaultShotDuration: res.data.setting?.defaultShotDuration || res.data.project?.defaultShotDuration || 5,
      imageCandidateCount: res.data.setting?.imageCandidateCount || res.data.project?.candidateImageCount || 3,
      previewMode: res.data.setting?.previewMode || res.data.project?.previewMode || '1'
    })
    await refreshPolishPromptPreview()
  } finally {
    loading.value = false
  }
}

async function refreshPolishPromptPreview() {
  const doc = latestDocument.value
  if (!doc) {
    polishPromptPreviewText.value = ''
    return
  }
  try {
    const res = await previewAivideoPolishPrompt({
      projectId: projectId.value,
      documentId: doc.documentId,
      customPrompt: customPrompt.value
    })
    polishPromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    polishPromptPreviewText.value = ''
  }
}

function schedulePolishPromptPreview() {
  if (promptPreviewTimer) {
    clearTimeout(promptPreviewTimer)
  }
  promptPreviewTimer = setTimeout(() => {
    refreshPolishPromptPreview()
  }, 350)
}

async function withSubmit(action: () => Promise<void>, successMessage: string) {
  submitting.value = true
  try {
    await action()
    ElMessage.success(successMessage)
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

function handleConfirmDocument() {
  const doc = latestDocument.value
  if (!doc) return
  withSubmit(
    () => confirmAivideoDocument({
      projectId: projectId.value,
      documentId: doc.documentId,
      parsedText: doc.parsedText || doc.rawText
    }).then(() => undefined),
    '原文已确认'
  )
}

function triggerSourceFileSelect() {
  sourceFileInputRef.value?.click()
}

async function handleSourceFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!/\.(txt|md|markdown)$/i.test(file.name)) {
    ElMessage.warning('仅支持 TXT、Markdown 文件')
    input.value = ''
    return
  }
  const text = await file.text()
  sourceDraft.rawText = text
  sourceDraft.fileName = file.name
  sourceDraft.sourceType = /\.(md|markdown)$/i.test(file.name) ? 'MARKDOWN' : 'TEXT'
  input.value = ''
}

function handleSaveDocument(confirmAfterSave: boolean) {
  if (!hasSourceDraftText.value) {
    ElMessage.warning('请先填写原文内容')
    return
  }
  const rawText = sourceDraft.rawText.trim()
  withSubmit(
    async () => {
      const res = await saveAivideoDocument({
        projectId: projectId.value,
        sourceType: sourceDraft.sourceType,
        fileName: sourceDraft.fileName,
        rawText
      })
      if (confirmAfterSave) {
        await confirmAivideoDocument({
          projectId: projectId.value,
          documentId: res.data,
          parsedText: rawText
        })
      }
      sourceDraft.fileName = ''
      sourceDraft.rawText = ''
      sourceDraft.sourceType = 'TEXT'
    },
    confirmAfterSave ? '原文已保存并确认' : '原文已保存'
  )
}

async function handleGeneratePolish() {
  if (!latestDocument.value) {
    ElMessage.warning('请先保存并确认原文')
    return
  }
  polishStreaming.value = true
  submitting.value = true
  polishStreamText.value = ''
  polishStreamMeta.value = {}
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_POLISH_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        documentId: latestDocument.value.documentId,
        customPrompt: customPrompt.value
      },
      onDelta: ({ fullContent }) => {
        polishStreamText.value = fullContent
      },
      onMeta: (payload) => {
        polishStreamMeta.value = payload
      },
      onError: (message) => {
        ElMessage.error(message || '润色生成失败')
      }
    })
    ElMessage.success('润色稿已生成')
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '润色生成失败')
  } finally {
    polishStreaming.value = false
    submitting.value = false
  }
}

function handleConfirmPolish(versionId: string | number) {
  withSubmit(
    () => confirmAivideoPolish({ projectId: projectId.value, versionId }).then(() => undefined),
    '润色稿已确认'
  )
}

function handleGenerateScript() {
  withSubmit(
    () => generateAivideoScript({
      projectId: projectId.value,
      customPrompt: customPrompt.value
    }).then(() => undefined),
    '短剧剧本已生成'
  )
}

function handleConfirmScript(versionId: string | number) {
  withSubmit(
    () => confirmAivideoScript({ projectId: projectId.value, versionId }).then(() => undefined),
    '短剧剧本已确认'
  )
}

function handleExtractAssets() {
  withSubmit(
    () => extractAivideoAssets({
      projectId: projectId.value,
      customPrompt: customPrompt.value
    }).then(() => undefined),
    '资产已提取'
  )
}

function handleConfirmAllAssets() {
  withSubmit(
    () => confirmAivideoAsset({ projectId: projectId.value, targetType: 'ALL' }).then(() => undefined),
    '资产已确认'
  )
}

function handleConfirmAsset(targetType: string, targetId: string | number) {
  withSubmit(
    () => confirmAivideoAsset({ projectId: projectId.value, targetType, targetId }).then(() => undefined),
    '资产已确认'
  )
}

onMounted(() => {
  loadDetail()
})

watch(customPrompt, () => {
  schedulePolishPromptPreview()
})

onBeforeUnmount(() => {
  if (promptPreviewTimer) {
    clearTimeout(promptPreviewTimer)
  }
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
  gap: 16px;
  margin-bottom: 16px;
}

.title-block h2 {
  margin: 8px 0 0;
  font-size: 22px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.workbench-grid {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 300px;
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

.flow-panel {
  display: grid;
  align-content: start;
  gap: 10px;
}

.flow-item {
  display: grid;
  grid-template-columns: 24px 1fr auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #374151;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;

  small {
    color: #6b7280;
  }

  &.active {
    border-color: #2563eb;
    color: #1d4ed8;
    background: #eff6ff;
  }
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 18px;
  }
}

.section-actions {
  display: flex;
  gap: 10px;
}

.polish-compare-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
  align-items: start;
}

.source-preview-panel,
.polish-output-panel {
  min-width: 0;
}

.source-preview-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #f9fafb;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;

  h4 {
    margin: 0;
    font-size: 15px;
  }
}

.source-preview-text {
  max-height: calc(100vh - 300px);
  overflow: auto;
}

.prompt-preview {
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #eff6ff;

  summary {
    color: #1d4ed8;
    cursor: pointer;
    font-weight: 600;
  }

  pre {
    max-height: 300px;
    overflow: auto;
  }
}

.document-editor {
  display: grid;
  gap: 12px;
}

.editor-toolbar,
.editor-actions,
.editor-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.editor-toolbar {
  justify-content: flex-start;
}

.source-type-select {
  width: 132px;
}

.source-file-input {
  display: none;
}

.editor-actions {
  justify-content: space-between;
}

.editor-tip {
  color: #6b7280;
  font-size: 13px;
}

.text-block {
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
    font-family: inherit;
  }
}

.source-preview-text,
.prompt-preview pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #374151;
  line-height: 1.7;
  font-family: inherit;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  color: #6b7280;
}

.params-panel h3 {
  margin: 0 0 16px;
  font-size: 18px;
}

@media (max-width: 1200px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .polish-compare-grid {
    grid-template-columns: 1fr;
  }

  .flow-panel {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workbench-header,
  .section-head,
  .section-actions,
  .editor-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .editor-toolbar,
  .editor-buttons {
    align-items: stretch;
    flex-direction: column;
  }

  .source-type-select {
    width: 100%;
  }

  .flow-panel {
    grid-template-columns: 1fr;
  }
}
</style>
