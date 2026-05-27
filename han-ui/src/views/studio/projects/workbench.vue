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
          <div class="content-compare-grid">
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
              :disabled="!selectedPolish || scriptStreaming"
              :loading="submitting || scriptStreaming"
              @click="handleGenerateScript"
            >
              {{ scriptVersions.length ? '重新生成剧本' : '生成剧本' }}
            </el-button>
          </div>
          <div class="content-compare-grid">
            <aside class="source-preview-panel">
              <div class="panel-title-row">
                <h4>已确认润色稿</h4>
                <el-tag v-if="selectedPolish" type="success">已确认</el-tag>
              </div>
              <div v-if="selectedPolish" class="meta-line">
                <el-tag>{{ selectedPolish.title || `润色稿 v${selectedPolish.versionNo}` }}</el-tag>
                <span>{{ selectedPolish.contentText?.length || 0 }} 字</span>
                <span>{{ selectedPolish.createTime || '-' }}</span>
              </div>
              <pre v-if="selectedPolish?.contentText" class="source-preview-text">{{ selectedPolish.contentText }}</pre>
              <el-empty v-else description="请先确认润色稿" />
            </aside>

            <div class="script-output-panel">
              <details class="prompt-preview">
                <summary>查看本次剧本提示词</summary>
                <pre>{{ scriptPromptPreviewText || '暂无可预览提示词' }}</pre>
              </details>
              <el-empty v-if="!scriptVersions.length && !scriptStreamText" description="暂无短剧剧本" />
              <article v-if="scriptStreamText" class="text-block stream-block">
                <div class="meta-line">
                  <el-tag type="warning">{{ scriptStreaming ? '生成中' : '最新生成' }}</el-tag>
                  <span v-if="scriptStreamMeta.taskId">任务 {{ scriptStreamMeta.taskId }}</span>
                  <span v-if="scriptStreamMeta.modelCode">{{ scriptStreamMeta.modelCode }}</span>
                </div>
                <MarkdownViewer :content="scriptStreamText" />
              </article>
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
                <MarkdownViewer :content="item.contentText" />
                <details class="raw-output">
                  <summary>查看原始 Markdown</summary>
                  <pre>{{ item.contentText }}</pre>
                </details>
              </article>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'assets'" class="result-section">
          <div class="section-head">
            <h3>人物 / 场景 / 分镜</h3>
            <div class="section-actions">
              <el-button
                type="primary"
                :icon="Film"
                :disabled="!selectedScript || assetStreaming"
                :loading="submitting || assetStreaming"
                @click="handleExtractAssets"
              >
                {{ hasAssets ? '重新提取' : '提取资产' }}
              </el-button>
              <el-button
                type="success"
                :icon="Check"
                :disabled="!hasAssets || confirmingAssetKeys.size > 0"
                :loading="confirmingAllAssets"
                @click="handleConfirmAllAssets"
              >
                确认全部
              </el-button>
            </div>
          </div>

          <div class="content-compare-grid asset-workspace-grid">
            <aside class="source-preview-panel">
              <div class="panel-title-row">
                <h4>已确认短剧剧本</h4>
                <el-tag v-if="selectedScript" type="success">已确认</el-tag>
              </div>
              <div v-if="selectedScript" class="meta-line">
                <el-tag>{{ selectedScript.title || `剧本 v${selectedScript.versionNo}` }}</el-tag>
                <span>{{ selectedScript.contentText?.length || 0 }} 字</span>
                <span>{{ selectedScript.createTime || '-' }}</span>
              </div>
              <MarkdownViewer v-if="selectedScript?.contentText" class="source-preview-text" :content="selectedScript.contentText" />
              <el-empty v-else description="请先确认短剧剧本" />
            </aside>

            <div class="asset-output-panel">
              <details class="prompt-preview">
                <summary>查看本次资产提取提示词</summary>
                <pre>{{ assetPromptPreviewText || '暂无可预览提示词' }}</pre>
              </details>
              <el-empty v-if="!assetPreviewText && !hasAssets" description="暂无资产提取输出" />
              <article v-if="assetPreviewText" class="text-block stream-block">
                <div class="meta-line">
                  <el-tag :type="assetStreaming ? 'warning' : 'success'">
                    {{ assetStreaming ? '生成中' : (latestAssetExtract?.title || 'Markdown/JSON 输出') }}
                  </el-tag>
                  <el-tag type="info" effect="plain">原始输出</el-tag>
                  <span v-if="assetStreamMeta.taskId">任务 {{ assetStreamMeta.taskId }}</span>
                  <span v-else-if="latestAssetExtract?.taskId">任务 {{ latestAssetExtract.taskId }}</span>
                  <span v-if="assetStreamMeta.modelCode">{{ assetStreamMeta.modelCode }}</span>
                </div>
                <JsonStructureViewer :content="assetPreviewText" />
                <details class="raw-output">
                  <summary>查看原始 Markdown/JSON</summary>
                  <pre class="asset-raw-output">{{ assetPreviewText }}</pre>
                </details>
              </article>
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
                    <el-button
                      size="small"
                      :disabled="row.confirmStatus === 'APPROVED' || confirmingAllAssets"
                      :loading="isAssetConfirming('CHARACTER', row.characterId)"
                      @click="handleConfirmAsset('CHARACTER', row.characterId)"
                    >
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
                <el-table-column label="场景图" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.lockedMediaId" type="success">已选 #{{ row.lockedMediaId }}</el-tag>
                    <el-tag v-else type="info">未选</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="190">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      :disabled="row.confirmStatus === 'APPROVED' || confirmingAllAssets"
                      :loading="isAssetConfirming('SCENE', row.sceneId)"
                      @click="handleConfirmAsset('SCENE', row.sceneId)"
                    >
                      确认
                    </el-button>
                    <el-button size="small" type="primary" plain @click="openSceneImageDrawer(row)">
                      场景图
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
                    <el-button
                      size="small"
                      :disabled="row.confirmStatus === 'APPROVED' || confirmingAllAssets"
                      :loading="isAssetConfirming('SHOT', row.shotId)"
                      @click="handleConfirmAsset('SHOT', row.shotId)"
                    >
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

    <el-drawer v-model="sceneImageDrawerVisible" size="680px" :title="sceneImageDrawerTitle">
      <div v-if="selectedSceneForImage" class="scene-image-drawer">
        <details class="prompt-preview" open>
          <summary>查看本次场景图提示词</summary>
          <pre>{{ sceneImagePromptPreviewText || '暂无可预览提示词' }}</pre>
        </details>

        <div class="scene-image-actions">
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="sceneImageGenerating"
            :disabled="sceneImageGenerating"
            @click="handleGenerateSceneImages"
          >
            生成 {{ params.imageCandidateCount || 2 }} 张候选图
          </el-button>
          <el-button :icon="Refresh" :disabled="sceneImageGenerating" @click="loadSceneImageCandidates">
            刷新候选
          </el-button>
        </div>

        <el-empty v-if="!sceneImageCandidates.length && !sceneImageGenerating" description="暂无场景候选图" />
        <div v-else class="scene-image-grid">
          <article
            v-for="item in sceneImageCandidates"
            :key="item.mediaId"
            class="scene-image-card"
            :class="{ selected: item.selected === '1' }"
          >
            <div class="scene-image-thumb">
              <img
                v-if="sceneImagePreviewUrls[String(item.mediaId)]"
                :src="sceneImagePreviewUrls[String(item.mediaId)]"
                alt="场景候选图"
              />
              <el-empty v-else description="图片加载中" />
            </div>
            <div class="scene-image-meta">
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">候选 {{ item.candidateNo }}</el-tag>
              <span v-if="item.taskId">任务 {{ item.taskId }}</span>
            </div>
            <el-button
              type="success"
              size="small"
              :disabled="item.selected === '1'"
              :loading="sceneImageSelectingIds.has(String(item.mediaId))"
              @click="handleSelectSceneImage(item)"
            >
              选择这张
            </el-button>
          </article>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, DocumentChecked, Film, MagicStick, Refresh, Tickets, Upload, UserFilled } from '@element-plus/icons-vue'
import {
  AIVIDEO_ASSET_STREAM_PATH,
  AIVIDEO_POLISH_STREAM_PATH,
  AIVIDEO_SCENE_IMAGE_STREAM_PATH,
  AIVIDEO_SCRIPT_STREAM_PATH,
  aivideoProjectStageOptions,
  confirmAivideoAsset,
  confirmAivideoDocument,
  confirmAivideoPolish,
  confirmAivideoScript,
  getAivideoProject,
  listAivideoMedia,
  previewAivideoMedia,
  previewAivideoAssetPrompt,
  previewAivideoPolishPrompt,
  previewAivideoSceneImagePrompt,
  previewAivideoScriptPrompt,
  ratioOptions,
  saveAivideoDocument,
  selectAivideoMedia,
  type AivideoMediaAsset,
  type AivideoProjectDetail,
  type AivideoScene
} from '@/api/aivideo'
import JsonStructureViewer from '@/components/aivideo/JsonStructureViewer.vue'
import MarkdownViewer from '@/components/aivideo/MarkdownViewer.vue'
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
const scriptStreaming = ref(false)
const scriptStreamText = ref('')
const scriptStreamMeta = ref<AiStreamMetaPayload>({})
const scriptPromptPreviewText = ref('')
const assetStreaming = ref(false)
const assetStreamText = ref('')
const assetStreamMeta = ref<AiStreamMetaPayload>({})
const assetPromptPreviewText = ref('')
const sceneImageDrawerVisible = ref(false)
const selectedSceneForImage = ref<AivideoScene>()
const sceneImagePromptPreviewText = ref('')
const sceneImageGenerating = ref(false)
const sceneImageCandidates = ref<AivideoMediaAsset[]>([])
const sceneImagePreviewUrls = ref<Record<string, string>>({})
const sceneImageSelectingIds = ref<Set<string>>(new Set())
const confirmingAllAssets = ref(false)
const confirmingAssetKeys = ref<Set<string>>(new Set())
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
  imageCandidateCount: 2,
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
const assetExtractVersions = computed(() => contentVersions.value.filter((item) => item.contentType === 'ASSET_EXTRACT'))
const selectedPolish = computed(() => polishVersions.value.find((item) => item.selected === '1'))
const selectedScript = computed(() => scriptVersions.value.find((item) => item.selected === '1'))
const latestAssetExtract = computed(() => assetExtractVersions.value[0])
const assetPreviewText = computed(() => assetStreamText.value || latestAssetExtract.value?.contentText || '')
const characters = computed(() => detail.characters || [])
const scenes = computed(() => detail.scenes || [])
const shots = computed(() => detail.shots || [])
const hasAssets = computed(() => characters.value.length > 0 || scenes.value.length > 0 || shots.value.length > 0)
const sceneImageDrawerTitle = computed(() => selectedSceneForImage.value?.sceneName
  ? `场景图候选：${selectedSceneForImage.value.sceneName}`
  : '场景图候选')
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
      imageCandidateCount: res.data.setting?.imageCandidateCount || res.data.project?.candidateImageCount || 2,
      previewMode: res.data.setting?.previewMode || res.data.project?.previewMode || '1'
    })
    await refreshPolishPromptPreview()
    await refreshScriptPromptPreview()
    await refreshAssetPromptPreview()
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
    refreshScriptPromptPreview()
    refreshAssetPromptPreview()
    refreshSceneImagePromptPreview()
  }, 350)
}

async function refreshScriptPromptPreview() {
  if (!selectedPolish.value) {
    scriptPromptPreviewText.value = ''
    return
  }
  try {
    const res = await previewAivideoScriptPrompt({
      projectId: projectId.value,
      customPrompt: customPrompt.value
    })
    scriptPromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    scriptPromptPreviewText.value = ''
  }
}

async function refreshAssetPromptPreview() {
  if (!selectedScript.value) {
    assetPromptPreviewText.value = ''
    return
  }
  try {
    const res = await previewAivideoAssetPrompt({
      projectId: projectId.value,
      customPrompt: customPrompt.value
    })
    assetPromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    assetPromptPreviewText.value = ''
  }
}

async function refreshSceneImagePromptPreview() {
  const scene = selectedSceneForImage.value
  if (!scene) {
    sceneImagePromptPreviewText.value = ''
    return
  }
  try {
    const res = await previewAivideoSceneImagePrompt({
      projectId: projectId.value,
      sceneId: scene.sceneId,
      candidateCount: params.imageCandidateCount || 2,
      ratio: params.defaultRatio,
      resolution: params.defaultResolution,
      customPrompt: customPrompt.value
    })
    sceneImagePromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    sceneImagePromptPreviewText.value = ''
  }
}

async function openSceneImageDrawer(scene: AivideoScene) {
  selectedSceneForImage.value = scene
  sceneImageDrawerVisible.value = true
  await refreshSceneImagePromptPreview()
  await loadSceneImageCandidates()
}

function revokeSceneImagePreviewUrls() {
  Object.values(sceneImagePreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  sceneImagePreviewUrls.value = {}
}

async function loadSceneImagePreviewUrl(asset: AivideoMediaAsset) {
  const key = String(asset.mediaId)
  try {
    const response = await previewAivideoMedia(asset.mediaId)
    const blob = (response as any).data as Blob
    if (!(blob instanceof Blob) || blob.size === 0) {
      return
    }
    const objectUrl = URL.createObjectURL(blob)
    const next = { ...sceneImagePreviewUrls.value }
    if (next[key]) {
      URL.revokeObjectURL(next[key])
    }
    next[key] = objectUrl
    sceneImagePreviewUrls.value = next
  } catch (_error) {
    // Preview errors are surfaced by the generation/list actions; keep the card placeholder.
  }
}

async function refreshSceneImagePreviewUrls(candidates: AivideoMediaAsset[]) {
  revokeSceneImagePreviewUrls()
  await Promise.all(candidates.map((item) => loadSceneImagePreviewUrl(item)))
}

async function loadSceneImageCandidates() {
  const scene = selectedSceneForImage.value
  if (!scene) {
    sceneImageCandidates.value = []
    revokeSceneImagePreviewUrls()
    return
  }
  const res = await listAivideoMedia({
    projectId: projectId.value,
    assetType: 'SCENE_IMAGE',
    bizType: 'SCENE',
    bizId: scene.sceneId
  })
  const candidates = res.data || []
  sceneImageCandidates.value = candidates
  await refreshSceneImagePreviewUrls(candidates)
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

async function handleGenerateScript() {
  if (!selectedPolish.value) {
    ElMessage.warning('请先确认润色稿')
    return
  }
  scriptStreaming.value = true
  submitting.value = true
  scriptStreamText.value = ''
  scriptStreamMeta.value = {}
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_SCRIPT_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        customPrompt: customPrompt.value
      },
      onDelta: ({ fullContent }) => {
        scriptStreamText.value = fullContent
      },
      onMeta: (payload) => {
        scriptStreamMeta.value = payload
      },
      onError: (message) => {
        ElMessage.error(message || '短剧剧本生成失败')
      }
    })
    ElMessage.success('短剧剧本已生成')
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '短剧剧本生成失败')
  } finally {
    scriptStreaming.value = false
    submitting.value = false
  }
}

function handleConfirmScript(versionId: string | number) {
  withSubmit(
    () => confirmAivideoScript({ projectId: projectId.value, versionId }).then(() => undefined),
    '短剧剧本已确认'
  )
}

async function handleExtractAssets() {
  if (!selectedScript.value) {
    ElMessage.warning('请先确认短剧剧本')
    return
  }
  assetStreaming.value = true
  submitting.value = true
  assetStreamText.value = ''
  assetStreamMeta.value = {}
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_ASSET_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        customPrompt: customPrompt.value
      },
      onDelta: ({ fullContent }) => {
        assetStreamText.value = fullContent
      },
      onMeta: (payload) => {
        assetStreamMeta.value = payload
      },
      onError: (message) => {
        ElMessage.error(message || '资产提取失败')
      }
    })
    ElMessage.success('资产已提取')
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '资产提取失败')
  } finally {
    assetStreaming.value = false
    submitting.value = false
  }
}

async function handleConfirmAllAssets() {
  if (confirmingAllAssets.value) {
    return
  }
  confirmingAllAssets.value = true
  try {
    await confirmAivideoAsset({ projectId: projectId.value, targetType: 'ALL' })
    ElMessage.success('资产已确认')
    await loadDetail()
  } finally {
    confirmingAllAssets.value = false
  }
}

async function handleConfirmAsset(targetType: string, targetId: string | number) {
  const key = assetConfirmKey(targetType, targetId)
  if (confirmingAssetKeys.value.has(key)) {
    return
  }
  setAssetConfirming(key, true)
  try {
    await confirmAivideoAsset({ projectId: projectId.value, targetType, targetId })
    ElMessage.success('资产已确认')
    await loadDetail()
  } finally {
    setAssetConfirming(key, false)
  }
}

async function handleGenerateSceneImages() {
  const scene = selectedSceneForImage.value
  if (!scene || sceneImageGenerating.value) {
    return
  }
  sceneImageGenerating.value = true
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_SCENE_IMAGE_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        sceneId: scene.sceneId,
        candidateCount: params.imageCandidateCount || 2,
        ratio: params.defaultRatio,
        resolution: params.defaultResolution,
        customPrompt: customPrompt.value
      },
      onMeta: (payload) => {
        if (payload.event === 'candidate' && payload.asset) {
          const asset = payload.asset as AivideoMediaAsset
          sceneImageCandidates.value = [
            asset,
            ...sceneImageCandidates.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
          ]
          void loadSceneImagePreviewUrl(asset)
        }
      },
      onError: (message) => {
        ElMessage.error(message || '场景图生成失败')
      }
    })
    ElMessage.success('场景图候选已生成')
    await loadSceneImageCandidates()
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '场景图生成失败')
  } finally {
    sceneImageGenerating.value = false
  }
}

async function handleSelectSceneImage(item: AivideoMediaAsset) {
  const key = String(item.mediaId)
  if (sceneImageSelectingIds.value.has(key)) {
    return
  }
  const next = new Set(sceneImageSelectingIds.value)
  next.add(key)
  sceneImageSelectingIds.value = next
  try {
    await selectAivideoMedia({
      projectId: projectId.value,
      mediaId: item.mediaId,
      bizType: 'SCENE',
      bizId: item.bizId
    })
    ElMessage.success('场景图已选定')
    await loadSceneImageCandidates()
    await loadDetail()
  } finally {
    const done = new Set(sceneImageSelectingIds.value)
    done.delete(key)
    sceneImageSelectingIds.value = done
  }
}

function isAssetConfirming(targetType: string, targetId: string | number) {
  return confirmingAssetKeys.value.has(assetConfirmKey(targetType, targetId))
}

function assetConfirmKey(targetType: string, targetId: string | number) {
  return `${targetType}:${targetId}`
}

function setAssetConfirming(key: string, confirming: boolean) {
  const next = new Set(confirmingAssetKeys.value)
  if (confirming) {
    next.add(key)
  } else {
    next.delete(key)
  }
  confirmingAssetKeys.value = next
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
  revokeSceneImagePreviewUrls()
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

.content-compare-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
  align-items: start;
}

.source-preview-panel,
.polish-output-panel,
.script-output-panel,
.asset-output-panel {
  min-width: 0;
}

.asset-workspace-grid {
  margin-bottom: 14px;
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

.scene-image-drawer {
  display: grid;
  gap: 14px;
}

.scene-image-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.scene-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.scene-image-card {
  display: grid;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
  background: #ffffff;

  &.selected {
    border-color: #67c23a;
    background: #f0f9eb;
  }
}

.scene-image-thumb {
  display: grid;
  place-items: center;
  overflow: hidden;
  width: 100%;
  aspect-ratio: 9 / 16;
  border-radius: 6px;
  background: #f3f4f6;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.scene-image-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #6b7280;
  font-size: 12px;
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

.raw-output {
  margin-top: 12px;

  summary {
    cursor: pointer;
    color: #2563eb;
    font-size: 13px;
    font-weight: 600;
  }
}

.source-preview-text,
.asset-raw-output,
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

  .content-compare-grid {
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
