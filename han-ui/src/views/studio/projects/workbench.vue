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
                <div class="content-action-bar">
                  <div class="meta-line">
                    <el-tag :type="item.selected === '1' ? 'success' : 'info'">{{ item.title || `润色稿 v${item.versionNo}` }}</el-tag>
                    <span>{{ item.confirmStatus || 'PENDING' }}</span>
                    <span>{{ item.createTime || '-' }}</span>
                  </div>
                  <div class="content-action-buttons">
                    <el-button
                      v-if="item.selected === '1'"
                      size="small"
                      type="warning"
                      plain
                      :loading="submitting"
                      @click="handleCancelConfirmPolish(item.versionId)"
                    >
                      取消确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="success"
                      :icon="Check"
                      :loading="submitting"
                      @click="handleConfirmPolish(item.versionId)"
                    >
                      确认这个润色稿
                    </el-button>
                  </div>
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
                <div class="content-action-bar">
                  <div class="meta-line">
                    <el-tag :type="item.selected === '1' ? 'success' : 'info'">{{ item.title || `剧本 v${item.versionNo}` }}</el-tag>
                    <span>{{ item.confirmStatus || 'PENDING' }}</span>
                    <span>{{ item.createTime || '-' }}</span>
                  </div>
                  <div class="content-action-buttons">
                    <el-button
                      v-if="item.selected === '1'"
                      size="small"
                      type="warning"
                      plain
                      :loading="submitting"
                      @click="handleCancelConfirmScript(item.versionId)"
                    >
                      取消确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="success"
                      :icon="Check"
                      :loading="submitting"
                      @click="handleConfirmScript(item.versionId)"
                    >
                      确认这个剧本
                    </el-button>
                  </div>
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
            <h3>角色 / 场景 / 分镜</h3>
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
              <el-tooltip
                :disabled="!assetConfirmDisabledReason"
                :content="assetConfirmDisabledReason"
                placement="top"
              >
                <span class="action-button-wrap">
                  <el-button
                    type="success"
                    :icon="Check"
                    :disabled="!!assetConfirmDisabledReason"
                    :loading="confirmingAllAssets"
                    @click="handleConfirmAllAssets"
                  >
                    确认全部
                  </el-button>
                </span>
              </el-tooltip>
              <el-button
                v-if="hasApprovedAssets"
                type="warning"
                plain
                :disabled="confirmingAssetKeys.size > 0"
                :loading="confirmingAllAssets"
                @click="handleCancelConfirmAllAssets"
              >
                取消全部确认
              </el-button>
            </div>
          </div>

          <div class="asset-context-toggle">
            <div>
              <strong>已确认剧本与提示词</strong>
              <el-tag v-if="selectedScript" type="success" effect="plain">剧本已确认</el-tag>
              <el-tag v-if="hasAssets" type="success" effect="plain">资产已提取</el-tag>
              <el-tag v-if="hasAssets" type="info" effect="plain">
                角色 {{ assetCounts.characters }} / 场景 {{ assetCounts.scenes }} / 分镜 {{ assetCounts.shots }}
              </el-tag>
            </div>
            <el-button
              text
              type="primary"
              :icon="assetContextCollapsed ? ArrowDown : ArrowUp"
              @click="assetContextCollapsed = !assetContextCollapsed"
            >
              {{ assetContextCollapsed ? '展开上下文' : '收起上下文' }}
            </el-button>
          </div>

          <el-alert
            v-if="assetExtractNotStructured"
            class="asset-status-alert"
            title="资产提取结果还没有结构化入库，暂时不能确认"
            description="当前页面只保留了 AI 返回的原始输出，还没有形成可确认的角色、场景、分镜记录。请重新提取资产；系统会兼容缺少外层大括号的 JSON 输出。"
            type="warning"
            show-icon
            :closable="false"
          />

          <div v-show="!assetContextCollapsed || assetStreaming" class="content-compare-grid asset-workspace-grid">
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
            <el-tab-pane label="角色" name="characters">
              <el-table :data="characters" border>
                <el-table-column prop="characterName" label="角色名称" min-width="120" />
                <el-table-column prop="storyRole" label="角色定位" min-width="120" />
                <el-table-column prop="appearance" label="形象描述" min-width="220" show-overflow-tooltip />
                <el-table-column label="角色图" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.lockedMediaId" type="success">已选 #{{ row.lockedMediaId }}</el-tag>
                    <el-tag v-else type="info">未选</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="240">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.confirmStatus !== 'APPROVED'"
                      size="small"
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('CHARACTER', row.characterId)"
                      @click="handleConfirmAsset('CHARACTER', row.characterId)"
                    >
                      确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="warning"
                      plain
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('CHARACTER', row.characterId)"
                      @click="handleCancelConfirmAsset('CHARACTER', row.characterId)"
                    >
                      取消确认
                    </el-button>
                    <el-button size="small" type="primary" plain @click="openCharacterImageDrawer(row)">
                      角色图
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
                <el-table-column label="操作" width="240">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.confirmStatus !== 'APPROVED'"
                      size="small"
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('SCENE', row.sceneId)"
                      @click="handleConfirmAsset('SCENE', row.sceneId)"
                    >
                      确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="warning"
                      plain
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('SCENE', row.sceneId)"
                      @click="handleCancelConfirmAsset('SCENE', row.sceneId)"
                    >
                      取消确认
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
                <el-table-column label="视频" width="160">
                  <template #default="{ row }">
                    <el-tag v-if="row.videoMediaId" type="success">已选 #{{ row.videoMediaId }}</el-tag>
                    <el-tag v-else type="info">未选</el-tag>
                    <el-tag v-if="row.tailFrameMediaId" class="shot-tail-frame-tag" type="success" effect="plain">
                      尾帧 #{{ row.tailFrameMediaId }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="240">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.confirmStatus !== 'APPROVED'"
                      size="small"
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('SHOT', row.shotId)"
                      @click="handleConfirmAsset('SHOT', row.shotId)"
                    >
                      确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="warning"
                      plain
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('SHOT', row.shotId)"
                      @click="handleCancelConfirmAsset('SHOT', row.shotId)"
                    >
                      取消确认
                    </el-button>
                    <el-button size="small" type="primary" plain :icon="VideoCamera" @click="openShotVideoDrawer(row)">
                      视频
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
          <el-form-item label="视频候选数">
            <el-input-number v-model="params.videoCandidateCount" disabled />
          </el-form-item>
          <el-form-item label="当前策略">
            <div class="strategy-grid">
              <el-select v-model="params.defaultStyle" filterable allow-create default-first-option placeholder="视觉风格">
                <el-option v-for="item in visualStyleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.generationStrategy" placeholder="生成策略">
                <el-option v-for="item in generationStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.audioMode" placeholder="声音模式">
                <el-option v-for="item in audioModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.subtitleMode" placeholder="字幕模式">
                <el-option v-for="item in subtitleModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.referenceStrategy" placeholder="参考素材">
                <el-option v-for="item in referenceStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.actionIntensity" placeholder="动作强度">
                <el-option v-for="item in actionIntensityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.continuityLevel" placeholder="连续性">
                <el-option v-for="item in continuityLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.multiRoleStrategy" placeholder="多角色">
                <el-option v-for="item in multiRoleStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
          </el-form-item>
          <el-form-item label="本阶段生效">
            <div class="stage-strategy-summary">
              <el-tag
                v-for="item in activeStageStrategyItems"
                :key="item.key"
                effect="plain"
              >
                {{ item.label }}：{{ item.value }}
              </el-tag>
              <span class="stage-strategy-note">
                当前作用域会和全局追加一起发送，未列出的策略不会硬塞进本阶段提示词。
              </span>
            </div>
          </el-form-item>
          <el-form-item label="高级追加提示词">
            <div class="prompt-scope-editor">
              <el-select v-model="activePromptScope">
                <el-option v-for="item in promptScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input
                v-model="promptScopes[activePromptScope]"
                type="textarea"
                :rows="6"
                maxlength="1200"
                show-word-limit
                placeholder="这里只追加当前作用域提示词；全局追加会和当前阶段提示词一起发送"
              />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              plain
              :loading="savingStrategies"
              @click="handleSaveProjectStrategies"
            >
              保存当前策略
            </el-button>
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

        <el-form class="reference-image-form" label-position="top">
          <el-form-item label="场景参考图（可粘贴 URL 或上传）">
            <div class="reference-image-control">
              <el-input
                v-model="sceneImageReferenceUrl"
                clearable
                placeholder="粘贴参考图地址；会写入本次场景图提示词，用于锁定空间、光线、天气和色调"
              />
              <el-upload
                accept="image/*"
                :show-file-list="false"
                :http-request="uploadSceneReferenceImage"
                :disabled="sceneReferenceUploading || sceneImageGenerating"
              >
                <el-button
                  :icon="Upload"
                  :loading="sceneReferenceUploading"
                  :disabled="sceneImageGenerating"
                >
                  上传参考图
                </el-button>
              </el-upload>
            </div>
            <div v-if="sceneImageReferenceUrl" class="reference-image-tip">
              <span>当前参考图 URL 已生效。</span>
              <el-link :href="sceneImageReferenceUrl" target="_blank" type="primary">打开查看</el-link>
            </div>
          </el-form-item>
        </el-form>

        <div class="scene-image-actions">
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="sceneImageGenerating"
            :disabled="sceneImageGenerating || sceneReferenceUploading"
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

    <el-drawer v-model="characterImageDrawerVisible" size="680px" :title="characterImageDrawerTitle">
      <div v-if="selectedCharacterForImage" class="scene-image-drawer">
        <details class="prompt-preview" open>
          <summary>查看本次角色图提示词</summary>
          <pre>{{ characterImagePromptPreviewText || '暂无可预览提示词' }}</pre>
        </details>

        <el-form class="reference-image-form" label-position="top">
          <el-form-item label="角色参考图（可粘贴 URL 或上传）">
            <div class="reference-image-control">
              <el-input
                v-model="characterImageReferenceUrl"
                clearable
                placeholder="粘贴参考图地址；会写入本次角色图提示词，用于锁定身份、外观轮廓、毛发/服装和色彩"
              />
              <el-upload
                accept="image/*"
                :show-file-list="false"
                :http-request="uploadCharacterReferenceImage"
                :disabled="characterReferenceUploading || characterImageGenerating"
              >
                <el-button
                  :icon="Upload"
                  :loading="characterReferenceUploading"
                  :disabled="characterImageGenerating"
                >
                  上传参考图
                </el-button>
              </el-upload>
            </div>
            <div v-if="characterImageReferenceUrl" class="reference-image-tip">
              <span>当前参考图 URL 已生效。</span>
              <el-link :href="characterImageReferenceUrl" target="_blank" type="primary">打开查看</el-link>
            </div>
          </el-form-item>
        </el-form>

        <div class="scene-image-actions">
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="characterImageGenerating"
            :disabled="characterImageGenerating || characterReferenceUploading"
            @click="handleGenerateCharacterImages"
          >
            生成 {{ params.imageCandidateCount || 2 }} 张候选图
          </el-button>
          <el-button :icon="Refresh" :disabled="characterImageGenerating" @click="loadCharacterImageCandidates">
            刷新候选
          </el-button>
        </div>

        <el-empty v-if="!characterImageCandidates.length && !characterImageGenerating" description="暂无角色候选图" />
        <div v-else class="scene-image-grid">
          <article
            v-for="item in characterImageCandidates"
            :key="item.mediaId"
            class="scene-image-card"
            :class="{ selected: item.selected === '1' }"
          >
            <div class="scene-image-thumb">
              <img
                v-if="characterImagePreviewUrls[String(item.mediaId)]"
                :src="characterImagePreviewUrls[String(item.mediaId)]"
                alt="角色候选图"
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
              :loading="characterImageSelectingIds.has(String(item.mediaId))"
              @click="handleSelectCharacterImage(item)"
            >
              选择这张
            </el-button>
          </article>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="shotVideoDrawerVisible" size="760px" :title="shotVideoDrawerTitle">
      <div v-if="selectedShotForVideo" class="scene-image-drawer">
        <el-form class="shot-video-strategy" label-position="top">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="生成策略">
                <el-select v-model="params.generationStrategy">
                  <el-option v-for="item in generationStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="声音模式">
                <el-select v-model="params.audioMode">
                  <el-option v-for="item in audioModeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="连续性">
                <el-select v-model="params.continuityLevel">
                  <el-option v-for="item in continuityLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <details class="prompt-preview" open>
          <summary>查看本次分镜视频提示词</summary>
          <pre>{{ shotVideoPromptPreviewText || '暂无可预览提示词' }}</pre>
        </details>

        <el-alert
          v-if="previousShotVideoRequired"
          class="shot-video-gate-alert"
          type="warning"
          show-icon
          :closable="false"
          :title="previousShotGateMessage"
        />

        <div class="scene-image-actions">
          <el-button
            type="primary"
            :icon="VideoCamera"
            :loading="shotVideoGenerating"
            :disabled="shotVideoActionLocked"
            @click="handleGenerateShotVideos"
          >
            生成 {{ params.videoCandidateCount || 1 }} 条候选视频
          </el-button>
          <el-button
            :icon="Refresh"
            :loading="shotVideoGenerating"
            :disabled="shotVideoGenerating"
            @click="handleRefreshShotVideoCandidates"
          >
            刷新候选
          </el-button>
        </div>

        <el-empty v-if="!shotVideoCandidates.length && !shotVideoGenerating && !shotVideoTasks.length && !shotVideoLoading" description="暂无分镜视频候选" />
        <div v-if="shotVideoCandidates.length || shotVideoGenerating" class="scene-image-grid">
          <article
            v-for="item in shotVideoCandidates"
            :key="item.mediaId"
            class="scene-image-card"
            :class="{ selected: item.selected === '1' }"
          >
            <div class="scene-image-thumb">
              <video
                v-if="shotVideoPreviewUrls[String(item.mediaId)]"
                :src="shotVideoPreviewUrls[String(item.mediaId)]"
                controls
                preload="metadata"
                playsinline
              />
              <el-empty v-else description="视频加载中" />
            </div>
            <div class="scene-image-meta">
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">候选 {{ item.candidateNo }}</el-tag>
              <span v-if="item.taskId">任务 {{ item.taskId }}</span>
            </div>
            <el-button
              type="success"
              size="small"
              :disabled="item.selected === '1'"
              :loading="shotVideoSelectingIds.has(String(item.mediaId))"
              @click="handleSelectShotVideo(item)"
            >
              选择这条
            </el-button>
          </article>
        </div>

        <div v-if="shotVideoTasks.length" class="shot-video-task-list">
          <article v-for="task in shotVideoTasks" :key="task.taskId" class="shot-video-task-card">
            <div class="shot-video-task-head">
              <strong>任务 {{ task.taskId }}</strong>
              <el-tag :type="getTaskStatusTagType(task.taskStatus)" effect="plain">
                {{ formatTaskStatus(task.taskStatus) }}
              </el-tag>
            </div>
            <div class="shot-video-task-meta">
              <span v-if="task.providerTaskId">火山任务 {{ task.providerTaskId }}</span>
              <span v-if="task.updateTime">更新时间 {{ task.updateTime }}</span>
            </div>
            <el-progress
              :percentage="normalizeTaskProgress(task)"
              :status="getTaskProgressStatus(task.taskStatus)"
            />
            <p v-if="shouldShowTaskMessage(task)" class="shot-video-task-message">{{ task.errorMessage }}</p>
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
import type { UploadRequestOptions } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowUp, Check, DocumentChecked, Film, MagicStick, Refresh, Tickets, Upload, UserFilled, VideoCamera } from '@element-plus/icons-vue'
import {
  AIVIDEO_ASSET_STREAM_PATH,
  AIVIDEO_CHARACTER_IMAGE_STREAM_PATH,
  AIVIDEO_POLISH_STREAM_PATH,
  AIVIDEO_SCENE_IMAGE_STREAM_PATH,
  AIVIDEO_SCRIPT_STREAM_PATH,
  AIVIDEO_SHOT_VIDEO_STREAM_PATH,
  actionIntensityOptions,
  aivideoProjectStageOptions,
  audioModeOptions,
  cancelConfirmAivideoAsset,
  cancelConfirmAivideoPolish,
  cancelConfirmAivideoScript,
  continuityLevelOptions,
  confirmAivideoAsset,
  confirmAivideoDocument,
  confirmAivideoPolish,
  confirmAivideoScript,
  generationStrategyOptions,
  getAivideoProject,
  listAivideoMedia,
  listAivideoShotVideoTasks,
  multiRoleStrategyOptions,
  previewAivideoMedia,
  previewAivideoAssetPrompt,
  previewAivideoCharacterImagePrompt,
  previewAivideoPolishPrompt,
  previewAivideoSceneImagePrompt,
  previewAivideoShotVideoPrompt,
  previewAivideoScriptPrompt,
  ratioOptions,
  referenceStrategyOptions,
  saveAivideoDocument,
  selectAivideoMedia,
  subtitleModeOptions,
  updateAivideoProject,
  uploadAivideoReferenceImage,
  visualStyleOptions,
  type AivideoCharacter,
  type AivideoMediaAsset,
  type AivideoProjectDetail,
  type AivideoScene,
  type AivideoShot,
  type AivideoTask
} from '@/api/aivideo'
import JsonStructureViewer from '@/components/aivideo/JsonStructureViewer.vue'
import MarkdownViewer from '@/components/aivideo/MarkdownViewer.vue'
import { requestAiStream, type AiStreamMetaPayload } from '@/utils/ai-stream'
import { useUserStore } from '@/stores/user'

type WorkbenchTab = 'document' | 'polish' | 'script' | 'assets' | 'task'
type PromptScope = 'global' | 'polish' | 'script' | 'asset' | 'characterImage' | 'sceneImage' | 'shotVideo'
type StrategyKey = 'defaultStyle' | 'generationStrategy' | 'audioMode' | 'subtitleMode' | 'referenceStrategy' | 'actionIntensity' | 'continuityLevel' | 'multiRoleStrategy'

interface StageStrategyItem {
  key: StrategyKey
  label: string
  value: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const savingStrategies = ref(false)
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
const characterImageDrawerVisible = ref(false)
const selectedCharacterForImage = ref<AivideoCharacter>()
const characterImagePromptPreviewText = ref('')
const characterImageReferenceUrl = ref('')
const characterReferenceUploading = ref(false)
const characterImageGenerating = ref(false)
const characterImageCandidates = ref<AivideoMediaAsset[]>([])
const characterImagePreviewUrls = ref<Record<string, string>>({})
const characterImageSelectingIds = ref<Set<string>>(new Set())
const sceneImageDrawerVisible = ref(false)
const selectedSceneForImage = ref<AivideoScene>()
const sceneImagePromptPreviewText = ref('')
const sceneImageReferenceUrl = ref('')
const sceneReferenceUploading = ref(false)
const sceneImageGenerating = ref(false)
const sceneImageCandidates = ref<AivideoMediaAsset[]>([])
const sceneImagePreviewUrls = ref<Record<string, string>>({})
const sceneImageSelectingIds = ref<Set<string>>(new Set())
const shotVideoDrawerVisible = ref(false)
const selectedShotForVideo = ref<AivideoShot>()
const shotVideoPromptPreviewText = ref('')
const shotVideoLoading = ref(false)
const shotVideoGenerating = ref(false)
const shotVideoCandidates = ref<AivideoMediaAsset[]>([])
const shotVideoTasks = ref<AivideoTask[]>([])
const shotVideoPreviewUrls = ref<Record<string, string>>({})
const shotVideoSelectingIds = ref<Set<string>>(new Set())
const confirmingAllAssets = ref(false)
const confirmingAssetKeys = ref<Set<string>>(new Set())
const activeTab = ref<WorkbenchTab>('document')
const activePromptScope = ref<PromptScope>('global')
const promptScopes = reactive<Record<PromptScope, string>>({
  global: '',
  polish: '',
  script: '',
  asset: '',
  characterImage: '',
  sceneImage: '',
  shotVideo: ''
})
const assetContextCollapsed = ref(true)
const sourceFileInputRef = ref<HTMLInputElement>()
const detail = reactive<AivideoProjectDetail>({})
let promptPreviewTimer: ReturnType<typeof setTimeout> | undefined
let shotVideoRecoveryTimer: ReturnType<typeof setTimeout> | undefined
const inFlightTaskStatuses = new Set(['PENDING', 'RUNNING'])
const SHOT_VIDEO_RECOVERY_INTERVAL = 15_000

const sourceDraft = reactive({
  sourceType: 'TEXT',
  fileName: '',
  rawText: ''
})

const params = reactive({
  defaultRatio: '9:16',
  defaultStyle: '写实电影感',
  defaultResolution: '720p',
  defaultShotDuration: 5,
  imageCandidateCount: 2,
  videoCandidateCount: 1,
  previewMode: '1',
  generationStrategy: 'AUTO',
  audioMode: 'SILENT',
  subtitleMode: 'NONE',
  referenceStrategy: 'CHARACTER_SCENE',
  actionIntensity: 'NORMAL',
  continuityLevel: 'STRICT',
  multiRoleStrategy: 'SINGLE_FIRST'
})

const promptScopeOptions: Array<{ label: string; value: PromptScope }> = [
  { label: '全局追加', value: 'global' },
  { label: '润色追加', value: 'polish' },
  { label: '剧本追加', value: 'script' },
  { label: '资产提取追加', value: 'asset' },
  { label: '角色图追加', value: 'characterImage' },
  { label: '场景图追加', value: 'sceneImage' },
  { label: '分镜视频追加', value: 'shotVideo' }
]

const stageStrategyKeys: Record<PromptScope, StrategyKey[]> = {
  global: ['defaultStyle', 'generationStrategy', 'audioMode', 'subtitleMode', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  polish: ['defaultStyle'],
  script: ['defaultStyle', 'generationStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  asset: ['defaultStyle', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  characterImage: ['defaultStyle', 'referenceStrategy', 'multiRoleStrategy'],
  sceneImage: ['defaultStyle', 'referenceStrategy', 'continuityLevel'],
  shotVideo: ['defaultStyle', 'generationStrategy', 'audioMode', 'subtitleMode', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy']
}

const strategyLabels: Record<StrategyKey, string> = {
  defaultStyle: '视觉风格',
  generationStrategy: '生成策略',
  audioMode: '声音模式',
  subtitleMode: '字幕模式',
  referenceStrategy: '参考素材策略',
  actionIntensity: '动作强度',
  continuityLevel: '连续性强度',
  multiRoleStrategy: '多角色策略'
}

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
const assetCounts = computed(() => ({
  characters: characters.value.length,
  scenes: scenes.value.length,
  shots: shots.value.length
}))
const hasAssets = computed(() => characters.value.length > 0 || scenes.value.length > 0 || shots.value.length > 0)
const assetExtractNotStructured = computed(() => !!assetPreviewText.value.trim() && !hasAssets.value && !assetStreaming.value)
const assetConfirmDisabledReason = computed(() => {
  if (confirmingAssetKeys.value.size > 0) {
    return '单条资产确认处理中，请稍后再试'
  }
  if (assetStreaming.value) {
    return '资产正在提取中，请等待结构化入库完成'
  }
  if (assetExtractNotStructured.value) {
    return '当前只有原始输出，没有可确认的结构化资产，请重新提取资产'
  }
  if (!hasAssets.value) {
    return '请先提取资产'
  }
  return ''
})
const hasApprovedAssets = computed(() => [
  ...characters.value,
  ...scenes.value,
  ...shots.value
].some((item) => item.confirmStatus === 'APPROVED'))
const activeStageStrategyItems = computed(() => stageStrategyItems(activePromptScope.value))
const sceneImageDrawerTitle = computed(() => selectedSceneForImage.value?.sceneName
  ? `场景图候选：${selectedSceneForImage.value.sceneName}`
  : '场景图候选')
const characterImageDrawerTitle = computed(() => selectedCharacterForImage.value?.characterName
  ? `角色图候选：${selectedCharacterForImage.value.characterName}`
  : '角色图候选')
const shotVideoDrawerTitle = computed(() => selectedShotForVideo.value
  ? `分镜视频候选：第 ${selectedShotForVideo.value.episodeNo || 1} 集 / 镜头 ${selectedShotForVideo.value.shotNo || '-'}`
  : '分镜视频候选')
const flowSteps = computed(() => [
  { label: '原文', name: 'document' as WorkbenchTab, icon: DocumentChecked, count: documents.value.length },
  { label: '润色', name: 'polish' as WorkbenchTab, icon: MagicStick, count: polishVersions.value.length },
  { label: '剧本', name: 'script' as WorkbenchTab, icon: Tickets, count: scriptVersions.value.length },
  { label: '资产', name: 'assets' as WorkbenchTab, icon: UserFilled, count: characters.value.length + scenes.value.length + shots.value.length },
  { label: '任务', name: 'task' as WorkbenchTab, icon: Film, count: detail.latestTask ? 1 : 0 }
])
const hasRunningShotVideoTask = computed(() => shotVideoTasks.value.some(isShotVideoTaskInFlight))
const hasRecoverableShotVideoTask = computed(() => shotVideoTasks.value.some(isRecoverableShotVideoTask))
const previousShotForVideo = computed(() => {
  const current = selectedShotForVideo.value
  if (!current) {
    return undefined
  }
  const currentEpisodeNo = Number(current.episodeNo || 1)
  const currentShotNo = Number(current.shotNo || 0)
  if (!currentShotNo) {
    return undefined
  }
  return [...shots.value]
    .filter((item) => String(item.shotId) !== String(current.shotId))
    .filter((item) => Number(item.episodeNo || 1) === currentEpisodeNo)
    .filter((item) => Number(item.shotNo || 0) < currentShotNo)
    .sort((a, b) => Number(b.shotNo || 0) - Number(a.shotNo || 0))[0]
})
const previousShotVideoRequired = computed(() => !!previousShotForVideo.value && !previousShotForVideo.value.videoMediaId)
const previousShotGateMessage = computed(() => {
  const previous = previousShotForVideo.value
  if (!previous) {
    return ''
  }
  return `请先为上一分镜（第 ${previous.episodeNo || 1} 集 / 镜头 ${previous.shotNo || '-'}）选择并确认视频，系统会把它的尾帧作为当前分镜衔接参考`
})
const shotVideoActionLocked = computed(() => shotVideoLoading.value
  || shotVideoGenerating.value
  || hasRunningShotVideoTask.value
  || previousShotVideoRequired.value)

function getStageLabel(value?: string) {
  return aivideoProjectStageOptions.find((item) => item.value === value)?.label || value || '草稿'
}

function parseParamsJson(paramsJson?: string) {
  if (!paramsJson) {
    return {}
  }
  try {
    return JSON.parse(paramsJson) as Record<string, string>
  } catch (_error) {
    return {}
  }
}

function strategyValue(settingParams: Record<string, string>, key: string, fallback: string) {
  const value = settingParams[key]
  return value && String(value).trim() ? String(value).trim() : fallback
}

function optionLabel(options: Array<{ label: string; value: string }>, value?: string) {
  return options.find((item) => item.value === value)?.label || value || ''
}

function strategyDisplayValue(key: StrategyKey) {
  switch (key) {
    case 'defaultStyle':
      return params.defaultStyle
    case 'generationStrategy':
      return optionLabel(generationStrategyOptions, params.generationStrategy)
    case 'audioMode':
      return optionLabel(audioModeOptions, params.audioMode)
    case 'subtitleMode':
      return optionLabel(subtitleModeOptions, params.subtitleMode)
    case 'referenceStrategy':
      return optionLabel(referenceStrategyOptions, params.referenceStrategy)
    case 'actionIntensity':
      return optionLabel(actionIntensityOptions, params.actionIntensity)
    case 'continuityLevel':
      return optionLabel(continuityLevelOptions, params.continuityLevel)
    case 'multiRoleStrategy':
      return optionLabel(multiRoleStrategyOptions, params.multiRoleStrategy)
    default:
      return ''
  }
}

function stageStrategyItems(scope: PromptScope): StageStrategyItem[] {
  return (stageStrategyKeys[scope] || []).map((key) => ({
    key,
    label: strategyLabels[key],
    value: strategyDisplayValue(key)
  })).filter((item) => item.value)
}

function stagePromptHints(scope: PromptScope) {
  const hints: Partial<Record<PromptScope, string[]>> = {
    polish: [
      '润色阶段只统一世界观、角色气质、画面风格和叙事语气，不提前塞入过密分镜动作。'
    ],
    script: [
      '剧本阶段必须判断每段适合拆成几个镜头，每个镜头只保留可拍的主动作。',
      '按动作预算拆分：5秒=1个主动作+1个反应/表情+结尾状态；6秒=2个连续动作+结尾状态；8秒=3个连续动作+明确结尾状态。',
      '超过3个动作beat或出现倒地起身、悬浮、变身、打斗、救援等强动作时，自动拆镜，不要硬塞进同一镜头。'
    ],
    asset: [
      '资产阶段必须输出稳定的角色锚点、场景锚点和分镜动作预算，便于后续图片/视频生成继承。',
      '出现爪子、手、脚、翅膀、尾巴等部位时，必须在分镜里写清楚构图需要露出对应部位。'
    ],
    characterImage: [
      '角色图阶段只锁定同一角色身份、体型、毛色/发型、服装与显著特征，不新增剧情动作。',
      '角色图必须输出 Seedance 视频角色锚定图：单一主体、3/4正面或轻微侧正面、全身完整可见、主体占画面高度60%-75%、纯白/浅灰极简背景。',
      '禁止四方向、三视图、多视图、转面表、分栏拼图、同款分身或多个角度并排；动物保持物种本体和自然四足站姿，不拟人化。'
    ],
    sceneImage: [
      '场景图阶段只锁定空间结构、天气、光线、色调、道具和前后景关系，不改变剧情设定。',
      '场景图必须是可作为 Seedance 首帧/环境锚点的单镜头纯场景图：前景/中景/远景清楚，留出角色可行动区域，禁止拼图、分栏、设定板、漫画格和文字标签。'
    ],
    shotVideo: [
      '视频阶段输出给模型直接执行的镜头 prompt，必须写清 0-2秒 / 2-5秒 / 5-8秒 动作节拍。',
      '出现部位发光时明确发光部位；例如前爪发光时禁止用眼睛发光替代。',
      '连续性严格时优先继承上一镜尾帧、同一场景锚点和角色锚点，不跨场景、不换主体外观。'
    ]
  }
  return hints[scope] || []
}

function stageStrategyPrompt(scope: PromptScope) {
  const lines = stageStrategyItems(scope).map((item) => `${item.label}：${item.value}`)
  const hints = stagePromptHints(scope)
  if (!lines.length && !hints.length) {
    return ''
  }
  return [
    '【本阶段生效策略】',
    ...lines,
    ...hints.map((item) => `规则：${item}`)
  ].join('\n')
}

function imageReferencePrompt(scope: PromptScope) {
  if (scope === 'characterImage' && characterImageReferenceUrl.value.trim()) {
    return `【角色参考图 URL】${characterImageReferenceUrl.value.trim()}\n请优先参考该图的角色身份、外观轮廓、毛发/服装/色彩特征，禁止改成其他角色；参考图只用于锁定外观，最终仍必须输出单主体视频角色锚定图，禁止四方向/三视图/多视图/分栏拼图。`
  }
  if (scope === 'sceneImage' && sceneImageReferenceUrl.value.trim()) {
    return `【场景参考图 URL】${sceneImageReferenceUrl.value.trim()}\n请优先参考该图的空间结构、光线、天气、色调和前后景关系，禁止替换为无关场景；最终必须保持单镜头纯场景首帧参考图，不生成拼图、分栏、设定板或文字标签。`
  }
  return ''
}

function scopedCustomPrompt(scope: PromptScope) {
  return [stageStrategyPrompt(scope), imageReferencePrompt(scope), promptScopes.global, promptScopes[scope]]
    .map((item) => item.trim())
    .filter(Boolean)
    .join('\n\n')
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getAivideoProject(projectId.value)
    Object.assign(detail, res.data || {})
    const settingParams = parseParamsJson(res.data.setting?.paramsJson)
    Object.assign(params, {
      defaultRatio: res.data.setting?.defaultRatio || res.data.project?.defaultRatio || '9:16',
      defaultStyle: strategyValue(settingParams, 'defaultStyle', res.data.project?.defaultStyle || '写实电影感'),
      defaultResolution: res.data.setting?.defaultResolution || '720p',
      defaultShotDuration: res.data.setting?.defaultShotDuration || res.data.project?.defaultShotDuration || 5,
      imageCandidateCount: res.data.setting?.imageCandidateCount || res.data.project?.candidateImageCount || 2,
      videoCandidateCount: res.data.setting?.videoCandidateCount || 1,
      previewMode: res.data.setting?.previewMode || res.data.project?.previewMode || '1',
      generationStrategy: strategyValue(settingParams, 'generationStrategy', 'AUTO'),
      audioMode: strategyValue(settingParams, 'audioMode', 'SILENT'),
      subtitleMode: strategyValue(settingParams, 'subtitleMode', 'NONE'),
      referenceStrategy: strategyValue(settingParams, 'referenceStrategy', 'CHARACTER_SCENE'),
      actionIntensity: strategyValue(settingParams, 'actionIntensity', 'NORMAL'),
      continuityLevel: strategyValue(settingParams, 'continuityLevel', 'STRICT'),
      multiRoleStrategy: strategyValue(settingParams, 'multiRoleStrategy', 'SINGLE_FIRST')
    })
    Object.assign(promptScopes, {
      global: strategyValue(settingParams, 'globalPrompt', ''),
      polish: strategyValue(settingParams, 'polishPrompt', ''),
      script: strategyValue(settingParams, 'scriptPrompt', ''),
      asset: strategyValue(settingParams, 'assetPrompt', ''),
      characterImage: strategyValue(settingParams, 'characterImagePrompt', ''),
      sceneImage: strategyValue(settingParams, 'sceneImagePrompt', ''),
      shotVideo: strategyValue(settingParams, 'shotVideoPrompt', '')
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
      customPrompt: scopedCustomPrompt('polish')
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
    refreshCharacterImagePromptPreview()
    refreshSceneImagePromptPreview()
    refreshShotVideoPromptPreview()
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
      customPrompt: scopedCustomPrompt('script')
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
      customPrompt: scopedCustomPrompt('asset')
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
      referenceImageUrl: sceneImageReferenceUrl.value.trim(),
      customPrompt: scopedCustomPrompt('sceneImage')
    })
    sceneImagePromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    sceneImagePromptPreviewText.value = ''
  }
}

async function refreshCharacterImagePromptPreview() {
  const character = selectedCharacterForImage.value
  if (!character) {
    characterImagePromptPreviewText.value = ''
    return
  }
  try {
    const res = await previewAivideoCharacterImagePrompt({
      projectId: projectId.value,
      characterId: character.characterId,
      candidateCount: params.imageCandidateCount || 2,
      ratio: params.defaultRatio,
      resolution: params.defaultResolution,
      referenceImageUrl: characterImageReferenceUrl.value.trim(),
      customPrompt: scopedCustomPrompt('characterImage')
    })
    characterImagePromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    characterImagePromptPreviewText.value = ''
  }
}

function validateReferenceImageFile(file: File) {
  const isImage = file.type.startsWith('image/') || /\.(png|jpe?g|webp|gif|bmp)$/i.test(file.name)
  if (!isImage) {
    throw new Error('请上传图片文件，支持 png、jpg、jpeg、webp、gif、bmp')
  }
  const maxSize = 20 * 1024 * 1024
  if (file.size > maxSize) {
    throw new Error('参考图不能超过 20MB')
  }
}

async function uploadReferenceImage(options: UploadRequestOptions, scope: 'characterImage' | 'sceneImage') {
  const loadingRef = scope === 'characterImage' ? characterReferenceUploading : sceneReferenceUploading
  loadingRef.value = true
  try {
    const file = options.file as File
    validateReferenceImageFile(file)
    const res = await uploadAivideoReferenceImage(file)
    const url = res.data?.url
    if (!url) {
      throw new Error('上传成功但没有返回参考图 URL')
    }
    if (scope === 'characterImage') {
      characterImageReferenceUrl.value = url
      await refreshCharacterImagePromptPreview()
    } else {
      sceneImageReferenceUrl.value = url
      await refreshSceneImagePromptPreview()
    }
    ElMessage.success('参考图已上传，URL 已写入本次生成提示词')
    options.onSuccess?.(res.data)
  } catch (error: any) {
    const message = error?.message || '参考图上传失败'
    ElMessage.error(message)
    options.onError?.(new Error(message) as any)
  } finally {
    loadingRef.value = false
  }
}

function uploadCharacterReferenceImage(options: UploadRequestOptions) {
  return uploadReferenceImage(options, 'characterImage')
}

function uploadSceneReferenceImage(options: UploadRequestOptions) {
  return uploadReferenceImage(options, 'sceneImage')
}

async function refreshShotVideoPromptPreview() {
  const shot = selectedShotForVideo.value
  if (!shot) {
    shotVideoPromptPreviewText.value = ''
    return
  }
  const shotId = String(shot.shotId)
  try {
    const res = await previewAivideoShotVideoPrompt({
      projectId: projectId.value,
      shotId: shot.shotId,
      candidateCount: params.videoCandidateCount || 1,
      ratio: params.defaultRatio,
      resolution: params.defaultResolution,
      durationSec: shot.durationSec || params.defaultShotDuration,
      customPrompt: scopedCustomPrompt('shotVideo'),
      generationStrategy: params.generationStrategy,
      audioMode: params.audioMode,
      subtitleMode: params.subtitleMode,
      referenceStrategy: params.referenceStrategy,
      actionIntensity: params.actionIntensity,
      continuityLevel: params.continuityLevel,
      multiRoleStrategy: params.multiRoleStrategy
    })
    if (!isCurrentShotVideoTarget(shotId)) {
      return
    }
    shotVideoPromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    if (isCurrentShotVideoTarget(shotId)) {
      shotVideoPromptPreviewText.value = ''
    }
  }
}

async function openCharacterImageDrawer(character: AivideoCharacter) {
  selectedCharacterForImage.value = character
  activePromptScope.value = 'characterImage'
  characterImageReferenceUrl.value = ''
  characterImageDrawerVisible.value = true
  await refreshCharacterImagePromptPreview()
  await loadCharacterImageCandidates()
}

function revokeCharacterImagePreviewUrls() {
  Object.values(characterImagePreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  characterImagePreviewUrls.value = {}
}

async function loadCharacterImagePreviewUrl(asset: AivideoMediaAsset) {
  const key = String(asset.mediaId)
  try {
    const response = await previewAivideoMedia(asset.mediaId)
    const blob = (response as any).data as Blob
    if (!(blob instanceof Blob) || blob.size === 0) {
      return
    }
    const objectUrl = URL.createObjectURL(blob)
    const next = { ...characterImagePreviewUrls.value }
    if (next[key]) {
      URL.revokeObjectURL(next[key])
    }
    next[key] = objectUrl
    characterImagePreviewUrls.value = next
  } catch (_error) {
    // Preview errors are surfaced by the generation/list actions; keep the card placeholder.
  }
}

async function refreshCharacterImagePreviewUrls(candidates: AivideoMediaAsset[]) {
  revokeCharacterImagePreviewUrls()
  await Promise.all(candidates.map((item) => loadCharacterImagePreviewUrl(item)))
}

async function loadCharacterImageCandidates() {
  const character = selectedCharacterForImage.value
  if (!character) {
    characterImageCandidates.value = []
    revokeCharacterImagePreviewUrls()
    return
  }
  const res = await listAivideoMedia({
    projectId: projectId.value,
    assetType: 'CHARACTER_IMAGE',
    bizType: 'CHARACTER',
    bizId: character.characterId
  })
  const candidates = res.data || []
  characterImageCandidates.value = candidates
  await refreshCharacterImagePreviewUrls(candidates)
}

async function openSceneImageDrawer(scene: AivideoScene) {
  selectedSceneForImage.value = scene
  activePromptScope.value = 'sceneImage'
  sceneImageReferenceUrl.value = ''
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

async function openShotVideoDrawer(shot: AivideoShot) {
  selectedShotForVideo.value = shot
  activePromptScope.value = 'shotVideo'
  shotVideoDrawerVisible.value = true
  resetShotVideoDrawerState()
  const shotId = String(shot.shotId)
  shotVideoLoading.value = true
  try {
    await Promise.all([
      refreshShotVideoPromptPreview(),
      loadShotVideoTasks(),
      loadShotVideoCandidates()
    ])
  } finally {
    if (isCurrentShotVideoTarget(shotId)) {
      shotVideoLoading.value = false
      scheduleShotVideoRecovery()
    }
  }
}

function revokeShotVideoPreviewUrls() {
  Object.values(shotVideoPreviewUrls.value).forEach((url) => {
    if (url.startsWith('blob:')) {
      URL.revokeObjectURL(url)
    }
  })
  shotVideoPreviewUrls.value = {}
}

function resetShotVideoDrawerState() {
  clearShotVideoRecoveryTimer()
  shotVideoPromptPreviewText.value = ''
  shotVideoTasks.value = []
  shotVideoCandidates.value = []
  revokeShotVideoPreviewUrls()
}

function isCurrentShotVideoTarget(shotId?: string | number) {
  return !!selectedShotForVideo.value && String(selectedShotForVideo.value.shotId) === String(shotId)
}

function isShotVideoAssetForShot(asset: AivideoMediaAsset, shotId?: string | number) {
  return String(asset.bizId) === String(shotId)
}

function setShotVideoPreviewUrl(key: string, url: string) {
  const next = { ...shotVideoPreviewUrls.value }
  if (next[key]?.startsWith('blob:')) {
    URL.revokeObjectURL(next[key])
  }
  next[key] = url
  shotVideoPreviewUrls.value = next
}

function resolveShotVideoDirectUrl(asset: AivideoMediaAsset) {
  const fileUrl = String(asset.fileUrl || '')
  if (!fileUrl) {
    return ''
  }
  if (fileUrl.startsWith('/file/public/')) {
    return fileUrl
  }
  try {
    const parsed = new URL(fileUrl)
    if (parsed.pathname.startsWith('/file/public/')) {
      return parsed.pathname
    }
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return fileUrl
    }
  } catch (_error) {
    // Keep blob preview fallback for non-public or malformed URLs.
  }
  return ''
}

async function loadShotVideoPreviewUrl(asset: AivideoMediaAsset) {
  const key = String(asset.mediaId)
  const directUrl = resolveShotVideoDirectUrl(asset)
  if (directUrl) {
    setShotVideoPreviewUrl(key, directUrl)
    return
  }
  try {
    const response = await previewAivideoMedia(asset.mediaId)
    const blob = (response as any).data as Blob
    if (!(blob instanceof Blob) || blob.size === 0) {
      return
    }
    const objectUrl = URL.createObjectURL(blob)
    setShotVideoPreviewUrl(key, objectUrl)
  } catch (_error) {
    // Preview errors are surfaced by the generation/list actions; keep the card placeholder.
  }
}

async function refreshShotVideoPreviewUrls(candidates: AivideoMediaAsset[]) {
  revokeShotVideoPreviewUrls()
  await Promise.all(candidates.map((item) => loadShotVideoPreviewUrl(item)))
}

async function loadShotVideoCandidates() {
  const shot = selectedShotForVideo.value
  if (!shot) {
    shotVideoCandidates.value = []
    revokeShotVideoPreviewUrls()
    return
  }
  const shotId = String(shot.shotId)
  const res = await listAivideoMedia({
    projectId: projectId.value,
    assetType: 'SHOT_VIDEO',
    bizType: 'SHOT',
    bizId: shot.shotId
  })
  if (!isCurrentShotVideoTarget(shotId)) {
    return
  }
  const candidates = (res.data || []).filter((item) => isShotVideoAssetForShot(item, shotId))
  shotVideoCandidates.value = candidates
  await refreshShotVideoPreviewUrls(candidates)
}

async function loadShotVideoTasks() {
  const shot = selectedShotForVideo.value
  if (!shot) {
    shotVideoTasks.value = []
    return
  }
  const shotId = String(shot.shotId)
  const res = await listAivideoShotVideoTasks({
    projectId: projectId.value,
    shotId: shot.shotId
  })
  if (!isCurrentShotVideoTarget(shotId)) {
    return
  }
  shotVideoTasks.value = (res.data || []).filter((item) => String(item.bizId) === shotId)
  scheduleShotVideoRecovery()
}

function mergeShotVideoTaskMeta(payload: AiStreamMetaPayload) {
  const taskId = payload.taskId
  if (!taskId) {
    return
  }
  const key = String(taskId)
  const current = shotVideoTasks.value.find((item) => String(item.taskId) === key)
  const event = metaText(payload.event)
  const taskStatus = normalizeShotVideoTaskStatus(metaText(payload.status) || current?.taskStatus, event)
  const next: AivideoTask = {
    ...(current || {}),
    taskId,
    projectId: projectId.value,
    taskType: 'SHOT_VIDEO',
    bizType: 'SHOT',
    bizId: selectedShotForVideo.value?.shotId,
    providerTaskId: metaText(payload.providerTaskId) || current?.providerTaskId || '',
    taskStatus,
    progress: Number(payload.progress ?? current?.progress ?? (event === 'done' ? 100 : 15)),
    errorMessage: shouldClearTaskMessage(event, taskStatus) ? '' : String(metaText(payload.message) || current?.errorMessage || '')
  }
  shotVideoTasks.value = [
    next,
    ...shotVideoTasks.value.filter((item) => String(item.taskId) !== key)
  ]
}

function metaText(value: unknown) {
  if (value === null || value === undefined) {
    return undefined
  }
  const text = String(value).trim()
  return text || undefined
}

function isShotVideoTaskInFlight(task?: AivideoTask) {
  return inFlightTaskStatuses.has(normalizeShotVideoTaskStatus(task?.taskStatus))
}

function getTaskStatusTagType(status?: string) {
  const normalized = normalizeShotVideoTaskStatus(status)
  if (normalized === 'SUCCESS') return 'success'
  if (normalized === 'FAILED') return 'danger'
  if (normalized === 'CANCELED') return 'info'
  return 'warning'
}

function getTaskProgressStatus(status?: string): 'success' | 'exception' | undefined {
  const normalized = normalizeShotVideoTaskStatus(status)
  if (normalized === 'FAILED') return 'exception'
  if (normalized === 'SUCCESS') return 'success'
  return undefined
}

function formatTaskStatus(status?: string) {
  const normalized = normalizeShotVideoTaskStatus(status)
  const statusMap: Record<string, string> = {
    PENDING: '排队中',
    RUNNING: '生成中',
    SUCCESS: '已完成',
    FAILED: '失败',
    CANCELED: '已取消'
  }
  return statusMap[normalized] || String(status || '生成中')
}

function normalizeTaskProgress(task: AivideoTask) {
  const value = Number(task.progress || 0)
  if (Number.isNaN(value)) return 0
  return Math.max(0, Math.min(100, value))
}

function isRecoverableShotVideoTask(task: AivideoTask) {
  const status = normalizeShotVideoTaskStatus(task.taskStatus)
  if (status === 'PENDING' || status === 'RUNNING') {
    return true
  }
  if (status !== 'FAILED' || !task.providerTaskId) {
    return false
  }
  const message = String(task.errorMessage || '').toLowerCase()
  return [
    'sse',
    'broken pipe',
    'read timed out',
    'timeout',
    'temporarily unavailable',
    'connection',
    'network',
    'ioexception',
    '超时',
    '网络',
    '连接'
  ].some((keyword) => message.includes(keyword))
}

function normalizeShotVideoTaskStatus(status?: string, event?: string) {
  if (event === 'done' || event === 'candidate') {
    return 'SUCCESS'
  }
  const normalized = String(status || 'RUNNING').trim().replace(/-/g, '_').toUpperCase()
  if (['SUCCESS', 'SUCCEEDED', 'COMPLETED', 'DONE', 'FINISHED'].some((keyword) => normalized.includes(keyword))) {
    return 'SUCCESS'
  }
  if (['FAIL', 'ERROR'].some((keyword) => normalized.includes(keyword))) {
    return 'FAILED'
  }
  if (normalized.includes('CANCEL')) {
    return 'CANCELED'
  }
  if (normalized.includes('PENDING') || normalized.includes('QUEUED')) {
    return 'PENDING'
  }
  return normalized || 'RUNNING'
}

function shouldClearTaskMessage(event?: string, status?: string) {
  return event === 'done' || event === 'candidate' || normalizeShotVideoTaskStatus(status) === 'SUCCESS'
}

function shouldShowTaskMessage(task: AivideoTask) {
  return !!task.errorMessage && normalizeShotVideoTaskStatus(task.taskStatus) !== 'SUCCESS'
}

function clearShotVideoRecoveryTimer() {
  if (shotVideoRecoveryTimer) {
    clearTimeout(shotVideoRecoveryTimer)
    shotVideoRecoveryTimer = undefined
  }
}

function scheduleShotVideoRecovery(delay = SHOT_VIDEO_RECOVERY_INTERVAL) {
  clearShotVideoRecoveryTimer()
  if (!shotVideoDrawerVisible.value || shotVideoGenerating.value || shotVideoLoading.value || !hasRecoverableShotVideoTask.value) {
    return
  }
  shotVideoRecoveryTimer = setTimeout(() => {
    void recoverShotVideoCandidates({ silent: true })
  }, delay)
}

async function handleRefreshShotVideoCandidates() {
  if (shotVideoGenerating.value) {
    return
  }
  const shot = selectedShotForVideo.value
  if (!shot) {
    return
  }
  const shotId = String(shot.shotId)
  await loadShotVideoTasks()
  await loadShotVideoCandidates()
  if (!isCurrentShotVideoTarget(shotId)) {
    return
  }
  const hasRecoverableTask = shotVideoTasks.value.some(isRecoverableShotVideoTask)
  if (hasRecoverableTask) {
    await recoverShotVideoCandidates()
  } else if (!shotVideoCandidates.value.length && shotVideoTasks.value.length) {
    ElMessage.info('没有可续查的进行中视频任务，需要重新生成候选')
  } else {
    ElMessage.success('候选已刷新')
  }
}

async function recoverShotVideoCandidates(options: { silent?: boolean } = {}) {
  const shot = selectedShotForVideo.value
  if (!shot || shotVideoGenerating.value) {
    return
  }
  clearShotVideoRecoveryTimer()
  const shotId = String(shot.shotId)
  shotVideoGenerating.value = true
  let streamErrorShown = false
  const silent = !!options.silent
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_SHOT_VIDEO_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        shotId: shot.shotId,
        candidateCount: 1,
        ratio: params.defaultRatio,
        resolution: params.defaultResolution,
        durationSec: shot.durationSec || params.defaultShotDuration,
        customPrompt: scopedCustomPrompt('shotVideo'),
        generationStrategy: params.generationStrategy,
        audioMode: params.audioMode,
        subtitleMode: params.subtitleMode,
        referenceStrategy: params.referenceStrategy,
        actionIntensity: params.actionIntensity,
        continuityLevel: params.continuityLevel,
        multiRoleStrategy: params.multiRoleStrategy,
        recoverOnly: true
      },
      onMeta: (payload) => {
        if (!isCurrentShotVideoTarget(shotId)) {
          return
        }
        mergeShotVideoTaskMeta(payload)
        if (payload.event === 'candidate' && payload.asset) {
          const asset = payload.asset as AivideoMediaAsset
          if (!isShotVideoAssetForShot(asset, shotId)) {
            return
          }
          shotVideoCandidates.value = [
            asset,
            ...shotVideoCandidates.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
          ]
          void loadShotVideoPreviewUrl(asset)
        }
        if (payload.event === 'pending') {
          if (!silent) {
            ElMessage.info(String(payload.message || '视频任务仍在生成中，稍后刷新候选'))
          }
        }
      },
      onError: (message) => {
        streamErrorShown = true
        if (!silent) {
          ElMessage.error(message || '续查分镜视频任务失败')
        }
      }
    })
    await loadShotVideoTasks()
    await loadShotVideoCandidates()
    await loadDetail()
  } catch (error: any) {
    if (streamErrorShown) {
      await loadShotVideoTasks()
      return
    }
    if (!silent) {
      ElMessage.error(error?.message || '续查分镜视频任务失败')
    }
    await loadShotVideoTasks()
  } finally {
    shotVideoGenerating.value = false
    scheduleShotVideoRecovery()
  }
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

async function handleSaveProjectStrategies() {
  const project = detail.project
  if (!project || savingStrategies.value) {
    return
  }
  savingStrategies.value = true
  try {
    await updateAivideoProject({
      projectId: projectId.value,
      projectName: project.projectName,
      topicType: project.topicType,
      targetPlatform: project.targetPlatform,
      defaultRatio: params.defaultRatio,
      defaultStyle: params.defaultStyle,
      generationStrategy: params.generationStrategy,
      audioMode: params.audioMode,
      subtitleMode: params.subtitleMode,
      referenceStrategy: params.referenceStrategy,
      actionIntensity: params.actionIntensity,
      continuityLevel: params.continuityLevel,
      multiRoleStrategy: params.multiRoleStrategy,
      globalPrompt: promptScopes.global,
      polishPrompt: promptScopes.polish,
      scriptPrompt: promptScopes.script,
      assetPrompt: promptScopes.asset,
      characterImagePrompt: promptScopes.characterImage,
      sceneImagePrompt: promptScopes.sceneImage,
      shotVideoPrompt: promptScopes.shotVideo,
      defaultShotDuration: params.defaultShotDuration,
      candidateImageCount: params.imageCandidateCount,
      previewMode: params.previewMode,
      budgetLimit: project.budgetLimit,
      summary: project.summary
    })
    ElMessage.success('策略已保存，后续生成会按当前策略发送')
    await loadDetail()
  } finally {
    savingStrategies.value = false
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
        customPrompt: scopedCustomPrompt('polish')
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

function handleCancelConfirmPolish(versionId: string | number) {
  withSubmit(
    () => cancelConfirmAivideoPolish({ projectId: projectId.value, versionId }).then(() => undefined),
    '润色稿已取消确认'
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
        customPrompt: scopedCustomPrompt('script')
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
    () => confirmAivideoScript({ projectId: projectId.value, versionId }).then(() => {
      assetContextCollapsed.value = true
    }),
    '短剧剧本已确认'
  )
}

function handleCancelConfirmScript(versionId: string | number) {
  withSubmit(
    () => cancelConfirmAivideoScript({ projectId: projectId.value, versionId }).then(() => {
      assetContextCollapsed.value = false
    }),
    '短剧剧本已取消确认'
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
        customPrompt: scopedCustomPrompt('asset')
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
    await loadDetail()
    if (hasAssets.value) {
      ElMessage.success('资产已提取并结构化入库')
    } else {
      assetContextCollapsed.value = false
      ElMessage.warning('资产原始输出已返回，但没有形成可确认的结构化资产，请重新提取')
    }
  } catch (error: any) {
    assetContextCollapsed.value = false
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
    assetContextCollapsed.value = true
    await loadDetail()
  } finally {
    confirmingAllAssets.value = false
  }
}

async function handleCancelConfirmAllAssets() {
  if (confirmingAllAssets.value) {
    return
  }
  confirmingAllAssets.value = true
  try {
    await cancelConfirmAivideoAsset({ projectId: projectId.value, targetType: 'ALL' })
    ElMessage.success('资产已取消确认')
    assetContextCollapsed.value = false
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
    assetContextCollapsed.value = true
    await loadDetail()
  } finally {
    setAssetConfirming(key, false)
  }
}

async function handleCancelConfirmAsset(targetType: string, targetId: string | number) {
  const key = assetConfirmKey(targetType, targetId)
  if (confirmingAssetKeys.value.has(key)) {
    return
  }
  setAssetConfirming(key, true)
  try {
    await cancelConfirmAivideoAsset({ projectId: projectId.value, targetType, targetId })
    ElMessage.success('资产已取消确认')
    assetContextCollapsed.value = false
    await loadDetail()
  } finally {
    setAssetConfirming(key, false)
  }
}

async function handleGenerateCharacterImages() {
  const character = selectedCharacterForImage.value
  if (!character || characterImageGenerating.value) {
    return
  }
  characterImageGenerating.value = true
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_CHARACTER_IMAGE_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        characterId: character.characterId,
        candidateCount: params.imageCandidateCount || 2,
        ratio: params.defaultRatio,
        resolution: params.defaultResolution,
        referenceImageUrl: characterImageReferenceUrl.value.trim(),
        customPrompt: scopedCustomPrompt('characterImage')
      },
      onMeta: (payload) => {
        if (payload.event === 'candidate' && payload.asset) {
          const asset = payload.asset as AivideoMediaAsset
          characterImageCandidates.value = [
            asset,
            ...characterImageCandidates.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
          ]
          void loadCharacterImagePreviewUrl(asset)
        }
      },
      onError: (message) => {
        ElMessage.error(message || '角色图生成失败')
      }
    })
    ElMessage.success('角色图候选已生成')
    await loadCharacterImageCandidates()
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '角色图生成失败')
  } finally {
    characterImageGenerating.value = false
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
        referenceImageUrl: sceneImageReferenceUrl.value.trim(),
        customPrompt: scopedCustomPrompt('sceneImage')
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

async function handleGenerateShotVideos() {
  const shot = selectedShotForVideo.value
  if (!shot) {
    return
  }
  if (previousShotVideoRequired.value) {
    ElMessage.warning(previousShotGateMessage.value)
    return
  }
  if (shotVideoActionLocked.value) {
    if (hasRunningShotVideoTask.value) {
      ElMessage.info('该分镜已有视频生成任务执行中，请稍后刷新候选视频')
    }
    return
  }
  const shotId = String(shot.shotId)
  shotVideoGenerating.value = true
  let receivedCandidate = false
  try {
    await requestAiStream({
      baseUrl: import.meta.env.VITE_APP_BASE_API || '',
      path: AIVIDEO_SHOT_VIDEO_STREAM_PATH,
      token: userStore.token,
      tenantId: userStore.tenantId,
      body: {
        projectId: projectId.value,
        shotId: shot.shotId,
        candidateCount: params.videoCandidateCount || 1,
        ratio: params.defaultRatio,
        resolution: params.defaultResolution,
        durationSec: shot.durationSec || params.defaultShotDuration,
        customPrompt: scopedCustomPrompt('shotVideo'),
        generationStrategy: params.generationStrategy,
        audioMode: params.audioMode,
        subtitleMode: params.subtitleMode,
        referenceStrategy: params.referenceStrategy,
        actionIntensity: params.actionIntensity,
        continuityLevel: params.continuityLevel,
        multiRoleStrategy: params.multiRoleStrategy
      },
      onMeta: (payload) => {
        if (!isCurrentShotVideoTarget(shotId)) {
          return
        }
        mergeShotVideoTaskMeta(payload)
        if (payload.event === 'candidate' && payload.asset) {
          const asset = payload.asset as AivideoMediaAsset
          if (!isShotVideoAssetForShot(asset, shotId)) {
            return
          }
          receivedCandidate = true
          shotVideoCandidates.value = [
            asset,
            ...shotVideoCandidates.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
          ]
          void loadShotVideoPreviewUrl(asset)
        }
        if (payload.event === 'pending') {
          ElMessage.info(String(payload.message || '视频任务仍在生成中，稍后刷新候选'))
        }
      },
      onError: (message) => {
        ElMessage.error(message || '分镜视频生成失败')
      }
    })
    if (receivedCandidate) {
      ElMessage.success('分镜视频候选已生成')
    } else {
      ElMessage.info('视频任务已提交，稍后点击刷新候选续查结果')
    }
    await loadShotVideoTasks()
    await loadShotVideoCandidates()
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '分镜视频生成失败')
    await loadShotVideoTasks()
  } finally {
    shotVideoGenerating.value = false
    scheduleShotVideoRecovery()
  }
}

async function handleSelectCharacterImage(item: AivideoMediaAsset) {
  const key = String(item.mediaId)
  if (characterImageSelectingIds.value.has(key)) {
    return
  }
  const next = new Set(characterImageSelectingIds.value)
  next.add(key)
  characterImageSelectingIds.value = next
  try {
    await selectAivideoMedia({
      projectId: projectId.value,
      mediaId: item.mediaId,
      bizType: 'CHARACTER',
      bizId: item.bizId
    })
    ElMessage.success('角色图已选定')
    await loadCharacterImageCandidates()
    await loadDetail()
  } finally {
    const done = new Set(characterImageSelectingIds.value)
    done.delete(key)
    characterImageSelectingIds.value = done
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

async function handleSelectShotVideo(item: AivideoMediaAsset) {
  const key = String(item.mediaId)
  if (shotVideoSelectingIds.value.has(key)) {
    return
  }
  const next = new Set(shotVideoSelectingIds.value)
  next.add(key)
  shotVideoSelectingIds.value = next
  try {
    await selectAivideoMedia({
      projectId: projectId.value,
      mediaId: item.mediaId,
      bizType: 'SHOT',
      bizId: item.bizId
    })
    ElMessage.success('分镜视频已选定')
    await loadShotVideoCandidates()
    await loadDetail()
  } finally {
    const done = new Set(shotVideoSelectingIds.value)
    done.delete(key)
    shotVideoSelectingIds.value = done
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

function promptScopeByTab(tab: WorkbenchTab): PromptScope {
  const map: Record<WorkbenchTab, PromptScope> = {
    document: 'global',
    polish: 'polish',
    script: 'script',
    assets: 'asset',
    task: 'shotVideo'
  }
  return map[tab]
}

onMounted(() => {
  loadDetail()
})

watch(promptScopes, () => {
  schedulePolishPromptPreview()
}, { deep: true })

watch(() => [
  params.defaultStyle,
  params.generationStrategy,
  params.audioMode,
  params.subtitleMode,
  params.referenceStrategy,
  params.actionIntensity,
  params.continuityLevel,
  params.multiRoleStrategy
], () => {
  schedulePolishPromptPreview()
})

watch(() => [
  characterImageReferenceUrl.value,
  sceneImageReferenceUrl.value
], () => {
  schedulePolishPromptPreview()
})

watch(activeTab, (tab) => {
  activePromptScope.value = promptScopeByTab(tab)
})

watch(shotVideoDrawerVisible, (visible) => {
  if (!visible) {
    clearShotVideoRecoveryTimer()
  } else {
    scheduleShotVideoRecovery()
  }
})

onBeforeUnmount(() => {
  if (promptPreviewTimer) {
    clearTimeout(promptPreviewTimer)
  }
  clearShotVideoRecoveryTimer()
  revokeCharacterImagePreviewUrls()
  revokeSceneImagePreviewUrls()
  revokeShotVideoPreviewUrls()
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

.action-button-wrap {
  display: inline-flex;
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

.asset-context-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;

  > div {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.asset-status-alert {
  margin-bottom: 12px;
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

.reference-image-form {
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.reference-image-control {
  display: flex;
  gap: 10px;
  align-items: center;

  .el-input {
    flex: 1;
  }
}

.reference-image-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 720px) {
  .reference-image-control {
    align-items: stretch;
    flex-direction: column;
  }
}

.shot-video-gate-alert {
  margin-bottom: 12px;
}

.shot-tail-frame-tag {
  margin-left: 6px;
}

.shot-video-task-list {
  display: grid;
  gap: 10px;
}

.shot-video-task-card {
  display: grid;
  gap: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fbff;
}

.shot-video-task-head,
.shot-video-task-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.shot-video-task-meta,
.shot-video-task-message {
  color: #6b7280;
  font-size: 12px;
}

.shot-video-task-message {
  margin: 0;
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

  video {
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

.content-action-bar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px;
  margin: -12px -12px 12px;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 8px 8px 0 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(6px);
}

.content-action-buttons {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
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

.strategy-grid,
.prompt-scope-editor {
  display: grid;
  width: 100%;
  gap: 8px;
}

.stage-strategy-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.stage-strategy-note {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}

.shot-video-strategy {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
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
