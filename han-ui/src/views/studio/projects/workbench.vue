<template>
  <div class="workbench-page">
    <div class="workbench-header">
      <div class="title-block">
        <el-button :icon="ArrowLeft" text @click="router.push('/studio/projects')">项目列表</el-button>
        <h2>{{ detail.project?.projectName || '短剧项目' }}</h2>
      </div>
      <div class="header-actions">
        <el-button
          v-if="hasAnyConfirmedItem"
          type="warning"
          plain
          :loading="submitting"
          @click="handleCancelAllConfirmations"
        >
          取消全部确认
        </el-button>
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
            <div class="section-actions">
              <el-button
                v-if="latestDocument?.confirmed === '1'"
                type="warning"
                plain
                :loading="submitting"
                @click="handleCancelConfirmDocument"
              >
                取消确认原文
              </el-button>
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
          </div>
          <div v-if="showDocumentEditor" class="document-editor">
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
              <span class="editor-tip">
                {{ sourceDraftDocumentId ? '正在修改当前待确认原文，保存后后续生成会使用这版内容。' : '保存后会生成一条待确认原文，用于后续润色和剧本生成。' }}
              </span>
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
          <article v-for="doc in visibleDocuments" :key="doc.documentId" class="text-block">
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
            <h3>角色 / 场景 / 道具 / 分镜</h3>
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
                角色 {{ assetCounts.characters }} / 场景 {{ assetCounts.scenes }} / 道具 {{ assetCounts.props }} / 分镜 {{ assetCounts.shots }}
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
                <el-table-column label="参考内容" min-width="220">
                  <template #default="{ row }">
                    <div class="reference-summary">
                      {{ characterReferenceSummary(row) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="声线资产" min-width="190">
                  <template #default="{ row }">
                    <div class="voice-summary">
                      <strong>{{ characterVoiceSummary(row) }}</strong>
                      <small>{{ characterVoiceDetail(row) }}</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="角色图" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.lockedMediaId" type="success">已选 #{{ row.lockedMediaId }}</el-tag>
                    <el-tag v-else type="info">未选</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="300">
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
                    <el-button size="small" type="success" plain @click="openCharacterVoiceDialog(row)">
                      声线
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
                <el-table-column label="参考内容" min-width="260">
                  <template #default="{ row }">
                    <div class="reference-summary">
                      {{ sceneReferenceSummary(row) }}
                    </div>
                  </template>
                </el-table-column>
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
            <el-tab-pane label="道具" name="props">
              <el-table :data="props" border>
                <el-table-column prop="propName" label="道具名称" min-width="140" />
                <el-table-column prop="propType" label="类型" min-width="120" />
                <el-table-column prop="visualDesc" label="视觉描述" min-width="240" show-overflow-tooltip />
                <el-table-column label="颜色/材质/形状" min-width="220">
                  <template #default="{ row }">
                    <div class="reference-summary">
                      {{ propAppearanceSummary(row) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="连续性" min-width="260" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ propContinuitySummary(row) }}
                  </template>
                </el-table-column>
                <el-table-column label="道具图" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.lockedMediaId" type="success">已选 #{{ row.lockedMediaId }}</el-tag>
                    <el-tag v-else type="info">未选</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="confirmStatus" label="状态" width="110" />
                <el-table-column label="操作" width="320">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.confirmStatus !== 'APPROVED'"
                      size="small"
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('PROP', row.propId)"
                      @click="handleConfirmAsset('PROP', row.propId)"
                    >
                      确认
                    </el-button>
                    <el-button
                      v-else
                      size="small"
                      type="warning"
                      plain
                      :disabled="confirmingAllAssets"
                      :loading="isAssetConfirming('PROP', row.propId)"
                      @click="handleCancelConfirmAsset('PROP', row.propId)"
                    >
                      取消确认
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :loading="mediaRegisterUploading && isMediaRegistering('PROP_IMAGE', row.propId)"
                      @click="triggerPropImageUpload(row)"
                    >
                      上传道具图
                    </el-button>
                    <el-button size="small" type="primary" link @click="promptPropImageUrl(row)">
                      图URL
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
                <el-table-column label="衔接/转场" min-width="220">
                  <template #default="{ row }">
                    <div class="shot-transition-cell">
                      <el-tag :type="shotTransitionTagType(row)" effect="plain">
                        {{ shotTransitionLabel(row) }}
                      </el-tag>
                      <span class="shot-transition-desc">{{ shotTransitionDesc(row) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="画内人物" min-width="220">
                  <template #default="{ row }">
                    <div class="shot-screen-cell">
                      <template v-if="shotScreenCharacterRule(row).onscreenCharacters.length">
                        <el-tag
                          v-for="character in shotScreenCharacterRule(row).onscreenCharacters"
                          :key="character.characterId"
                          type="success"
                          effect="plain"
                        >
                          {{ character.characterName || `角色 ${character.characterId}` }}
                        </el-tag>
                      </template>
                      <el-tag v-else type="info" effect="plain">未规定</el-tag>
                      <el-tag
                        v-if="shotScreenCharacterRule(row).missingCharacters.length"
                        type="danger"
                        effect="dark"
                      >
                        疑似缺失 {{ shotScreenCharacterRule(row).missingCharacters.map((item) => item.characterName || `角色 ${item.characterId}`).join('、') }}
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="actionDesc" label="动作" min-width="240" show-overflow-tooltip />
                <el-table-column label="视频资产" width="210">
                  <template #default="{ row }">
                    <div class="shot-media-cell">
                      <el-tag v-if="shotVideoMediaId(row)" type="success">视频 #{{ shotVideoMediaId(row) }}</el-tag>
                      <el-tag v-else type="info">视频未选</el-tag>
                      <el-tag v-if="shotVideoTaskId(row)" type="info" effect="plain">
                        任务 #{{ shotVideoTaskId(row) }}
                      </el-tag>
                      <el-tag v-if="shotTailFrameMediaId(row)" type="success" effect="plain">
                        尾帧 #{{ shotTailFrameMediaId(row) }}
                      </el-tag>
                      <el-tag v-if="shotAudioMediaId(row)" type="warning" effect="plain">
                        音频 #{{ shotAudioMediaId(row) }}
                      </el-tag>
                      <el-tag v-if="shotTtsAudioMediaId(row)" type="warning" effect="dark">
                        配音 #{{ shotTtsAudioMediaId(row) }}
                      </el-tag>
                    </div>
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
            <el-tab-pane label="剪辑" name="edit">
              <div class="project-edit-panel" v-loading="projectEditLoading">
                <div class="project-edit-toolbar">
                  <div>
                    <h4>整片剪辑合成预检</h4>
                    <p>按已确认分镜顺序，把所有“已选分镜视频”合成为一个完整成片；如果已准备后期配音素材，会按整片时间线统一混入。</p>
                  </div>
                  <div class="project-edit-actions">
                    <el-button
                      type="success"
                      plain
                      :loading="mediaRegisterUploading && isMediaRegistering('PROJECT_BGM_AUDIO', projectId)"
                      @click="triggerProjectBgmUpload"
                    >
                      上传BGM
                    </el-button>
                    <el-button type="success" plain @click="promptProjectBgmUrl">BGM URL</el-button>
                    <el-button :icon="Refresh" @click="refreshProjectEditPanel">刷新预检</el-button>
                    <el-button
                      type="primary"
                      :icon="Film"
                      :loading="projectEditGenerating"
                      :disabled="!projectEditReady || hasRunningProjectEditTask"
                      @click="handleGenerateProjectEdit"
                    >
                      生成整片成片
                    </el-button>
                  </div>
                </div>

                <el-alert
                  v-if="projectEditErrors.length"
                  class="project-edit-alert"
                  title="整片剪辑预检未通过"
                  type="warning"
                  show-icon
                  :closable="false"
                >
                  <ul>
                    <li v-for="item in projectEditErrors" :key="item">{{ item }}</li>
                  </ul>
                </el-alert>
                <el-alert
                  v-else-if="projectEditReady"
                  class="project-edit-alert"
                  title="整片剪辑预检通过"
                  type="success"
                  description="所有已确认分镜都有已选视频，可以提交整片剪辑合成任务。"
                  show-icon
                  :closable="false"
                />
                <el-alert
                  v-if="projectEditWarnings.length"
                  class="project-edit-alert"
                  title="剪辑衔接提示"
                  type="info"
                  show-icon
                  :closable="false"
                >
                  <ul>
                    <li v-for="item in projectEditWarnings" :key="item">{{ item }}</li>
                  </ul>
                </el-alert>

                <el-descriptions class="project-edit-summary" :column="4" border>
                  <el-descriptions-item label="可剪辑片段">{{ projectEditPreflight?.clipCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="缺少视频镜头">{{ projectEditPreflight?.missingShotCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="预计总时长">{{ projectEditPreflight?.totalDurationSec || 0 }} 秒</el-descriptions-item>
                  <el-descriptions-item label="音频轨">
                    {{ projectEditPreflight?.audioTrackCount || 0 }}
                    <el-tag v-if="projectEditPreflight?.bgmAudioMediaId" size="small" type="success" effect="plain">
                      BGM #{{ projectEditPreflight.bgmAudioMediaId }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>

                <el-table class="project-edit-table" :data="projectEditClips" border empty-text="暂无可剪辑片段">
                  <el-table-column label="镜头" width="90">
                    <template #default="{ row }">
                      {{ row.shotNo || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="时长" width="90">
                    <template #default="{ row }">
                      {{ row.durationSec || 0 }} 秒
                    </template>
                  </el-table-column>
                  <el-table-column label="衔接/转场" min-width="180">
                    <template #default="{ row }">
                      <div class="shot-transition-cell">
                        <el-tag effect="plain">{{ row.transitionBeforeType || '-' }}</el-tag>
                        <span class="shot-transition-desc">{{ row.transitionBeforeDesc || row.transitionEffect || '-' }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="动作" min-width="260" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.actionDesc || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="声音计划" min-width="260" show-overflow-tooltip>
                    <template #default="{ row }">
                      <div class="shot-sound-cue-cell">
                        <p><strong>BGM：</strong>{{ row.bgmCue || '未规划' }}</p>
                        <p><strong>音效：</strong>{{ row.sfxCues || '未规划' }}</p>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="视频/声音资产" width="210">
                    <template #default="{ row }">
                      <el-tag type="success">#{{ row.videoMediaId }}</el-tag>
                      <el-tag v-if="row.sfxAudioMediaId" class="asset-mini-tag" type="warning" effect="plain">
                        SFX #{{ row.sfxAudioMediaId }}
                      </el-tag>
                      <div class="media-mini-actions">
                        <el-button
                          size="small"
                          type="warning"
                          link
                          :loading="mediaRegisterUploading && isMediaRegistering('SHOT_SFX_AUDIO', row.shotId)"
                          @click="triggerShotSfxUpload(row)"
                        >
                          上传音效
                        </el-button>
                        <el-button size="small" type="warning" link @click="promptShotSfxUrl(row)">
                          SFX URL
                        </el-button>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="时间线" width="180">
                    <template #default="{ row }">
                      {{ formatTimeline(row.timelineStartMs) }} - {{ formatTimeline(row.timelineEndMs) }}
                    </template>
                  </el-table-column>
                </el-table>

                <div class="project-edit-subsection">
                  <h4>剪辑任务</h4>
                  <div v-if="projectEditTasks.length" class="project-edit-task-list">
                    <article v-for="task in projectEditTasks" :key="task.taskId" class="project-edit-task-card">
                      <div class="task-card-head">
                        <strong>任务 {{ task.taskId }}</strong>
                        <el-tag :type="getTaskStatusTagType(task.taskStatus)">
                          {{ formatTaskStatus(task.taskStatus) }}
                        </el-tag>
                      </div>
                      <el-progress
                        :percentage="normalizeTaskProgress(task)"
                        :status="getTaskProgressStatus(task.taskStatus)"
                      />
                      <p v-if="task.providerTaskId" class="muted-line">火山任务 {{ task.providerTaskId }}</p>
                      <p v-if="shouldShowTaskMessage(task)" class="task-error">{{ task.errorMessage }}</p>
                    </article>
                  </div>
                  <el-empty v-else description="暂无剪辑任务" />
                </div>

                <div class="project-edit-subsection">
                  <h4>成片资产</h4>
                  <div v-if="projectEditVideos.length" class="project-edit-video-grid">
                    <article v-for="asset in projectEditVideos" :key="asset.mediaId" class="project-edit-video-card">
                      <video
                        v-if="projectEditPlayableUrl(asset)"
                        :src="projectEditPlayableUrl(asset)"
                        controls
                        playsinline
                      />
                      <div v-else class="project-edit-vod-placeholder">
                        <strong>VOD 成片</strong>
                        <span>{{ projectEditVodId(asset) || `媒体 #${asset.mediaId}` }}</span>
                      </div>
                      <div class="project-edit-video-meta">
                        <strong>成片 #{{ asset.mediaId }}</strong>
                        <el-tag type="success" effect="plain">{{ asset.assetStatus || 'SELECTED' }}</el-tag>
                      </div>
                    </article>
                  </div>
                  <el-empty v-else description="暂无成片资产" />
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="后期语音" name="tts">
              <el-alert
                class="project-edit-alert"
                title="后期语音在整片剪辑之后处理"
                type="info"
                description="这里用于为分镜准备配音素材，不会重新生成分镜视频；最终声音会在整片剪辑/混音阶段按时间线统一合入。"
                show-icon
                :closable="false"
              />
              <el-alert
                v-if="!projectEditVideos.length"
                class="project-edit-alert"
                title="建议先完成整片剪辑成片"
                type="warning"
                description="没有成片资产时，当前语音只作为配音素材准备；请先在“剪辑”里生成整片成片，再补配音/混音。"
                show-icon
                :closable="false"
              />
              <el-table :data="shots" border empty-text="暂无可配音分镜">
                <el-table-column prop="shotNo" label="镜头" width="90" />
                <el-table-column prop="durationSec" label="秒数" width="90" />
                <el-table-column label="说话时间" width="170">
                  <template #default="{ row }">
                    <div class="tts-time-cell">
                      <strong>{{ formatShotTtsRange(row) }}</strong>
                      <small>{{ defaultShotTtsSpeaker(row) || '未识别说话人' }}</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="配音文本" min-width="320">
                  <template #default="{ row }">
                    <div class="tts-asset-text">
                      {{ defaultShotTtsText(row) || '暂无说出口的对白/旁白' }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="配音素材" width="150">
                  <template #default="{ row }">
                    <el-tag v-if="shotTtsAudioMediaId(row)" type="warning" effect="dark">
                      配音 #{{ shotTtsAudioMediaId(row) }}
                    </el-tag>
                    <el-tag v-else type="info" effect="plain">未配音</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="190">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" plain @click="openShotVideoDrawer(row)">
                      生成/选择配音素材
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
        <div class="params-panel-head">
          <div>
            <h3>参数</h3>
            <small>项目级默认配置，保存后影响后续生成</small>
          </div>
          <div class="params-actions">
            <template v-if="paramsEditing">
              <el-button size="small" :disabled="savingStrategies" @click="handleCancelProjectParams">
                取消
              </el-button>
              <el-button
                size="small"
                type="primary"
                :loading="savingStrategies"
                data-testid="save-project-params"
                @click="handleSaveProjectStrategies"
              >
                保存
              </el-button>
            </template>
            <el-button
              v-else
              size="small"
              type="primary"
              plain
              data-testid="edit-project-params"
              @click="handleEditProjectParams"
            >
              编辑参数
            </el-button>
          </div>
        </div>
        <el-form label-position="top">
          <el-form-item label="画幅">
            <el-select v-model="params.defaultRatio" :disabled="!paramsEditing || savingStrategies">
              <el-option v-for="item in ratioOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="清晰度">
            <el-input v-model="params.defaultResolution" disabled />
          </el-form-item>
          <el-form-item label="镜头秒数">
            <el-input-number
              v-model="params.defaultShotDuration"
              :min="5"
              :max="8"
              :step="1"
              :disabled="!paramsEditing || savingStrategies"
            />
          </el-form-item>
          <el-form-item label="图片候选数">
            <el-input-number
              v-model="params.imageCandidateCount"
              :min="1"
              :max="4"
              :step="1"
              :disabled="!paramsEditing || savingStrategies"
              data-testid="project-image-candidate-count"
            />
          </el-form-item>
          <el-form-item label="视频候选数">
            <el-input-number
              v-model="params.videoCandidateCount"
              :min="1"
              :max="3"
              :step="1"
              :disabled="!paramsEditing || savingStrategies"
            />
          </el-form-item>
          <el-form-item label="当前策略">
            <div class="strategy-grid">
              <el-select
                v-model="params.defaultStyle"
                filterable
                allow-create
                default-first-option
                placeholder="视觉风格"
                :disabled="!paramsEditing || savingStrategies"
              >
                <el-option v-for="item in visualStyleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.characterDesignType" placeholder="角色造型" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in characterDesignTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.generationStrategy" placeholder="生成策略" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in generationStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.audioMode" placeholder="声音模式" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in audioModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.subtitleMode" placeholder="字幕模式" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in subtitleModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.referenceStrategy" placeholder="参考素材" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in referenceStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.actionIntensity" placeholder="动作强度" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in actionIntensityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.continuityLevel" placeholder="连续性" :disabled="!paramsEditing || savingStrategies">
                <el-option v-for="item in continuityLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="params.multiRoleStrategy" placeholder="多角色" :disabled="!paramsEditing || savingStrategies">
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
                :disabled="!paramsEditing || savingStrategies"
              />
            </div>
          </el-form-item>
        </el-form>
      </aside>
    </div>

    <input
      ref="mediaRegisterFileInputRef"
      class="hidden-file-input"
      type="file"
      :accept="mediaRegisterAccept"
      @change="handleRegisterMediaUpload"
    />

    <el-drawer v-model="sceneImageDrawerVisible" size="760px" :title="sceneImageDrawerTitle">
      <div v-if="selectedSceneForImage" class="scene-image-drawer">
        <details class="prompt-preview" open>
          <summary>查看本次场景图提示词</summary>
          <pre>{{ sceneImagePromptPreviewText || '暂无可预览提示词' }}</pre>
        </details>

        <div class="image-extra-prompt-form">
          <label>本次场景图追加提示词</label>
          <el-input
            v-model="promptScopes.sceneImage"
            type="textarea"
            :rows="3"
            maxlength="1200"
            show-word-limit
            placeholder="例如：保持路边皮球位置不变、镜头低机位、不要出现人物"
            :disabled="sceneImageGenerating"
          />
        </div>

        <ReferenceImagePicker
          v-model="sceneReferenceMediaIds"
          scope="scene"
          label="从已确认场景图选择参考（可多选，按顺序作为图片1、图片2传给图片模型）"
          placeholder="选择已确认场景图；例如暴雨夜小区街道参考傍晚静谧小区街道"
          thumbnail-alt="场景参考缩略图"
          empty-description="未选择参考场景图；可直接生成，或先确认一张场景图后再引用"
          :options="sceneReferenceOptions"
          :preview-urls="referencePreviewUrls"
          :disabled="sceneImageGenerating"
          @change="handleSceneReferenceChange"
        />

        <div class="external-reference-form">
          <label>自定义场景参考图（可粘贴 URL 或上传，本次生成会按顺序追加到参考图）</label>
          <div class="reference-image-control">
            <el-input
              v-model="sceneReferenceImageUrlInput"
              placeholder="粘贴场景参考图 URL，回车或点添加"
              :disabled="sceneImageGenerating || sceneReferenceUploading"
              @keyup.enter="handleAddSceneReferenceUrl"
            />
            <el-button :disabled="sceneImageGenerating || sceneReferenceUploading" @click="handleAddSceneReferenceUrl">
              添加 URL
            </el-button>
            <el-button
              :icon="Upload"
              :loading="sceneReferenceUploading"
              :disabled="sceneImageGenerating"
              @click="triggerSceneReferenceUpload"
            >
              上传参考图
            </el-button>
            <input
              ref="sceneReferenceFileInputRef"
              class="hidden-file-input"
              type="file"
              accept="image/*"
              @change="handleUploadSceneReferenceImage"
            />
          </div>
          <div v-if="sceneReferenceImageUrls.length" class="external-reference-list">
            <el-tag
              v-for="(url, index) in sceneReferenceImageUrls"
              :key="url"
              closable
              type="info"
              @close="removeSceneReferenceUrl(index)"
            >
              外部图{{ index + 1 }}：{{ compactUrl(url) }}
            </el-tag>
          </div>
        </div>

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
            v-for="(item, index) in sceneImageCandidates"
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
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">候选 {{ index + 1 }}</el-tag>
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

    <el-drawer v-model="characterImageDrawerVisible" size="760px" :title="characterImageDrawerTitle">
      <div v-if="selectedCharacterForImage" class="scene-image-drawer">
        <details class="prompt-preview" open>
          <summary>查看本次角色图提示词</summary>
          <pre>{{ characterImagePromptPreviewText || '暂无可预览提示词' }}</pre>
        </details>

        <div class="image-extra-prompt-form">
          <label>本次角色图追加提示词</label>
          <el-input
            v-model="promptScopes.characterImage"
            type="textarea"
            :rows="3"
            maxlength="1200"
            show-word-limit
            placeholder="例如：Q版但必须全身入镜、不要头像贴纸、保持四肢和尾巴完整"
            :disabled="characterImageGenerating"
          />
        </div>

        <ReferenceImagePicker
          v-model="characterReferenceMediaIds"
          scope="character"
          label="从已确认角色图选择参考（可多选，按顺序作为图片1、图片2传给图片模型）"
          placeholder="选择已确认角色图；用于锁定物种、脸型、体型、毛色、服装或标志性细节"
          thumbnail-alt="角色参考缩略图"
          empty-description="未选择参考角色图；可直接生成，或先确认一张角色图后再引用"
          :options="characterReferenceOptions"
          :preview-urls="referencePreviewUrls"
          :disabled="characterImageGenerating"
          @change="handleCharacterReferenceChange"
        />

        <div class="external-reference-form">
          <label>自定义角色参考图（可粘贴 URL 或上传，本次生成会按顺序追加到参考图）</label>
          <div class="reference-image-control">
            <el-input
              v-model="characterReferenceImageUrlInput"
              placeholder="粘贴角色参考图 URL，回车或点添加"
              :disabled="characterImageGenerating || characterReferenceUploading"
              @keyup.enter="handleAddCharacterReferenceUrl"
            />
            <el-button :disabled="characterImageGenerating || characterReferenceUploading" @click="handleAddCharacterReferenceUrl">
              添加 URL
            </el-button>
            <el-button
              :icon="Upload"
              :loading="characterReferenceUploading"
              :disabled="characterImageGenerating"
              @click="triggerCharacterReferenceUpload"
            >
              上传参考图
            </el-button>
            <input
              ref="characterReferenceFileInputRef"
              class="hidden-file-input"
              type="file"
              accept="image/*"
              @change="handleUploadCharacterReferenceImage"
            />
          </div>
          <div v-if="characterReferenceImageUrls.length" class="external-reference-list">
            <el-tag
              v-for="(url, index) in characterReferenceImageUrls"
              :key="url"
              closable
              type="info"
              @close="removeCharacterReferenceUrl(index)"
            >
              外部图{{ index + 1 }}：{{ compactUrl(url) }}
            </el-tag>
          </div>
        </div>

        <div class="scene-image-actions">
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="characterImageGenerating"
            :disabled="characterImageGenerating"
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
            v-for="(item, index) in characterImageCandidates"
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
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">候选 {{ index + 1 }}</el-tag>
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

    <el-dialog v-model="characterVoiceDialogVisible" width="680px" :title="characterVoiceDialogTitle">
      <div v-if="selectedCharacterForVoice" class="character-voice-dialog">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="声线会保存到角色资产里，后期语音生成会优先继承该角色的音色、语速、音量和音高。"
        />
        <el-form class="character-voice-form" label-width="110px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="声音模式">
                <el-select v-model="characterVoiceForm.voiceMode" placeholder="选择声音模式">
                  <el-option
                    v-for="item in characterVoiceModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="音色ID">
                <el-input v-model="characterVoiceForm.voiceType" placeholder="例如 BV001_24k_streaming 或自定义音色ID" clearable />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="声线名称">
            <el-input v-model="characterVoiceForm.voiceName" placeholder="例如 喵小萌Q版少女声" clearable />
          </el-form-item>
          <el-form-item label="声线描述">
            <el-input
              v-model="characterVoiceForm.voiceDesc"
              type="textarea"
              :rows="3"
              maxlength="512"
              show-word-limit
              placeholder="描述年龄、性别、语速、语气、情绪边界，例如：清亮软萌、语速略快、吐字清楚。"
            />
          </el-form-item>
          <el-form-item label="参考音频ID">
            <div class="media-register-field">
              <el-input
                v-model="characterVoiceForm.voiceReferenceMediaId"
                placeholder="可填已生成/上传的音频素材 mediaId，后续扩展参考音频复用"
                clearable
              />
              <el-button
                type="success"
                plain
                :loading="mediaRegisterUploading && isMediaRegistering('CHARACTER_VOICE_AUDIO', selectedCharacterForVoice?.characterId)"
                @click="triggerCharacterVoiceReferenceUpload"
              >
                上传参考音
              </el-button>
              <el-button type="success" link @click="promptCharacterVoiceReferenceUrl">音频URL</el-button>
            </div>
          </el-form-item>
          <el-form-item label="试听文本">
            <el-input
              v-model="characterVoiceForm.voiceSampleText"
              type="textarea"
              :rows="2"
              maxlength="512"
              show-word-limit
              placeholder="例如：今天也要把班费账本整理清楚。"
            />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="语速">
                <el-input-number v-model="characterVoiceForm.voiceSpeedRatio" :min="0.5" :max="2" :step="0.05" :precision="2" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="音量">
                <el-input-number v-model="characterVoiceForm.voiceVolumeRatio" :min="0.1" :max="3" :step="0.05" :precision="2" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="音高">
                <el-input-number v-model="characterVoiceForm.voicePitchRatio" :min="0.5" :max="2" :step="0.05" :precision="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="characterVoiceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="characterVoiceSaving" @click="handleSaveCharacterVoice">
          保存声线资产
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="shotVideoDrawerVisible" size="760px" :title="shotVideoDrawerTitle">
      <div v-if="selectedShotForVideo" class="scene-image-drawer">
        <section class="shot-reference-panel">
          <div class="panel-title-row">
            <strong>当前分镜参考素材</strong>
            <small>来自上一镜尾帧/参考视频/参考音频、已确认场景图、角色图和道具图；点 + 可为当前分镜临时追加人物、场景或道具参考</small>
          </div>
          <div class="shot-reference-grid">
            <article
              v-for="option in shotVideoReferenceOptions"
              :key="`${option.mediaKind || 'image'}-${option.mediaId}`"
              class="shot-reference-card"
            >
              <video
                v-if="option.mediaKind === 'video' && referencePreviewUrls[option.mediaId]"
                class="shot-reference-thumb shot-reference-video"
                :src="referencePreviewUrls[option.mediaId]"
                muted
                controls
                playsinline
              />
              <div
                v-else-if="option.mediaKind === 'audio'"
                class="shot-reference-thumb shot-reference-audio"
              >
                <audio
                  v-if="referencePreviewUrls[option.mediaId]"
                  :src="referencePreviewUrls[option.mediaId]"
                  controls
                  preload="metadata"
                />
                <span v-else>音频加载中</span>
              </div>
              <el-image
                v-else-if="referencePreviewUrls[option.mediaId]"
                class="shot-reference-thumb"
                :src="referencePreviewUrls[option.mediaId]"
                :preview-src-list="shotVideoReferencePreviewList"
                fit="cover"
                preview-teleported
              />
              <el-empty v-else class="shot-reference-thumb shot-reference-placeholder" description="缩略图加载中" />
              <div class="shot-reference-text">
                <strong>{{ option.label }}</strong>
                <small>{{ option.subtitle }}</small>
              </div>
              <el-button
                v-if="option.removable"
                class="shot-reference-remove"
                type="danger"
                link
                @click="removeManualShotReference(selectedShotForVideo, option.mediaId)"
              >
                移除
              </el-button>
            </article>
            <el-popover
              placement="bottom-start"
              width="460"
              trigger="click"
              popper-class="shot-reference-add-popper"
            >
              <template #reference>
                <button class="shot-reference-add-card" type="button">
                  <span>+</span>
                  <strong>添加参考素材</strong>
                  <small>补人物 / 场景 / 道具</small>
                </button>
              </template>
              <div class="shot-reference-add-panel">
                <label>追加场景图</label>
                <el-select
                  :model-value="manualShotSceneReferenceIds(selectedShotForVideo)"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="选择已确认场景图"
                  @change="(value) => handleManualShotSceneReferenceChange(selectedShotForVideo, value)"
                >
                  <el-option
                    v-for="scene in confirmedSceneOptions"
                    :key="scene.sceneId"
                    :label="scene.sceneName || `场景 ${scene.sceneId}`"
                    :value="String(scene.lockedMediaId || '')"
                    :disabled="!scene.lockedMediaId || isAutoShotReferenceMedia(selectedShotForVideo, scene.lockedMediaId)"
                  >
                    <div class="shot-reference-add-option">
                      <img
                        v-if="scene.lockedMediaId && referencePreviewUrls[String(scene.lockedMediaId)]"
                        :src="referencePreviewUrls[String(scene.lockedMediaId)]"
                        alt="场景缩略图"
                      />
                      <span v-else class="shot-reference-add-placeholder">无图</span>
                      <div>
                        <strong>{{ scene.sceneName || `场景 ${scene.sceneId}` }}</strong>
                        <small>{{ [scene.timeDesc, scene.weather, scene.atmosphere, scene.visualFeatures].filter(Boolean).join(' / ') || '已确认场景' }}</small>
                      </div>
                    </div>
                  </el-option>
                </el-select>

                <label>追加道具图</label>
                <el-select
                  :model-value="manualShotPropReferenceIds(selectedShotForVideo)"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="选择已确认道具图"
                  @change="(value) => handleManualShotPropReferenceChange(selectedShotForVideo, value)"
                >
                  <el-option
                    v-for="prop in props"
                    :key="prop.propId"
                    :label="prop.propName || `道具 ${prop.propId}`"
                    :value="String(prop.lockedMediaId || '')"
                    :disabled="!prop.lockedMediaId || isAutoShotReferenceMedia(selectedShotForVideo, prop.lockedMediaId)"
                  >
                    <div class="shot-reference-add-option">
                      <img
                        v-if="prop.lockedMediaId && referencePreviewUrls[String(prop.lockedMediaId)]"
                        :src="referencePreviewUrls[String(prop.lockedMediaId)]"
                        alt="道具缩略图"
                      />
                      <span v-else class="shot-reference-add-placeholder">无图</span>
                      <div>
                        <strong>{{ prop.propName || `道具 ${prop.propId}` }}</strong>
                        <small>{{ propAppearanceSummary(prop) }}</small>
                      </div>
                    </div>
                  </el-option>
                </el-select>

                <label>追加角色图</label>
                <el-select
                  :model-value="manualShotCharacterReferenceIds(selectedShotForVideo)"
                  multiple
                  filterable
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="选择已确认角色图"
                  @change="(value) => handleManualShotCharacterReferenceChange(selectedShotForVideo, value)"
                >
                  <el-option
                    v-for="character in characters"
                    :key="character.characterId"
                    :label="character.characterName || `角色 ${character.characterId}`"
                    :value="String(character.lockedMediaId || '')"
                    :disabled="!character.lockedMediaId || isAutoShotReferenceMedia(selectedShotForVideo, character.lockedMediaId)"
                  >
                    <div class="shot-reference-add-option">
                      <img
                        v-if="character.lockedMediaId && referencePreviewUrls[String(character.lockedMediaId)]"
                        :src="referencePreviewUrls[String(character.lockedMediaId)]"
                        alt="角色缩略图"
                      />
                      <span v-else class="shot-reference-add-placeholder">无图</span>
                      <div>
                        <strong>{{ character.characterName || `角色 ${character.characterId}` }}</strong>
                        <small>{{ [character.storyRole, character.appearance].filter(Boolean).join(' / ') || '已确认角色' }}</small>
                      </div>
                    </div>
                  </el-option>
                </el-select>
                <small>已在当前分镜自动使用的素材会置灰，避免重复塞图。</small>
              </div>
            </el-popover>
          </div>
        </section>

        <section class="shot-screen-rule-panel">
          <div class="panel-title-row">
            <strong>当前画面人物规定</strong>
            <small>以分镜绑定角色为准；文案提到但未绑定的人会标为疑似缺失，避免视频里漏人或自动换人</small>
          </div>
          <div class="shot-screen-rule-grid">
            <div class="shot-screen-rule-item">
              <span>画内必须出现</span>
              <div>
                <el-tag
                  v-for="character in selectedShotScreenCharacterRule.onscreenCharacters"
                  :key="character.characterId"
                  type="success"
                  effect="plain"
                >
                  {{ character.characterName || `角色 ${character.characterId}` }}
                </el-tag>
                <el-tag v-if="!selectedShotScreenCharacterRule.onscreenCharacters.length" type="info" effect="plain">
                  未绑定，纯场景才可继续
                </el-tag>
              </div>
            </div>
            <div class="shot-screen-rule-item">
              <span>画外 / 不出现</span>
              <div>
                <el-tag
                  v-for="character in selectedShotScreenCharacterRule.offscreenCharacters"
                  :key="character.characterId"
                  type="info"
                  effect="plain"
                >
                  {{ character.characterName || `角色 ${character.characterId}` }}
                </el-tag>
                <el-tag v-if="!selectedShotScreenCharacterRule.offscreenCharacters.length" type="info" effect="plain">
                  未声明
                </el-tag>
              </div>
            </div>
            <div class="shot-screen-rule-item">
              <span>疑似缺失</span>
              <div>
                <el-tag
                  v-for="character in selectedShotScreenCharacterRule.missingCharacters"
                  :key="character.characterId"
                  type="danger"
                  effect="dark"
                >
                  {{ character.characterName || `角色 ${character.characterId}` }}
                </el-tag>
                <el-tag v-if="!selectedShotScreenCharacterRule.missingCharacters.length" type="success" effect="plain">
                  无
                </el-tag>
              </div>
            </div>
          </div>
          <p class="shot-screen-rule-note">{{ selectedShotScreenCharacterRule.positionRequirement }}</p>
        </section>

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

        <section class="shot-video-preflight-panel">
          <div class="panel-title-row">
            <strong>视频生成前预检</strong>
            <div class="preflight-title-actions">
              <el-button
                v-if="shotVideoFailedPreflightItems.length"
                size="small"
                type="primary"
                plain
                :loading="shotScriptOptimizing"
                @click="handleOptimizeShotScriptFromPreflight"
              >
                优化分镜脚本
              </el-button>
              <el-tag :type="shotVideoPreflightLevel" effect="plain">{{ shotVideoPreflightSummary }}</el-tag>
            </div>
          </div>
          <ul class="shot-video-preflight-list">
            <li v-for="item in shotVideoPreflightItems" :key="`${item.status}-${item.title}`">
              <el-tag :type="preflightTagType(item.status)" size="small">{{ preflightStatusText(item.status) }}</el-tag>
              <div>
                <strong>{{ item.title }}</strong>
                <small>{{ item.detail }}</small>
              </div>
            </li>
          </ul>
        </section>

        <section class="shot-tts-panel">
          <div class="panel-title-row">
            <div>
              <strong>配音素材生成（后期使用）</strong>
              <small>只生成“说出口”的台词素材；心理活动、脑海闪过、画面说明不要放进这里，最终声音在整片剪辑/混音阶段合入。</small>
            </div>
            <el-tag v-if="selectedShotTtsAudio" type="success" effect="plain">
              已选配音 #{{ selectedShotTtsAudio.mediaId }}
            </el-tag>
            <el-tag v-else type="info" effect="plain">未选配音</el-tag>
          </div>
          <el-input
            v-model="shotTtsText"
            type="textarea"
            :rows="2"
            maxlength="300"
            show-word-limit
            placeholder="填写本分镜要真正说出口的旁白/对白；如本镜无台词可留空"
          />
          <el-row :gutter="12" class="shot-tts-config">
            <el-col :span="6">
              <el-form-item label="说话人">
                <el-input v-model="shotTtsSpeaker" placeholder="如：喵小萌 / 旁白" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="音色ID">
                <el-input v-model="shotTtsVoiceType" placeholder="为空则后端按角色资产兜底" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="开始秒">
                <el-input-number
                  v-model="shotTtsStartSec"
                  :min="0"
                  :max="Math.max(0, Number(selectedShotForVideo?.durationSec || 5) - 0.1)"
                  :step="0.1"
                  :precision="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="结束秒">
                <el-input-number
                  v-model="shotTtsEndSec"
                  :min="Math.min(Number(selectedShotForVideo?.durationSec || 5), shotTtsStartSec + 0.1)"
                  :max="Number(selectedShotForVideo?.durationSec || 5)"
                  :step="0.1"
                  :precision="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="shot-tts-actions">
            <el-button
              type="primary"
              plain
              :loading="shotTtsGenerating"
              :disabled="shotTtsGenerating || !shotTtsText.trim()"
              @click="handleGenerateShotTtsAudio"
            >
              生成配音素材
            </el-button>
            <el-button :icon="Refresh" :loading="shotTtsGenerating" :disabled="shotTtsGenerating" @click="loadShotTtsAudios">
              刷新音频
            </el-button>
            <el-button
              type="success"
              plain
              :loading="mediaRegisterUploading && isMediaRegistering('SHOT_TTS_AUDIO', selectedShotForVideo?.shotId)"
              @click="triggerShotTtsUpload"
            >
              上传配音
            </el-button>
            <el-button type="success" plain @click="promptShotTtsUrl">配音URL</el-button>
          </div>
          <div v-if="shotTtsAudios.length" class="shot-tts-list">
            <article
              v-for="item in shotTtsAudios"
              :key="item.mediaId"
              class="shot-tts-card"
              :class="{ selected: item.selected === '1' }"
            >
              <audio v-if="referencePreviewUrls[String(item.mediaId)]" :src="referencePreviewUrls[String(item.mediaId)]" controls preload="metadata" />
              <span v-else class="shot-tts-placeholder">音频加载中</span>
              <div class="shot-tts-meta">
                <el-tag :type="item.selected === '1' ? 'success' : 'info'" effect="plain">
                  {{ item.selected === '1' ? '已选' : '候选' }}
                </el-tag>
                <span>配音 #{{ item.mediaId }}</span>
              </div>
              <el-button
                size="small"
                type="success"
                :disabled="item.selected === '1'"
                :loading="shotTtsSelectingIds.has(String(item.mediaId))"
                @click="handleSelectShotTtsAudio(item)"
              >
                选择这条
              </el-button>
            </article>
          </div>
        </section>

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
            v-for="(item, index) in shotVideoCandidates"
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
            <div class="scene-image-meta shot-video-candidate-meta">
              <el-tag :type="item.selected === '1' ? 'success' : 'info'">候选 {{ index + 1 }}</el-tag>
              <span>视频 #{{ item.mediaId }}</span>
              <span v-if="item.taskId">任务 #{{ item.taskId }}</span>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowUp, Check, DocumentChecked, Film, MagicStick, Refresh, Tickets, Upload, UserFilled, VideoCamera } from '@element-plus/icons-vue'
import {
  AIVIDEO_ASSET_STREAM_PATH,
  AIVIDEO_CHARACTER_IMAGE_STREAM_PATH,
  AIVIDEO_POLISH_STREAM_PATH,
  AIVIDEO_SCENE_IMAGE_STREAM_PATH,
  AIVIDEO_SCRIPT_STREAM_PATH,
  AIVIDEO_SHOT_VIDEO_STREAM_PATH,
  cancelConfirmAivideoAsset,
  cancelConfirmAivideoDocument,
  cancelConfirmAivideoPolish,
  cancelConfirmAivideoScript,
  confirmAivideoAsset,
  confirmAivideoDocument,
  confirmAivideoPolish,
  confirmAivideoScript,
  generateAivideoShotTtsAudio,
  generateAivideoProjectEdit,
  getAivideoProjectEditPreflight,
  getAivideoStudioTask,
  getLatestAivideoAssetTask,
  getAivideoProject,
  listAivideoMedia,
  listAivideoProjectEditTasks,
  listAivideoShotVideoTasks,
  optimizeAivideoShotScript,
  pollAivideoProjectEditTask,
  previewAivideoMedia,
  previewAivideoAssetPrompt,
  previewAivideoCharacterImagePrompt,
  previewAivideoPolishPrompt,
  previewAivideoSceneImagePrompt,
  previewAivideoShotVideoPrompt,
  previewAivideoScriptPrompt,
  registerAivideoMedia,
  saveAivideoDocument,
  selectAivideoMedia,
  updateAivideoCharacterVoice,
  updateAivideoProject,
  uploadAivideoMediaFile,
  uploadAivideoReferenceImage,
  type AivideoCharacter,
  type AivideoMediaAsset,
  type AivideoProp,
  type AivideoProjectEditPreflight,
  type AivideoProjectDetail,
  type AivideoScene,
  type AivideoShot,
  type AivideoSourceDocument,
  type AivideoTask
} from '@/api/aivideo'
import JsonStructureViewer from '@/components/aivideo/JsonStructureViewer.vue'
import MarkdownViewer from '@/components/aivideo/MarkdownViewer.vue'
import ReferenceImagePicker from '@/components/aivideo/ReferenceImagePicker.vue'
import { requestAiStream, type AiStreamMetaPayload } from '@/utils/ai-stream'
import { createAivideoDictOptionState } from '@/utils/aivideo-dict-options'
import { useUserStore } from '@/stores/user'

type WorkbenchTab = 'document' | 'polish' | 'script' | 'assets' | 'task'
type PromptScope = 'global' | 'polish' | 'script' | 'asset' | 'characterImage' | 'sceneImage' | 'shotVideo'
type StrategyKey = 'defaultStyle' | 'generationStrategy' | 'audioMode' | 'subtitleMode' | 'referenceStrategy' | 'actionIntensity' | 'continuityLevel' | 'multiRoleStrategy' | 'characterDesignType'
type MediaRegisterAssetType = 'PROP_IMAGE' | 'CHARACTER_VOICE_AUDIO' | 'PROJECT_BGM_AUDIO' | 'SHOT_SFX_AUDIO' | 'SHOT_TTS_AUDIO'
type MediaRegisterBizType = 'PROP' | 'CHARACTER' | 'PROJECT' | 'SHOT'

interface StageStrategyItem {
  key: StrategyKey
  label: string
  value: string
}

interface ReferenceImageOption {
  mediaId: string
  label: string
  subtitle: string
  sourceName: string
  mediaKind?: 'image' | 'video' | 'audio'
  removable?: boolean
}

interface ShotVideoPreflightItem {
  status: 'pass' | 'warn' | 'fail'
  title: string
  detail: string
}

interface CharacterVoiceForm {
  voiceMode: string
  voiceType: string
  voiceName: string
  voiceDesc: string
  voiceReferenceMediaId: string
  voiceSampleText: string
  voiceSpeedRatio: number
  voiceVolumeRatio: number
  voicePitchRatio: number
}

interface MediaRegisterContext {
  assetType: MediaRegisterAssetType
  bizType: MediaRegisterBizType
  bizId: string | number
  title: string
  accept: string
  promptText?: string
  paramsJson?: string
  comment?: string
  successMessage?: string
  afterRegistered?: (asset: AivideoMediaAsset) => Promise<void> | void
}

/**
 * 工作台同时消费项目状态字典和生成策略字典，统一走公共状态层避免和设置页分叉。
 */
const {
  actionIntensityOptions,
  audioModeOptions,
  characterDesignTypeOptions,
  continuityLevelOptions,
  generationStrategyOptions,
  loadAllOptions,
  multiRoleStrategyOptions,
  projectStageOptions,
  ratioOptions,
  referenceStrategyOptions,
  subtitleModeOptions,
  visualStyleOptions,
  labelOf
} = createAivideoDictOptionState()

interface ShotScreenCharacterRule {
  onscreenCharacters: AivideoCharacter[]
  mentionedCharacters: AivideoCharacter[]
  missingCharacters: AivideoCharacter[]
  offscreenCharacters: AivideoCharacter[]
  previousMissingCharacters: AivideoCharacter[]
  expectedOnscreenCount: number
  expectedOnscreenCountReason: string
  positionRequirement: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const invalidProjectRouteHandled = ref(false)
const savingStrategies = ref(false)
const paramsEditing = ref(false)
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
const characterVoiceDialogVisible = ref(false)
const selectedCharacterForVoice = ref<AivideoCharacter>()
const characterVoiceSaving = ref(false)
const characterVoiceForm = reactive<CharacterVoiceForm>({
  voiceMode: 'POST_TTS',
  voiceType: '',
  voiceName: '',
  voiceDesc: '',
  voiceReferenceMediaId: '',
  voiceSampleText: '',
  voiceSpeedRatio: 1,
  voiceVolumeRatio: 1,
  voicePitchRatio: 1
})
const characterImagePromptPreviewText = ref('')
const characterReferenceMediaIds = ref<string[]>([])
const characterReferenceImageUrlInput = ref('')
const characterReferenceImageUrls = ref<string[]>([])
const characterReferenceUploading = ref(false)
const characterImageGenerating = ref(false)
const characterImageCandidates = ref<AivideoMediaAsset[]>([])
const characterImagePreviewUrls = ref<Record<string, string>>({})
const characterImageSelectingIds = ref<Set<string>>(new Set())
const sceneImageDrawerVisible = ref(false)
const selectedSceneForImage = ref<AivideoScene>()
const sceneImagePromptPreviewText = ref('')
const sceneReferenceMediaIds = ref<string[]>([])
const sceneReferenceImageUrlInput = ref('')
const sceneReferenceImageUrls = ref<string[]>([])
const sceneReferenceUploading = ref(false)
const sceneImageGenerating = ref(false)
const sceneImageCandidates = ref<AivideoMediaAsset[]>([])
const sceneImagePreviewUrls = ref<Record<string, string>>({})
const sceneImageSelectingIds = ref<Set<string>>(new Set())
const referencePreviewUrls = ref<Record<string, string>>({})
const shotVideoDrawerVisible = ref(false)
const selectedShotForVideo = ref<AivideoShot>()
const shotVideoPromptPreviewText = ref('')
const shotVideoLoading = ref(false)
const shotVideoGenerating = ref(false)
const shotScriptOptimizing = ref(false)
const shotMediaAssets = ref<AivideoMediaAsset[]>([])
const shotVideoCandidates = ref<AivideoMediaAsset[]>([])
const shotVideoTasks = ref<AivideoTask[]>([])
const shotVideoPreviewUrls = ref<Record<string, string>>({})
const shotVideoSelectingIds = ref<Set<string>>(new Set())
const shotTtsAudios = ref<AivideoMediaAsset[]>([])
const shotTtsText = ref('')
const shotTtsSpeaker = ref('')
const shotTtsVoiceType = ref('')
const shotTtsStartSec = ref(0)
const shotTtsEndSec = ref(0)
const shotTtsGenerating = ref(false)
const shotTtsSelectingIds = ref<Set<string>>(new Set())
const shotManualReferenceMediaIdsByShotId = ref<Record<string, string[]>>({})
const projectEditPreflight = ref<AivideoProjectEditPreflight>()
const projectEditTasks = ref<AivideoTask[]>([])
const projectEditVideos = ref<AivideoMediaAsset[]>([])
const projectEditLoading = ref(false)
const projectEditGenerating = ref(false)
const mediaRegisterFileInputRef = ref<HTMLInputElement>()
const mediaRegisterContext = ref<MediaRegisterContext>()
const mediaRegisterUploading = ref(false)
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
const sceneReferenceFileInputRef = ref<HTMLInputElement>()
const characterReferenceFileInputRef = ref<HTMLInputElement>()
const detail = reactive<AivideoProjectDetail>({})
let promptPreviewTimer: ReturnType<typeof setTimeout> | undefined
let assetTaskPollTimer: ReturnType<typeof setTimeout> | undefined
let shotVideoRecoveryTimer: ReturnType<typeof setTimeout> | undefined
let projectEditPollTimer: ReturnType<typeof setTimeout> | undefined
const inFlightTaskStatuses = new Set(['PENDING', 'RUNNING'])
const ASSET_TASK_POLL_INTERVAL = 3_000
const SHOT_VIDEO_RECOVERY_INTERVAL = 15_000
const PROJECT_EDIT_POLL_INTERVAL = 5_000
type ShotMediaAssetType = 'SHOT_VIDEO' | 'SHOT_TAIL_FRAME' | 'SHOT_AUDIO' | 'SHOT_TTS_AUDIO' | 'SHOT_SFX_AUDIO'
const AUDIO_FILE_ACCEPT = 'audio/*,.mp3,.wav,.m4a,.aac,.ogg'
const characterVoiceModeOptions = [
  { label: '后期 TTS（推荐）', value: 'POST_TTS' },
  { label: '参考音频有声', value: 'REFERENCE_AUDIO' },
  { label: '原生有声提示', value: 'NATIVE_AUDIO' }
]

const sourceDraft = reactive({
  sourceType: 'TEXT',
  fileName: '',
  rawText: ''
})
const sourceDraftDocumentId = ref<string | number>()
const sourceDraftHydratedDocumentKey = ref('')

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
  multiRoleStrategy: 'SINGLE_FIRST',
  characterDesignType: 'AUTO'
})
const paramsSnapshot = ref<{
  params: typeof params
  promptScopes: Record<PromptScope, string>
} | null>(null)

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
  global: ['defaultStyle', 'characterDesignType', 'generationStrategy', 'audioMode', 'subtitleMode', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  polish: ['defaultStyle'],
  script: ['defaultStyle', 'characterDesignType', 'generationStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  asset: ['defaultStyle', 'characterDesignType', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy'],
  characterImage: ['defaultStyle', 'characterDesignType', 'referenceStrategy', 'multiRoleStrategy'],
  sceneImage: ['defaultStyle', 'referenceStrategy', 'continuityLevel'],
  shotVideo: ['defaultStyle', 'characterDesignType', 'generationStrategy', 'audioMode', 'subtitleMode', 'referenceStrategy', 'actionIntensity', 'continuityLevel', 'multiRoleStrategy']
}

const strategyLabels: Record<StrategyKey, string> = {
  defaultStyle: '视觉风格',
  generationStrategy: '生成策略',
  audioMode: '声音模式',
  subtitleMode: '字幕模式',
  referenceStrategy: '参考素材策略',
  actionIntensity: '动作强度',
  continuityLevel: '连续性强度',
  multiRoleStrategy: '多角色策略',
  characterDesignType: '角色造型类型'
}

const projectId = computed(() => {
  const value = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
})
const hasValidProjectId = computed(() => /^[1-9]\d*$/.test(projectId.value))
const documents = computed(() => detail.documents || [])
const latestDocument = computed(() => documents.value[0])
const latestSourceText = computed(() => latestDocument.value?.parsedText || latestDocument.value?.rawText || '')
const showDocumentEditor = computed(() => !documents.value.length || latestDocument.value?.confirmed !== '1')
const visibleDocuments = computed(() => documents.value.filter((doc) => {
  if (!sourceDraftDocumentId.value) {
    return true
  }
  return String(doc.documentId) !== String(sourceDraftDocumentId.value)
}))
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
const props = computed(() => detail.props || [])
const shots = computed(() => detail.shots || [])
const confirmedSceneOptions = computed(() => scenes.value.filter((item) => String(item.confirmStatus || '').toUpperCase() === 'APPROVED'))
const characterReferenceOptions = computed(() => characters.value
  .filter((item) => !!item.lockedMediaId)
  .map(characterToReferenceOption))
const sceneReferenceOptions = computed(() => scenes.value
  .filter((item) => !!item.lockedMediaId)
  .map(sceneToReferenceOption))
const propReferenceOptions = computed(() => props.value
  .filter((item) => !!item.lockedMediaId)
  .map(propToReferenceOption))
const characterSelectedReferenceOptions = computed(() => selectedReferenceOptions(characterReferenceMediaIds.value, characterReferenceOptions.value))
const sceneSelectedReferenceOptions = computed(() => selectedReferenceOptions(sceneReferenceMediaIds.value, sceneReferenceOptions.value))
const shotVideoReferenceOptions = computed(() => buildShotVideoReferenceOptions(selectedShotForVideo.value))
const shotVideoAddableReferenceOptions = computed(() => [
  ...sceneReferenceOptions.value,
  ...characterReferenceOptions.value,
  ...propReferenceOptions.value
])
const shotVideoReferencePreviewList = computed(() => shotVideoReferenceOptions.value
  .filter((item) => (item.mediaKind || 'image') === 'image')
  .map((item) => referencePreviewUrls.value[item.mediaId])
  .filter(Boolean))

function shouldRedirectForInvalidProject(error: any) {
  const message = String(error?.message || '')
  return [
    '项目不存在',
    '无权访问该项目',
    '请求参数错误',
    'status code 400',
    'status code 404'
  ].some((keyword) => message.includes(keyword))
}

function redirectToProjectList(message = '项目不存在或链接已失效，已返回项目列表') {
  if (invalidProjectRouteHandled.value) {
    return
  }
  invalidProjectRouteHandled.value = true
  ElMessage.warning(message)
  void router.replace('/studio/projects')
}

function ensureProjectContext(message = '项目链接无效，已返回项目列表') {
  if (hasValidProjectId.value) {
    return true
  }
  redirectToProjectList(message)
  return false
}
const mediaRegisterAccept = computed(() => mediaRegisterContext.value?.accept || '')
const selectedShotScreenCharacterRule = computed(() => buildShotScreenCharacterRule(selectedShotForVideo.value))
const selectedShotTtsAudio = computed(() => shotTtsAudios.value.find(isSelectedMediaAsset))
const shotVideoPreflightItems = computed(() => buildShotVideoPreflightItems(selectedShotForVideo.value))
const shotVideoFailedPreflightItems = computed(() => shotVideoPreflightItems.value.filter((item) => item.status === 'fail'))
const shotVideoPreflightLevel = computed(() => {
  if (shotVideoPreflightItems.value.some((item) => item.status === 'fail')) {
    return 'danger'
  }
  if (shotVideoPreflightItems.value.some((item) => item.status === 'warn')) {
    return 'warning'
  }
  return 'success'
})
const shotVideoPreflightSummary = computed(() => {
  if (shotVideoPreflightLevel.value === 'danger') {
    return '存在不合格项'
  }
  if (shotVideoPreflightLevel.value === 'warning') {
    return '需人工复核'
  }
  return '系统检查通过'
})
const assetCounts = computed(() => ({
  characters: characters.value.length,
  scenes: scenes.value.length,
  props: props.value.length,
  shots: shots.value.length
}))
const hasAssets = computed(() => characters.value.length > 0 || scenes.value.length > 0 || props.value.length > 0 || shots.value.length > 0)
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
  ...props.value,
  ...shots.value
].some((item) => item.confirmStatus === 'APPROVED'))
const hasAnyConfirmedItem = computed(() =>
  latestDocument.value?.confirmed === '1'
  || !!selectedPolish.value
  || !!selectedScript.value
  || hasApprovedAssets.value
)
const activeStageStrategyItems = computed(() => stageStrategyItems(activePromptScope.value))
const sceneImageDrawerTitle = computed(() => selectedSceneForImage.value?.sceneName
  ? `场景图候选：${selectedSceneForImage.value.sceneName}`
  : '场景图候选')
const characterImageDrawerTitle = computed(() => selectedCharacterForImage.value?.characterName
  ? `角色图候选：${selectedCharacterForImage.value.characterName}`
  : '角色图候选')
const characterVoiceDialogTitle = computed(() => selectedCharacterForVoice.value?.characterName
  ? `角色声线资产：${selectedCharacterForVoice.value.characterName}`
  : '角色声线资产')
const shotVideoDrawerTitle = computed(() => selectedShotForVideo.value
  ? `分镜视频候选：第 ${selectedShotForVideo.value.episodeNo || 1} 集 / 镜头 ${selectedShotForVideo.value.shotNo || '-'}`
  : '分镜视频候选')
const flowSteps = computed(() => [
  { label: '原文', name: 'document' as WorkbenchTab, icon: DocumentChecked, count: documents.value.length },
  { label: '润色', name: 'polish' as WorkbenchTab, icon: MagicStick, count: polishVersions.value.length },
  { label: '剧本', name: 'script' as WorkbenchTab, icon: Tickets, count: scriptVersions.value.length },
  { label: '资产', name: 'assets' as WorkbenchTab, icon: UserFilled, count: characters.value.length + scenes.value.length + props.value.length + shots.value.length + projectEditVideos.value.length },
  { label: '任务', name: 'task' as WorkbenchTab, icon: Film, count: detail.latestTask ? 1 : 0 }
])
const hasRunningShotVideoTask = computed(() => shotVideoTasks.value.some(isShotVideoTaskInFlight))
const hasRecoverableShotVideoTask = computed(() => shotVideoTasks.value.some(isRecoverableShotVideoTask))
const projectEditClips = computed(() => projectEditPreflight.value?.clips || [])
const projectEditErrors = computed(() => projectEditPreflight.value?.errors || [])
const projectEditWarnings = computed(() => projectEditPreflight.value?.warnings || [])
const projectEditReady = computed(() => !!projectEditPreflight.value?.ready)
const hasRunningProjectEditTask = computed(() => projectEditTasks.value.some(isShotVideoTaskInFlight))
const previousShotForVideo = computed(() => {
  const current = selectedShotForVideo.value
  return current ? findPreviousShot(current) : undefined
})
const previousShotVideoRequired = computed(() => {
  const current = selectedShotForVideo.value
  const previous = previousShotForVideo.value
  return !!current && !!previous && shotRequiresPreviousVideo(current) && !previous.videoMediaId
})
const previousShotGateMessage = computed(() => {
  const previous = previousShotForVideo.value
  if (!previous) {
    return ''
  }
  return `当前分镜是连续镜头，请先为上一分镜（第 ${previous.episodeNo || 1} 集 / 镜头 ${previous.shotNo || '-'}）选择并确认视频，系统会把它的尾帧作为当前分镜衔接参考`
})
const shotVideoActionLocked = computed(() => shotVideoLoading.value
  || shotVideoGenerating.value
  || hasRunningShotVideoTask.value
  || previousShotVideoRequired.value)
const shotMediaByShotId = computed(() => {
  const result = new Map<string, AivideoMediaAsset[]>()
  shotMediaAssets.value.forEach((item) => {
    if (String(item.bizType || '').toUpperCase() !== 'SHOT' || !item.bizId) {
      return
    }
    const shotId = String(item.bizId)
    const next = result.get(shotId) || []
    next.push(item)
    result.set(shotId, next)
  })
  return result
})

function getStageLabel(value?: string) {
  return labelOf(projectStageOptions, value, '草稿')
}

function findPreviousShot(shot?: AivideoShot) {
  if (!shot) {
    return undefined
  }
  const currentEpisodeNo = Number(shot.episodeNo || 1)
  const currentShotNo = Number(shot.shotNo || 0)
  if (!currentShotNo) {
    return undefined
  }
  return [...shots.value]
    .filter((item) => String(item.shotId) !== String(shot.shotId))
    .filter((item) => Number(item.episodeNo || 1) === currentEpisodeNo)
    .filter((item) => Number(item.shotNo || 0) < currentShotNo)
    .sort((a, b) => Number(b.shotNo || 0) - Number(a.shotNo || 0))[0]
}

function normalizedShotTransitionType(shot?: AivideoShot) {
  const raw = String(shot?.transitionBeforeType || '').trim().toUpperCase()
  if (['OPENING', 'CONTINUE', 'SCENE_CUT', 'TIME_JUMP', 'MONTAGE', 'INSERT'].includes(raw)) {
    return raw
  }
  const previous = findPreviousShot(shot)
  if (!previous) {
    return 'OPENING'
  }
  if (String(previous.sceneId || '') !== String(shot?.sceneId || '')) {
    return 'SCENE_CUT'
  }
  return hasNewShotCharacters(shot, previous) ? 'INSERT' : 'CONTINUE'
}

function shotRequiresPreviousVideo(shot?: AivideoShot) {
  return normalizedShotTransitionType(shot) === 'CONTINUE'
}

function hasNewShotCharacters(shot?: AivideoShot, previous?: AivideoShot) {
  const currentCharacters = parseShotCharacterIds(shot?.characterIds)
  if (!currentCharacters.length) {
    return false
  }
  const previousCharacters = parseShotCharacterIds(previous?.characterIds)
  if (!previousCharacters.length) {
    return true
  }
  return currentCharacters.some((character) => !previousCharacters.includes(character))
}

function findSceneById(sceneId?: string | number) {
  if (sceneId == null || sceneId === '') {
    return undefined
  }
  return scenes.value.find((item) => String(item.sceneId) === String(sceneId))
}

function sceneNameById(sceneId?: string | number) {
  return findSceneById(sceneId)?.sceneName || ''
}

function shotTransitionLabel(shot?: AivideoShot) {
  const type = normalizedShotTransitionType(shot)
  const labels: Record<string, string> = {
    OPENING: '开场',
    CONTINUE: '连续',
    SCENE_CUT: '切场',
    TIME_JUMP: '时间跳转',
    MONTAGE: '蒙太奇',
    INSERT: '插入镜头'
  }
  return labels[type] || type
}

function shotTransitionTagType(shot?: AivideoShot) {
  const type = normalizedShotTransitionType(shot)
  if (type === 'CONTINUE') {
    return 'success'
  }
  if (type === 'OPENING') {
    return 'info'
  }
  if (type === 'SCENE_CUT') {
    return 'warning'
  }
  return 'primary'
}

function shotTransitionDesc(shot?: AivideoShot) {
  if (shot?.transitionBeforeDesc) {
    return shot.transitionBeforeDesc
  }
  const type = normalizedShotTransitionType(shot)
  const previous = findPreviousShot(shot)
  if (type === 'OPENING' || !previous) {
    return '建立当前场景'
  }
  if (type === 'CONTINUE') {
    return '同一场景，生成时需要上一尾帧'
  }
  if (type === 'SCENE_CUT') {
    return `${sceneNameById(previous.sceneId) || '上一场景'} -> ${sceneNameById(shot?.sceneId) || '当前场景'}，生成时不强制上一尾帧`
  }
  return '后期剪辑边界，生成时不强制上一尾帧'
}

function isSelectedMediaAsset(item?: AivideoMediaAsset) {
  return String(item?.selected || '') === '1'
}

function isShotMediaAssetOfType(item: AivideoMediaAsset, assetType: ShotMediaAssetType) {
  return String(item.assetType || '').toUpperCase() === assetType
}

function compareMediaAssetDesc(a: AivideoMediaAsset, b: AivideoMediaAsset) {
  const mediaDiff = Number(b.mediaId) - Number(a.mediaId)
  if (Number.isFinite(mediaDiff) && mediaDiff !== 0) {
    return mediaDiff
  }
  return String(b.createTime || '').localeCompare(String(a.createTime || ''))
}

function findShotMediaAsset(shot: AivideoShot, assetType: ShotMediaAssetType) {
  const shotId = String(shot.shotId)
  const assets = (shotMediaByShotId.value.get(shotId) || [])
    .filter((item) => isShotMediaAssetOfType(item, assetType))
    .sort(compareMediaAssetDesc)
  const fixedMediaId = assetType === 'SHOT_VIDEO'
    ? shot.videoMediaId
    : assetType === 'SHOT_TAIL_FRAME'
      ? shot.tailFrameMediaId
      : undefined
  if (fixedMediaId) {
    const fixedAsset = assets.find((item) => String(item.mediaId) === String(fixedMediaId))
    if (fixedAsset) {
      return fixedAsset
    }
    return {
      mediaId: fixedMediaId,
      assetType,
      bizType: 'SHOT',
      bizId: shot.shotId
    } as AivideoMediaAsset
  }
  return assets.find(isSelectedMediaAsset) || assets[0]
}

function shotVideoTaskId(shot: AivideoShot) {
  return findShotMediaAsset(shot, 'SHOT_VIDEO')?.taskId
}

function shotVideoMediaId(shot: AivideoShot) {
  return findShotMediaAsset(shot, 'SHOT_VIDEO')?.mediaId
}

function shotTailFrameMediaId(shot: AivideoShot) {
  return findShotMediaAsset(shot, 'SHOT_TAIL_FRAME')?.mediaId
}

function shotAudioMediaId(shot: AivideoShot) {
  return findShotMediaAsset(shot, 'SHOT_AUDIO')?.mediaId
}

function shotTtsAudioMediaId(shot: AivideoShot) {
  return findShotMediaAsset(shot, 'SHOT_TTS_AUDIO')?.mediaId
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

function optionLabel(
  options: Array<{ label: string; value: string }> | Ref<Array<{ label: string; value: string }>>,
  value?: string
) {
  const optionList = Array.isArray(options) ? options : options.value
  return optionList.find((item) => item.value === value)?.label || value || ''
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
    case 'characterDesignType':
      return optionLabel(characterDesignTypeOptions, params.characterDesignType)
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
      '角色造型类型为 Q版萌系全身时，只允许 Q版完整全身角色锚定图，禁止头像、大头贴、半身、表情包和多角度拼图。',
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

function characterToReferenceOption(character: AivideoCharacter): ReferenceImageOption {
  const mediaId = String(character.lockedMediaId || '')
  return {
    mediaId,
    label: `${character.characterName || '未命名角色'} #${mediaId}`,
    subtitle: [character.storyRole, character.appearance].filter(Boolean).join(' / ') || '已确认角色图',
    sourceName: character.characterName || '未命名角色'
  }
}

function sceneToReferenceOption(scene: AivideoScene): ReferenceImageOption {
  const mediaId = String(scene.lockedMediaId || '')
  return {
    mediaId,
    label: `${scene.sceneName || '未命名场景'} #${mediaId}`,
    subtitle: [scene.timeDesc, scene.weather, scene.atmosphere, scene.visualFeatures].filter(Boolean).join(' / ') || '已确认场景图',
    sourceName: scene.sceneName || '未命名场景'
  }
}

function propToReferenceOption(prop: AivideoProp): ReferenceImageOption {
  const mediaId = String(prop.lockedMediaId || '')
  return {
    mediaId,
    label: `道具：${prop.propName || '未命名道具'} #${mediaId}`,
    subtitle: propAppearanceSummary(prop),
    sourceName: prop.propName || '未命名道具'
  }
}

function propAppearanceSummary(prop: AivideoProp) {
  return [prop.color, prop.material, prop.shape, prop.visualDesc].filter(Boolean).join(' / ') || '已确认道具图'
}

function propContinuitySummary(prop: AivideoProp) {
  const handoff = [
    prop.ownerCharacterName ? `归属：${prop.ownerCharacterName}` : '',
    prop.firstShotNo ? `首次镜头：${prop.firstShotNo}` : '',
    prop.lastHolder ? `最后持有：${prop.lastHolder}` : ''
  ].filter(Boolean).join('；')
  return [handoff, prop.continuityRules].filter(Boolean).join('；') || '暂无连续性说明'
}

function selectedReferenceOptions(selectedIds: string[], options: ReferenceImageOption[]) {
  const optionMap = new Map(options.map((item) => [item.mediaId, item]))
  return selectedIds.map((id) => optionMap.get(String(id))).filter(Boolean) as ReferenceImageOption[]
}

function compactUrl(url: string) {
  const value = String(url || '').trim()
  return value.length > 46 ? `${value.slice(0, 22)}...${value.slice(-20)}` : value
}

function normalizeExternalReferenceUrls(urls: string[]) {
  return Array.from(new Set((urls || []).map((url) => String(url || '').trim()).filter(Boolean)))
}

function appendExternalReferenceUrl(urlsRef: Ref<string[]>, value: string) {
  const url = String(value || '').trim()
  if (!url) {
    return false
  }
  const next = normalizeExternalReferenceUrls([...urlsRef.value, url])
  if (next.length > 9) {
    ElMessage.warning('参考图最多 9 张，已自动保留前 9 张')
    urlsRef.value = next.slice(0, 9)
  } else {
    urlsRef.value = next
  }
  return true
}

function triggerSceneReferenceUpload() {
  sceneReferenceFileInputRef.value?.click()
}

function triggerCharacterReferenceUpload() {
  characterReferenceFileInputRef.value?.click()
}

async function uploadReferenceImageFile(file: File, urlsRef: Ref<string[]>) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return false
  }
  const res = await uploadAivideoReferenceImage(file)
  const url = res.data?.url
  if (!url) {
    ElMessage.error('上传成功但没有返回可用图片地址')
    return false
  }
  appendExternalReferenceUrl(urlsRef, url)
  return true
}

async function handleUploadSceneReferenceImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  sceneReferenceUploading.value = true
  try {
    if (await uploadReferenceImageFile(file, sceneReferenceImageUrls)) {
      ElMessage.success('场景参考图已上传')
      await refreshSceneImagePromptPreview()
    }
  } finally {
    sceneReferenceUploading.value = false
    input.value = ''
  }
}

async function handleUploadCharacterReferenceImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  characterReferenceUploading.value = true
  try {
    if (await uploadReferenceImageFile(file, characterReferenceImageUrls)) {
      ElMessage.success('角色参考图已上传')
      await refreshCharacterImagePromptPreview()
    }
  } finally {
    characterReferenceUploading.value = false
    input.value = ''
  }
}

async function handleAddSceneReferenceUrl() {
  if (appendExternalReferenceUrl(sceneReferenceImageUrls, sceneReferenceImageUrlInput.value)) {
    sceneReferenceImageUrlInput.value = ''
    await refreshSceneImagePromptPreview()
  }
}

async function handleAddCharacterReferenceUrl() {
  if (appendExternalReferenceUrl(characterReferenceImageUrls, characterReferenceImageUrlInput.value)) {
    characterReferenceImageUrlInput.value = ''
    await refreshCharacterImagePromptPreview()
  }
}

async function removeSceneReferenceUrl(index: number) {
  sceneReferenceImageUrls.value = sceneReferenceImageUrls.value.filter((_url, currentIndex) => currentIndex !== index)
  await refreshSceneImagePromptPreview()
}

async function removeCharacterReferenceUrl(index: number) {
  characterReferenceImageUrls.value = characterReferenceImageUrls.value.filter((_url, currentIndex) => currentIndex !== index)
  await refreshCharacterImagePromptPreview()
}

function isMediaRegistering(assetType: MediaRegisterAssetType, bizId?: string | number) {
  const context = mediaRegisterContext.value
  return !!context
    && context.assetType === assetType
    && String(context.bizId) === String(bizId || '')
}

function triggerMediaRegisterUpload(context: MediaRegisterContext) {
  mediaRegisterContext.value = context
  mediaRegisterFileInputRef.value?.click()
}

async function promptRegisterMediaUrl(context: MediaRegisterContext) {
  let value = ''
  try {
    const result = await ElMessageBox.prompt(
      `粘贴 ${context.title} 的可访问 URL。支持 http(s) 地址或系统 /file/public/ 地址。`,
      `添加${context.title}`,
      {
        confirmButtonText: '添加并选中',
        cancelButtonText: '取消',
        inputPattern: /^(https?:\/\/|\/file\/public\/).+/i,
        inputErrorMessage: '请输入 http(s) 或 /file/public/ 开头的地址'
      }
    )
    value = String(result.value || '').trim()
  } catch (_error) {
    // 用户取消时不提示错误。
    return
  }
  try {
    await registerMediaFromSource(context, value)
  } catch (error: any) {
    ElMessage.error(error?.message || `${context.title}登记失败`)
  }
}

async function handleRegisterMediaUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const context = mediaRegisterContext.value
  if (!file || !context) {
    input.value = ''
    return
  }
  mediaRegisterUploading.value = true
  try {
    const res = await uploadAivideoMediaFile(file)
    const uploaded = res.data
    const url = uploaded?.url
    if (!url) {
      ElMessage.error('上传成功但没有返回可用媒体地址')
      return
    }
    await registerMediaFromSource(context, url, uploaded?.id)
  } catch (error: any) {
    ElMessage.error(error?.message || `${context.title}上传失败`)
  } finally {
    mediaRegisterUploading.value = false
    mediaRegisterContext.value = undefined
    input.value = ''
  }
}

async function registerMediaFromSource(context: MediaRegisterContext, fileUrl: string, fileId?: string | number) {
  const url = String(fileUrl || '').trim()
  if (!url) {
    return
  }
  const res = await registerAivideoMedia({
    projectId: projectId.value,
    assetType: context.assetType,
    bizType: context.bizType,
    bizId: context.bizId,
    fileId,
    fileUrl: url,
    promptText: context.promptText,
    paramsJson: context.paramsJson,
    selected: true,
    comment: context.comment
  })
  const asset = res.data
  if (asset) {
    await context.afterRegistered?.(asset)
    if (asset.mediaId) {
      await loadReferencePreviewUrl(asset.mediaId)
    }
  }
  ElMessage.success(context.successMessage || `${context.title}已登记并选中`)
}

function buildPropImageMediaContext(prop: AivideoProp): MediaRegisterContext {
  return {
    assetType: 'PROP_IMAGE',
    bizType: 'PROP',
    bizId: prop.propId,
    title: `道具图：${prop.propName || prop.propId}`,
    accept: 'image/*',
    promptText: prop.promptText || propAppearanceSummary(prop),
    comment: '手动补充道具参考图',
    successMessage: '道具图已登记并选中',
    afterRegistered: async () => {
      await loadDetail()
      await refreshShotMediaAssets()
    }
  }
}

function triggerPropImageUpload(prop: AivideoProp) {
  triggerMediaRegisterUpload(buildPropImageMediaContext(prop))
}

function promptPropImageUrl(prop: AivideoProp) {
  void promptRegisterMediaUrl(buildPropImageMediaContext(prop))
}

function buildCharacterVoiceMediaContext(character?: AivideoCharacter): MediaRegisterContext | undefined {
  if (!character) {
    ElMessage.warning('请先选择角色')
    return undefined
  }
  return {
    assetType: 'CHARACTER_VOICE_AUDIO',
    bizType: 'CHARACTER',
    bizId: character.characterId,
    title: `角色声线参考音：${character.characterName || character.characterId}`,
    accept: AUDIO_FILE_ACCEPT,
    promptText: characterVoiceForm.voiceDesc || character.voiceDesc || character.characterName,
    comment: '手动补充角色声线参考音频',
    successMessage: '参考音频已写入声线表单，请保存声线资产',
    afterRegistered: async (asset) => {
      characterVoiceForm.voiceReferenceMediaId = String(asset.mediaId || '')
    }
  }
}

function triggerCharacterVoiceReferenceUpload() {
  const context = buildCharacterVoiceMediaContext(selectedCharacterForVoice.value)
  if (context) {
    triggerMediaRegisterUpload(context)
  }
}

function promptCharacterVoiceReferenceUrl() {
  const context = buildCharacterVoiceMediaContext(selectedCharacterForVoice.value)
  if (context) {
    void promptRegisterMediaUrl(context)
  }
}

function buildProjectBgmMediaContext(): MediaRegisterContext {
  return {
    assetType: 'PROJECT_BGM_AUDIO',
    bizType: 'PROJECT',
    bizId: projectId.value,
    title: '项目背景音乐',
    accept: AUDIO_FILE_ACCEPT,
    promptText: detail.project?.projectName || '整片背景音乐',
    comment: '手动补充整片背景音乐',
    successMessage: '项目 BGM 已登记并选中',
    afterRegistered: async () => {
      await refreshProjectEditPanel()
    }
  }
}

function triggerProjectBgmUpload() {
  triggerMediaRegisterUpload(buildProjectBgmMediaContext())
}

function promptProjectBgmUrl() {
  void promptRegisterMediaUrl(buildProjectBgmMediaContext())
}

function buildShotSfxMediaContext(shot: Pick<AivideoShot, 'shotId' | 'shotNo' | 'sfxCues'>): MediaRegisterContext {
  return {
    assetType: 'SHOT_SFX_AUDIO',
    bizType: 'SHOT',
    bizId: shot.shotId,
    title: `镜头 ${shot.shotNo || '-'} 音效`,
    accept: AUDIO_FILE_ACCEPT,
    promptText: shot.sfxCues || `镜头 ${shot.shotNo || ''} 音效`,
    comment: '手动补充镜头音效',
    successMessage: '镜头音效已登记并选中',
    afterRegistered: async () => {
      await refreshShotMediaAssets()
      await refreshProjectEditPanel()
      await loadDetail()
    }
  }
}

function triggerShotSfxUpload(shot: Pick<AivideoShot, 'shotId' | 'shotNo' | 'sfxCues'>) {
  triggerMediaRegisterUpload(buildShotSfxMediaContext(shot))
}

function promptShotSfxUrl(shot: Pick<AivideoShot, 'shotId' | 'shotNo' | 'sfxCues'>) {
  void promptRegisterMediaUrl(buildShotSfxMediaContext(shot))
}

function buildShotTtsMediaContext(shot?: AivideoShot): MediaRegisterContext | undefined {
  if (!shot) {
    ElMessage.warning('请先选择分镜')
    return undefined
  }
  const ttsStartMs = normalizeTtsMs(shotTtsStartSec.value * 1000, shot.ttsStartMs || 0, 0, shotDurationMs(shot))
  const ttsEndMs = normalizeTtsMs(shotTtsEndSec.value * 1000, shot.ttsEndMs || shotDurationMs(shot), ttsStartMs, shotDurationMs(shot))
  return {
    assetType: 'SHOT_TTS_AUDIO',
    bizType: 'SHOT',
    bizId: shot.shotId,
    title: `镜头 ${shot.shotNo || '-'} 配音`,
    accept: AUDIO_FILE_ACCEPT,
    promptText: shotTtsText.value.trim() || defaultShotTtsText(shot),
    paramsJson: JSON.stringify({
      speaker: shotTtsSpeaker.value.trim() || defaultShotTtsSpeaker(shot),
      voiceType: shotTtsVoiceType.value.trim() || defaultShotTtsVoiceType(shot),
      ttsStartMs,
      ttsEndMs
    }),
    comment: '手动补充镜头后期配音',
    successMessage: '本镜配音素材已登记并选中',
    afterRegistered: async (asset) => {
      shotTtsAudios.value = [
        asset,
        ...shotTtsAudios.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
      ].sort(compareMediaAssetDesc)
      await loadShotTtsAudios()
      await refreshShotMediaAssets()
      await loadDetail()
    }
  }
}

function triggerShotTtsUpload() {
  const context = buildShotTtsMediaContext(selectedShotForVideo.value)
  if (context) {
    triggerMediaRegisterUpload(context)
  }
}

function promptShotTtsUrl() {
  const context = buildShotTtsMediaContext(selectedShotForVideo.value)
  if (context) {
    void promptRegisterMediaUrl(context)
  }
}

function shouldUseSceneContinuityReference(scene: AivideoScene) {
  const text = [
    scene.sceneName,
    scene.sceneType,
    scene.timeDesc,
    scene.weather,
    scene.atmosphere,
    scene.visualFeatures,
    scene.promptText
  ].filter(Boolean).join(' ')
  return /同一|同一个|同条|同场景|同地点|同街道|延续|上一|相同|同款|同位置/.test(text)
}

function findRecommendedSceneReference(scene: AivideoScene) {
  if (!shouldUseSceneContinuityReference(scene)) {
    return undefined
  }
  const currentIndex = scenes.value.findIndex((item) => String(item.sceneId) === String(scene.sceneId))
  if (currentIndex <= 0) {
    return undefined
  }
  return scenes.value
    .slice(0, currentIndex)
    .reverse()
    .find((item) => !!item.lockedMediaId)
}

function sceneReferenceSummary(scene: AivideoScene) {
  const recommended = findRecommendedSceneReference(scene)
  if (recommended?.lockedMediaId) {
    return `建议参考：${recommended.sceneName || '上一已确认场景'} 的图 #${recommended.lockedMediaId}`
  }
  if (scene.lockedMediaId) {
    return `已确认图 #${scene.lockedMediaId}，可作为后续同场景参考`
  }
  return sceneReferenceOptions.value.length ? '生成时可选择已确认场景图作为参考' : '暂无已确认场景图可参考'
}

function characterReferenceSummary(character: AivideoCharacter) {
  if (character.lockedMediaId) {
    return `已确认角色图 #${character.lockedMediaId}；再生成时默认参考此图保持一致`
  }
  return characterReferenceOptions.value.length ? '生成时可选择已确认角色图作为外观参考' : '暂无已确认角色图可参考'
}

function defaultSceneReferenceMediaIds(scene: AivideoScene) {
  const recommended = findRecommendedSceneReference(scene)
  if (recommended?.lockedMediaId) {
    return [String(recommended.lockedMediaId)]
  }
  return scene.lockedMediaId ? [String(scene.lockedMediaId)] : []
}

function defaultCharacterReferenceMediaIds(character: AivideoCharacter) {
  return character.lockedMediaId ? [String(character.lockedMediaId)] : []
}

function parseShotCharacterTokens(value?: string) {
  if (!value) {
    return []
  }
  const text = String(value).trim()
  if (!text) {
    return []
  }
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => {
          if (item && typeof item === 'object') {
            return String(item.characterId || item.id || item.characterName || item.name || '')
          }
          return String(item)
        })
        .filter(Boolean)
    }
  } catch (_error) {
    // 兼容逗号、中文逗号或分号分隔的老数据。
  }
  return text.split(/[,，;；\s]+/).map((item) => item.trim()).filter(Boolean)
}

function normalizeCharacterTokenKey(value?: string | number) {
  return String(value || '').trim().replace(/[\s　]+/g, '')
}

function characterNameAliases(value?: string) {
  const text = String(value || '').trim()
  if (!text) {
    return []
  }
  const aliases = new Set<string>()
  const addAlias = (item?: string) => {
    const alias = normalizeCharacterTokenKey(item)
    if (alias) {
      aliases.add(alias)
    }
  }
  addAlias(text)
  addAlias(text.replace(/[（(][^）)]*[）)]/g, ''))
  const bracketPattern = /[（(]([^）)]+)[）)]/g
  let match = bracketPattern.exec(text)
  while (match) {
    addAlias(match[1])
    match = bracketPattern.exec(text)
  }
  return Array.from(aliases)
}

function resolveCharacterTokenToId(token?: string) {
  const normalized = normalizeCharacterTokenKey(token)
  if (!normalized) {
    return ''
  }
  const matched = characters.value.find((character) => {
    const characterId = String(character.characterId || '')
    if (characterId && characterId === normalized) {
      return true
    }
    return characterNameAliases(character.characterName).includes(normalized)
  })
  return matched?.characterId ? String(matched.characterId) : ''
}

function parseShotCharacterIds(value?: string) {
  const ids: string[] = []
  parseShotCharacterTokens(value).forEach((token) => {
    const id = resolveCharacterTokenToId(token)
    if (id && !ids.includes(id)) {
      ids.push(id)
    }
  })
  return ids
}

function unresolvedShotCharacterTokens(value?: string) {
  return parseShotCharacterTokens(value).filter((token) => !resolveCharacterTokenToId(token))
}

function shotRelationshipText(shot?: AivideoShot) {
  return [
    shot?.transitionBeforeType,
    shot?.transitionBeforeDesc,
    shot?.actionDesc,
    shot?.dialogue,
    shot?.voiceOver,
    shot?.promptText
  ].filter(Boolean).join(' ')
}

function isRelationshipActionText(text: string) {
  return /靠近|凑近|走向|看向|望向|旁边|身边|递给|递向|递出|交给|传给|拿给|接过|接住|收下|对话|同框|两人|三人|多人|一起/.test(text)
}

function resolveEffectiveShotCharacterIds(shot?: AivideoShot) {
  const ids = parseShotCharacterIds(shot?.characterIds)
  const text = shotRelationshipText(shot)
  if (!isRelationshipActionText(text)) {
    return ids
  }
  const result = [...ids]
  characters.value.forEach((character) => {
    const characterId = String(character.characterId || '')
    const characterName = String(character.characterName || '').trim()
    if (!characterId || !characterName || !text.includes(characterName)) {
      return
    }
    if (isOffscreenCharacterMention(text, characterName)) {
      return
    }
    if (!result.includes(characterId)) {
      result.push(characterId)
    }
  })
  return result
}

function autoAddedShotCharacters(shot?: AivideoShot) {
  const rawIds = new Set(parseShotCharacterIds(shot?.characterIds))
  const effectiveIds = resolveEffectiveShotCharacterIds(shot)
  return characters.value.filter((character) => {
    const characterId = String(character.characterId)
    return effectiveIds.includes(characterId) && !rawIds.has(characterId)
  })
}

function characterDisplayName(character: AivideoCharacter) {
  return character.characterName || `角色 ${character.characterId}`
}

function characterVoiceSummary(character: AivideoCharacter) {
  return character.voiceName || character.voiceType || '未配置声线'
}

function characterVoiceDetail(character: AivideoCharacter) {
  const details = [
    character.voiceMode ? characterVoiceModeOptions.find((item) => item.value === character.voiceMode)?.label || character.voiceMode : '',
    character.voiceReferenceMediaId ? `参考音频 #${character.voiceReferenceMediaId}` : '',
    character.voiceDesc
  ].filter(Boolean)
  return details.join(' / ') || '后期配音会使用默认音色，建议为主要角色配置声线资产'
}

function numberOrDefault(value: unknown, fallback = 1) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

function optionalMediaId(value: string) {
  const trimmed = String(value || '').trim()
  if (!trimmed) {
    return undefined
  }
  const numeric = Number(trimmed)
  return Number.isFinite(numeric) ? numeric : undefined
}

function openCharacterVoiceDialog(character: AivideoCharacter) {
  selectedCharacterForVoice.value = character
  characterVoiceForm.voiceMode = character.voiceMode || 'POST_TTS'
  characterVoiceForm.voiceType = character.voiceType || ''
  characterVoiceForm.voiceName = character.voiceName || ''
  characterVoiceForm.voiceDesc = character.voiceDesc || ''
  characterVoiceForm.voiceReferenceMediaId = character.voiceReferenceMediaId ? String(character.voiceReferenceMediaId) : ''
  characterVoiceForm.voiceSampleText = character.voiceSampleText || ''
  characterVoiceForm.voiceSpeedRatio = numberOrDefault(character.voiceSpeedRatio, 1)
  characterVoiceForm.voiceVolumeRatio = numberOrDefault(character.voiceVolumeRatio, 1)
  characterVoiceForm.voicePitchRatio = numberOrDefault(character.voicePitchRatio, 1)
  characterVoiceDialogVisible.value = true
}

async function handleSaveCharacterVoice() {
  const character = selectedCharacterForVoice.value
  if (!character || characterVoiceSaving.value) {
    return
  }
  characterVoiceSaving.value = true
  try {
    await updateAivideoCharacterVoice({
      projectId: projectId.value,
      characterId: character.characterId,
      voiceMode: characterVoiceForm.voiceMode,
      voiceType: characterVoiceForm.voiceType.trim() || undefined,
      voiceName: characterVoiceForm.voiceName.trim() || undefined,
      voiceDesc: characterVoiceForm.voiceDesc.trim() || undefined,
      voiceReferenceMediaId: optionalMediaId(characterVoiceForm.voiceReferenceMediaId),
      voiceSampleText: characterVoiceForm.voiceSampleText.trim() || undefined,
      voiceSpeedRatio: characterVoiceForm.voiceSpeedRatio,
      voiceVolumeRatio: characterVoiceForm.voiceVolumeRatio,
      voicePitchRatio: characterVoiceForm.voicePitchRatio
    })
    ElMessage.success('角色声线资产已保存')
    characterVoiceDialogVisible.value = false
    await loadDetail()
  } finally {
    characterVoiceSaving.value = false
  }
}

function findCharactersByTokens(tokens: string[]) {
  const tokenSet = new Set(tokens.map((item) => String(item || '').trim()).filter(Boolean))
  if (!tokenSet.size) {
    return []
  }
  return characters.value.filter((character) => {
    const characterId = String(character.characterId || '')
    const characterName = String(character.characterName || '').trim()
    return tokenSet.has(characterId) || (!!characterName && tokenSet.has(characterName))
  })
}

function shotOnscreenCharacters(shot?: AivideoShot) {
  return findCharactersByTokens(parseShotCharacterIds(shot?.characterIds))
}

function normalizeShotSpeechText(value?: string) {
  return String(value || '')
    .replace(/^\s*(对白|旁白|画外音|内心|心声|心理活动|想法)\s*[:：]/, '')
    .trim()
}

function isInternalSpeechText(value?: string) {
  return /心声|内心|心理|脑海|心里|默想|暗想|想起|闪过/.test(String(value || ''))
}

function defaultShotTtsText(shot?: AivideoShot) {
  const dialogue = normalizeShotSpeechText(shot?.dialogue)
  if (dialogue && !isInternalSpeechText(shot?.dialogue)) {
    return dialogue
  }
  const voiceOver = normalizeShotSpeechText(shot?.voiceOver)
  if (voiceOver && !isInternalSpeechText(shot?.voiceOver)) {
    return voiceOver
  }
  return ''
}

function shotDurationMs(shot?: AivideoShot) {
  const sec = Number(shot?.durationSec || params.defaultShotDuration || 5)
  return Math.max(1000, Math.round(sec * 1000))
}

function normalizeTtsMs(value: unknown, fallback: number, min: number, max: number) {
  const numeric = Number(value)
  const resolved = Number.isFinite(numeric) ? numeric : fallback
  return Math.max(min, Math.min(max, Math.round(resolved)))
}

function inferTtsSpeakerFromText(text?: string) {
  const firstLine = String(text || '').split(/\r?\n/, 1)[0]?.trim() || ''
  const zh = firstLine.indexOf('：')
  const en = firstLine.indexOf(':')
  const index = zh >= 0 && en >= 0 ? Math.min(zh, en) : Math.max(zh, en)
  if (index <= 0 || index > 32) {
    return ''
  }
  return firstLine.slice(0, index).trim()
}

function findCharacterByName(name?: string) {
  const normalized = String(name || '').trim()
  if (!normalized) {
    return undefined
  }
  return characters.value.find((character) => String(character.characterName || '').trim() === normalized)
}

function defaultShotTtsSpeaker(shot?: AivideoShot) {
  return String(shot?.ttsSpeaker || inferTtsSpeakerFromText(defaultShotTtsText(shot))).trim()
}

function defaultShotTtsVoiceType(shot?: AivideoShot) {
  const speaker = defaultShotTtsSpeaker(shot)
  const character = findCharacterByName(speaker)
  return String(shot?.ttsVoiceType || character?.voiceType || '').trim()
}

function defaultShotTtsStartMs(shot?: AivideoShot) {
  const durationMs = shotDurationMs(shot)
  return normalizeTtsMs(shot?.ttsStartMs, 0, 0, Math.max(0, durationMs - 1))
}

function defaultShotTtsEndMs(shot?: AivideoShot) {
  const durationMs = shotDurationMs(shot)
  const startMs = defaultShotTtsStartMs(shot)
  return normalizeTtsMs(shot?.ttsEndMs, durationMs, Math.min(durationMs, startMs + 1), durationMs)
}

function formatShotTtsRange(shot?: AivideoShot) {
  const start = (defaultShotTtsStartMs(shot) / 1000).toFixed(1)
  const end = (defaultShotTtsEndMs(shot) / 1000).toFixed(1)
  return `${start}s - ${end}s`
}

function initializeShotTtsDraft(shot?: AivideoShot) {
  const durationMs = shotDurationMs(shot)
  const startMs = defaultShotTtsStartMs(shot)
  const endMs = defaultShotTtsEndMs(shot)
  shotTtsSpeaker.value = defaultShotTtsSpeaker(shot)
  shotTtsVoiceType.value = defaultShotTtsVoiceType(shot)
  shotTtsStartSec.value = Number((startMs / 1000).toFixed(1))
  shotTtsEndSec.value = Number((Math.min(durationMs, Math.max(startMs + 1, endMs)) / 1000).toFixed(1))
}

function shotMentionedCharacters(shot?: AivideoShot) {
  const text = shotRelationshipText(shot)
  if (!text) {
    return []
  }
  return characters.value.filter((character) => {
    const name = String(character.characterName || '').trim()
    return !!name && text.includes(name)
  })
}

function textContainsAny(value: string, keywords: string[]) {
  return keywords.some((keyword) => keyword && value.includes(keyword))
}

function parseChineseOrArabicCount(value?: string) {
  const raw = String(value || '').trim()
  if (!raw) {
    return 0
  }
  if (/^\d+$/.test(raw)) {
    return Number(raw)
  }
  const map: Record<string, number> = {
    一: 1,
    二: 2,
    两: 2,
    三: 3,
    四: 4,
    五: 5,
    六: 6,
    七: 7,
    八: 8,
    九: 9,
    十: 10
  }
  return map[raw] || 0
}

function inferShotExpectedOnscreenCount(text: string) {
  let count = 0
  let reason = ''
  const update = (nextCount: number, nextReason: string) => {
    if (nextCount > count) {
      count = nextCount
      reason = nextReason
    }
  }
  Array.from(text.matchAll(/当前镜头在场角色\s*[:：]\s*(\d+|[一二两三四五六七八九十])\s*人/g)).forEach((match) => {
    update(parseChineseOrArabicCount(match[1]), `写明当前镜头在场角色 ${match[1]} 人`)
  })
  Array.from(text.matchAll(/其余\s*(\d+|[一二两三四五六七八九十])\s*人/g)).forEach((match) => {
    const remaining = parseChineseOrArabicCount(match[1])
    if (remaining > 0) {
      update(remaining + 1, `出现“其余${match[1]}人”，至少需要 ${remaining + 1} 个画内角色`)
    }
  })
  Array.from(text.matchAll(/(\d+|[一二两三四五六七八九十])\s*(人|个角色|名角色|位角色)/g)).forEach((match) => {
    update(parseChineseOrArabicCount(match[1]), `出现“${match[0]}”`)
  })
  if (textContainsAny(text, ['全员', '所有角色', '全部角色', '全体角色']) && characters.value.length) {
    update(characters.value.length, `出现“全员/所有角色”，按当前项目 ${characters.value.length} 个角色校验`)
  }
  return { count, reason }
}

function isOffscreenCharacterMention(text: string, characterName?: string) {
  const name = String(characterName || '').trim()
  if (!text || !name || !text.includes(name)) {
    return false
  }
  const offscreenKeywords = ['画外', '画外音', '旁白', '不出现', '不入画', '镜外', '离场', '离开', '退出画面', '退出画外', '只闻其声', '声音传来', '脑海', '心里', '内心独白', '单人反应', '只拍', '只露手', '只露肩', '只露背影', '特写裁切']
  let index = text.indexOf(name)
  while (index >= 0) {
    const context = text.slice(Math.max(0, index - 16), Math.min(text.length, index + name.length + 16))
    if (textContainsAny(context, offscreenKeywords)) {
      return true
    }
    index = text.indexOf(name, index + name.length)
  }
  return false
}

function allowsPreviousCharacterOutOfFrame(shot: AivideoShot | undefined, character: AivideoCharacter) {
  const text = shotRelationshipText(shot)
  const name = String(character.characterName || '').trim()
  if (!text) {
    return false
  }
  if (isOffscreenCharacterMention(text, name)) {
    return true
  }
  return false
}

function extractShotPositionRequirement(shot?: AivideoShot, onscreenCharacters: AivideoCharacter[] = []) {
  const text = shotRelationshipText(shot)
  const matches = [
    text.match(/画面站位[:：]\s*([^。；;\n]+)/),
    text.match(/屏幕站位[:：]\s*([^。；;\n]+)/),
    text.match(/当前镜头在场角色[:：]\s*([^。；;\n]+)/)
  ].map((match) => match?.[1]?.trim()).filter(Boolean) as string[]
  if (matches.length) {
    return Array.from(new Set(matches)).join('；')
  }
  if (onscreenCharacters.length >= 2) {
    return `多人镜头：画内必须同时保留 ${onscreenCharacters.map(characterDisplayName).join('、')}，保持人数、相对站位、朝向和距离关系清楚。`
  }
  if (onscreenCharacters.length === 1) {
    return `单人镜头：画内主体锁定为 ${characterDisplayName(onscreenCharacters[0])}，其他角色不得自动出现。`
  }
  return '未绑定画内角色；如果不是纯场景镜头，请先回资产分镜补齐角色。'
}

function sameSceneWithoutHardBreak(shot?: AivideoShot, previous?: AivideoShot) {
  if (!shot || !previous || String(shot.sceneId || '') !== String(previous.sceneId || '')) {
    return false
  }
  return !['SCENE_CUT', 'TIME_JUMP', 'MONTAGE'].includes(normalizedShotTransitionType(shot))
}

function buildShotScreenCharacterRule(shot?: AivideoShot): ShotScreenCharacterRule {
  const onscreenCharacters = shotOnscreenCharacters(shot)
  const onscreenIdSet = new Set(onscreenCharacters.map((item) => String(item.characterId)))
  const mentionedCharacters = shotMentionedCharacters(shot)
  const text = shotRelationshipText(shot)
  const expected = inferShotExpectedOnscreenCount(text)
  const offscreenCharacters = mentionedCharacters.filter((character) => {
    return !onscreenIdSet.has(String(character.characterId))
      && isOffscreenCharacterMention(text, character.characterName)
  })
  const missingCharacters = mentionedCharacters.filter((character) => {
    return !onscreenIdSet.has(String(character.characterId))
      && !offscreenCharacters.some((item) => String(item.characterId) === String(character.characterId))
  })
  const previous = findPreviousShot(shot)
  const previousOnscreenCharacters = shotOnscreenCharacters(previous)
  const previousMissingCharacters = sameSceneWithoutHardBreak(shot, previous)
    ? previousOnscreenCharacters.filter((character) => {
      return !onscreenIdSet.has(String(character.characterId))
        && !allowsPreviousCharacterOutOfFrame(shot, character)
    })
    : []
  return {
    onscreenCharacters,
    mentionedCharacters,
    missingCharacters,
    offscreenCharacters,
    previousMissingCharacters,
    expectedOnscreenCount: expected.count,
    expectedOnscreenCountReason: expected.reason,
    positionRequirement: extractShotPositionRequirement(shot, onscreenCharacters)
  }
}

function shotScreenCharacterRule(shot?: AivideoShot) {
  return buildShotScreenCharacterRule(shot)
}

function addShotReferenceOption(options: ReferenceImageOption[], option?: ReferenceImageOption) {
  if (!option?.mediaId || options.some((item) => String(item.mediaId) === String(option.mediaId))) {
    return
  }
  options.push(option)
}

function shouldUsePreviousVisualReferenceForShot(shot?: AivideoShot, previousShot?: AivideoShot) {
  return !!shot
    && !!previousShot
    && (shotRequiresPreviousVideo(shot) || shouldUsePreviousShotReferenceForInsertHandoff(shot, previousShot))
}

function shouldUsePreviousAudioReference(shot?: AivideoShot, previousShot?: AivideoShot) {
  return String(params.audioMode || '').toUpperCase() === 'REFERENCE_AUDIO'
    && shouldUsePreviousVisualReferenceForShot(shot, previousShot)
}

function shouldDisplayPreviousAudioReference(shot?: AivideoShot, previousShot?: AivideoShot) {
  return shouldUsePreviousVisualReferenceForShot(shot, previousShot)
}

function buildShotVideoAutoReferenceOptions(shot?: AivideoShot) {
  if (!shot) {
    return []
  }
  const options: ReferenceImageOption[] = []
  const previousShot = findPreviousShotForVideo(shot)
  if (shouldUsePreviousVisualReferenceForShot(shot, previousShot) && previousShot) {
    const previousTailFrameMediaId = shotTailFrameMediaId(previousShot)
    const previousVideoMediaId = shotVideoMediaId(previousShot)
    const previousAudioMediaId = shotAudioMediaId(previousShot)
    if (previousTailFrameMediaId) {
      addShotReferenceOption(options, {
        mediaId: String(previousTailFrameMediaId),
        label: `上一镜尾帧：第 ${previousShot.shotNo || ''} 镜 #${previousTailFrameMediaId}`,
        subtitle: '锁定本镜头开头的姿态、道具位置、光影和空间连续性',
        sourceName: '上一镜尾帧',
        mediaKind: 'image'
      })
    }
    if (shouldUsePreviousShotReferenceForInsertHandoff(shot, previousShot) && previousVideoMediaId) {
      addShotReferenceOption(options, {
        mediaId: String(previousVideoMediaId),
        label: `上一镜参考视频：第 ${previousShot.shotNo || ''} 镜 #${previousVideoMediaId}`,
        subtitle: '作为 reference_video 参考上一镜动作逻辑、运镜节奏和道具交接关系',
        sourceName: '上一镜参考视频',
        mediaKind: 'video'
      })
    }
    if (shouldDisplayPreviousAudioReference(shot, previousShot) && previousAudioMediaId) {
      const audioWillBeSent = shouldUsePreviousAudioReference(shot, previousShot)
      addShotReferenceOption(options, {
        mediaId: String(previousAudioMediaId),
        label: audioWillBeSent
          ? `上一镜参考音频：第 ${previousShot.shotNo || ''} 镜 #${previousAudioMediaId}`
          : `上一镜音频：第 ${previousShot.shotNo || ''} 镜 #${previousAudioMediaId}`,
        subtitle: audioWillBeSent
          ? '作为 reference_audio 继承音色、语速、口吻和环境声风格；不复读上一镜台词'
          : '当前声音模式不是参考音频，仅展示上一镜音频；如需继承声线，请切换为“参考音频有声”',
        sourceName: '上一镜参考音频',
        mediaKind: 'audio'
      })
    }
  }
  const scene = findSceneById(shot.sceneId)
  if (scene?.lockedMediaId) {
    addShotReferenceOption(options, {
      mediaId: String(scene.lockedMediaId),
      label: `场景：${scene.sceneName || '未命名场景'} #${scene.lockedMediaId}`,
      subtitle: [scene.timeDesc, scene.weather, scene.atmosphere, scene.visualFeatures].filter(Boolean).join(' / ') || '当前分镜场景图',
      sourceName: scene.sceneName || '当前分镜场景',
      mediaKind: 'image'
    })
  }
  autoShotRequiredProps(shot)
    .map(({ prop }) => prop)
    .filter((prop): prop is AivideoProp => !!prop?.lockedMediaId)
    .forEach((prop) => {
      addShotReferenceOption(options, {
        mediaId: String(prop.lockedMediaId),
        label: `道具：${prop.propName || '未命名道具'} #${prop.lockedMediaId}`,
        subtitle: propAppearanceSummary(prop),
        sourceName: prop.propName || '当前分镜道具',
        mediaKind: 'image'
      })
    })
  const characterIdSet = new Set(resolveEffectiveShotCharacterIds(shot))
  characters.value
    .filter((item) => item.lockedMediaId && characterIdSet.has(String(item.characterId)))
    .forEach((item) => {
      addShotReferenceOption(options, {
        mediaId: String(item.lockedMediaId),
        label: `角色：${item.characterName || '未命名角色'} #${item.lockedMediaId}`,
        subtitle: [item.storyRole, item.appearance].filter(Boolean).join(' / ') || '当前分镜角色图',
        sourceName: item.characterName || '当前分镜角色',
        mediaKind: 'image'
      })
    })
  return options
}

function shotManualReferenceMediaIds(shot?: AivideoShot) {
  if (!shot?.shotId) {
    return []
  }
  return shotManualReferenceMediaIdsByShotId.value[String(shot.shotId)] || []
}

type ShotReferenceAssetType = 'scene' | 'character' | 'prop'

function referenceOptionIdsByType(type: ShotReferenceAssetType) {
  const options = type === 'scene'
    ? sceneReferenceOptions.value
    : type === 'character'
      ? characterReferenceOptions.value
      : propReferenceOptions.value
  return new Set(options.map((item) => String(item.mediaId)))
}

function manualShotSceneReferenceIds(shot?: AivideoShot) {
  const sceneIds = referenceOptionIdsByType('scene')
  return shotManualReferenceMediaIds(shot).filter((id) => sceneIds.has(String(id)))
}

function manualShotCharacterReferenceIds(shot?: AivideoShot) {
  const characterIds = referenceOptionIdsByType('character')
  return shotManualReferenceMediaIds(shot).filter((id) => characterIds.has(String(id)))
}

function manualShotPropReferenceIds(shot?: AivideoShot) {
  const propIds = referenceOptionIdsByType('prop')
  return shotManualReferenceMediaIds(shot).filter((id) => propIds.has(String(id)))
}

function manualShotReferenceOptionByMediaId(mediaId: string) {
  const sceneOption = sceneReferenceOptions.value.find((item) => String(item.mediaId) === String(mediaId))
  if (sceneOption) {
    return {
      ...sceneOption,
      label: `追加场景：${sceneOption.sourceName} #${sceneOption.mediaId}`,
      removable: true
    } as ReferenceImageOption
  }
  const characterOption = characterReferenceOptions.value.find((item) => String(item.mediaId) === String(mediaId))
  if (characterOption) {
    return {
      ...characterOption,
      label: `追加角色：${characterOption.sourceName} #${characterOption.mediaId}`,
      removable: true
    } as ReferenceImageOption
  }
  const propOption = propReferenceOptions.value.find((item) => String(item.mediaId) === String(mediaId))
  if (propOption) {
    return {
      ...propOption,
      label: `追加道具：${propOption.sourceName} #${propOption.mediaId}`,
      removable: true
    } as ReferenceImageOption
  }
  return undefined
}

function isAutoShotReferenceMedia(shot?: AivideoShot, mediaId?: string | number) {
  if (!shot || mediaId == null || mediaId === '') {
    return false
  }
  return buildShotVideoAutoReferenceOptions(shot)
    .some((item) => String(item.mediaId) === String(mediaId))
}

function buildShotVideoReferenceOptions(shot?: AivideoShot) {
  const options = buildShotVideoAutoReferenceOptions(shot)
  const existing = new Set(options.map((item) => String(item.mediaId)))
  shotManualReferenceMediaIds(shot).forEach((mediaId) => {
    const option = manualShotReferenceOptionByMediaId(String(mediaId))
    if (!option || existing.has(String(option.mediaId))) {
      return
    }
    options.push(option)
    existing.add(String(option.mediaId))
  })
  return options
}

function currentShotVideoReferenceMediaIds() {
  return shotVideoReferenceOptions.value
    .filter((item) => (item.mediaKind || 'image') === 'image')
    .map((item) => item.mediaId)
}

function findPreviousShotForVideo(shot: AivideoShot) {
  const orderedShots = [...shots.value]
    .filter((item) => item.episodeNo === shot.episodeNo || item.episodeNo == null || shot.episodeNo == null)
    .sort((left, right) => Number(left.shotNo || 0) - Number(right.shotNo || 0))
  const currentIndex = orderedShots.findIndex((item) => String(item.shotId) === String(shot.shotId))
  return currentIndex > 0 ? orderedShots[currentIndex - 1] : undefined
}

function shouldUsePreviousShotReferenceForInsertHandoff(shot?: AivideoShot, previousShot?: AivideoShot) {
  if (!shot || !previousShot || String(shot.sceneId || '') !== String(previousShot.sceneId || '')) {
    return false
  }
  const transitionType = String(shot.transitionBeforeType || '').toUpperCase()
  const text = [
    shot.transitionBeforeType,
    shot.transitionBeforeDesc,
    shot.actionDesc,
    shot.dialogue,
    shot.voiceOver,
    shot.promptText
  ].filter(Boolean).join(' ')
  if (/不继承上一尾帧|不使用上一尾帧|禁止使用上一尾帧|不要使用上一尾帧|不继承上一镜尾帧|不使用上一镜尾帧/.test(text)) {
    return false
  }
  const insertLike = transitionType === 'INSERT' || /插入镜头|同场景切人|同场景道具交接/.test(text)
  const handoffLike = /承接上一镜|继承上一尾帧|上一镜道具|道具交接|接过|接住|收下|递给|递出|交给|传给/.test(text)
  const relationshipLike = isRelationshipActionText(text)
  return insertLike && (handoffLike || relationshipLike)
}

function resolveEffectiveCharacterDesignType() {
  const type = String(params.characterDesignType || 'AUTO')
  if (type !== 'AUTO') {
    return type
  }
  const style = String(params.defaultStyle || '')
  if (/Q版|萌系/.test(style)) {
    return 'CHIBI_FULL_BODY'
  }
  if (/3D|CG|国漫/.test(style)) {
    return 'THREE_D_ANIME_CG'
  }
  if (/2D|日漫|动漫/.test(style)) {
    return 'TWO_D_ANIME'
  }
  if (/绘本/.test(style)) {
    return 'CHILDREN_PICTURE_BOOK'
  }
  if (/写实|电影/.test(style)) {
    return 'REALISTIC_NATURAL'
  }
  return 'AUTO'
}

function characterDesignLabel(value?: string) {
  return labelOf(characterDesignTypeOptions, value, '自动')
}

function characterDesignPreflightRule(value?: string) {
  switch (value) {
    case 'CHIBI_FULL_BODY':
      return '请人工确认角色图是 Q版单主体完整全身，2.5-3.5 头身，猫耳/猫尾/手脚或标志物完整可见；不是头像、半身、贴纸、表情包、四视图/三视图/多视图。'
    case 'THREE_D_ANIME_CG':
      return '请人工确认角色图是 3D动漫/国漫CG 单主体完整全身，材质、服装、发型、眼睛和标志物稳定；不是真人照片、2D平面漫画、Q版大头、粘土玩具或多视图。'
    case 'TWO_D_ANIME':
      return '请人工确认角色图是 2D动漫/日漫单主体完整全身，线稿清晰、色块稳定、适合后续动作；不是3D渲染、真人照片、漫画分镜、对白气泡或多视图。'
    case 'REALISTIC_NATURAL':
      return '请人工确认角色图是写实自然比例单主体完整全身，骨骼、服装、毛发/皮肤可信；不是Q版、动漫化、玩偶化、网红写真、头像或半身。'
    case 'SEMI_REAL_CARTOON':
      return '请人工确认角色图是半写实卡通单主体完整全身，比例可动且不是Q版大头、贴纸、表情包、头像、半身或多视图。'
    case 'ANIMAL_BODY_CUTE':
      return '请人工确认动物角色图保持物种本体，四足/翅膀/尾巴/耳朵/爪子完整可见；不是人类身体、真人脸、直立人形或人类戏服。'
    case 'ANTHROPOMORPHIC':
      return '请人工确认拟人化角色图同时固定人形比例和物种标志物，例如猫耳、猫尾、翅膀或角；不能变成纯人类或丢失标志物。'
    case 'CHILDREN_PICTURE_BOOK':
      return '请人工确认角色图是低龄绘本单主体完整全身，轮廓柔和、色块清爽、低复杂度；不是成人写实、恐怖化、复杂背景或多视图。'
    case 'MONSTER_VILLAIN':
      return '请人工确认怪物/反派角色图完整全身、结构可动，锁定2-3个稳定标志特征；不是随机变形、断肢错肢、血腥或多视图。'
    default:
      return '请人工确认角色图是 Seedance 视频可用的单主体完整全身角色锚定图，背景纯白/浅灰/极简棚拍，不是头像、半身、四视图、三视图、多视图或同款分身。'
  }
}

const shotStrongActionKeywords = [
  '拔出', '拔剑', '出鞘', '挥剑', '挥砍', '斩', '刺向', '冲刺', '冲向', '跳起',
  '飞跃', '变身', '爆炸', '倒地', '起身', '悬浮', '坠落', '落水', '救援', '打斗',
  '搏斗', '俯冲', '释放', '施法', '掰弯', '击中', '劈中', '抽搐', '电流包裹', '飞起', '落下'
]
const shotMainActionKeywords = [
  '拿起', '抬起', '低头', '抬头', '转身', '转向', '看向', '靠近', '走向', '递给',
  '接过', '交给', '传给', '展示', '指向', '站起', '坐下', '放入', '拉开', '推开',
  '合上', '打开', '写', '读', '跑', '跳', '抽出', '举起', '握住', '挥动', '靠拢'
]
const shotReactionKeywords = ['嘴角', '微笑', '笑', '表情', '眼神', '露出', '发现', '惊讶', '困惑', '点头', '认可', '凝视', '深吸']
const shotEndStateKeywords = ['结尾', '最后', '停在', '定格', '保持', '站定', '结束', '收束']
const requiredPropKeywords = [
  '蓝色透明收纳盒', '寒光剑', '价格标签', '收纳盒', '试卷', '账本', '存折', '票据',
  '皮球', '钥匙', '手机', '书包', '笔记本', '铅笔', '卡片', '长剑', '光剑', '法杖',
  '魔杖', '盾牌', '盾', '弓箭', '弓', '匕首', '刀', '剑', '枪'
]

function normalizeShotDurationForBudget(durationSec?: number) {
  const duration = Number(durationSec || params.defaultShotDuration || 5)
  if (duration <= 5) {
    return 5
  }
  if (duration <= 6) {
    return 6
  }
  return 8
}

function shotBudgetFor(durationSec: number) {
  if (durationSec <= 5) {
    return { maxMain: 1, maxStrong: 1, maxCost: 2.25 }
  }
  if (durationSec <= 6) {
    return { maxMain: 2, maxStrong: 1, maxCost: 3.25 }
  }
  return { maxMain: 3, maxStrong: 2, maxCost: 4.25 }
}

function splitShotActionBeats(shot?: AivideoShot) {
  // 动作预算只看结构化动作；视频执行提示词会复述动作，混算会造成重复预警。
  const text = (shot?.actionDesc || shot?.promptText || '')
    .replace(/然后|接着|随后|同时|再/g, '，')
  const seen = new Set<string>()
  return text.split(/[，,；;。！？!?\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item) => {
      if (seen.has(item)) {
        return false
      }
      seen.add(item)
      return true
    })
}

function matchedKeywords(text: string, keywords: string[]) {
  return keywords.filter((keyword) => keyword && text.includes(keyword))
}

function inspectShotActionBudget(shot?: AivideoShot): ShotVideoPreflightItem | undefined {
  if (!shot) {
    return undefined
  }
  const duration = normalizeShotDurationForBudget(shot.durationSec)
  const budget = shotBudgetFor(duration)
  let cost = 0
  let mainActionCount = 0
  let strongActionCount = 0
  let reactionCount = 0
  const usedKeywords: string[] = []
  splitShotActionBeats(shot).forEach((beat) => {
    const strongMatches = matchedKeywords(beat, shotStrongActionKeywords)
    if (strongMatches.length) {
      cost += 2
      mainActionCount += 1
      strongActionCount += 1
      usedKeywords.push(...strongMatches)
      return
    }
    const isEndState = matchedKeywords(beat, shotEndStateKeywords).length > 0
    const mainMatches = matchedKeywords(beat, shotMainActionKeywords)
    if (mainMatches.length && !isEndState) {
      cost += 1
      mainActionCount += 1
      usedKeywords.push(...mainMatches)
      return
    }
    const reactionMatches = matchedKeywords(beat, shotReactionKeywords)
    if (reactionMatches.length) {
      cost += 0.25
      reactionCount += 1
      usedKeywords.push(...reactionMatches)
    }
  })
  const overBudget = mainActionCount > budget.maxMain || strongActionCount > budget.maxStrong || cost > budget.maxCost
  const keywordSummary = Array.from(new Set(usedKeywords)).slice(0, 8).join('、') || '未命中明显动作词'
  if (overBudget) {
    return {
      status: 'fail',
      title: `动作预算过载：${duration} 秒镜头`,
      detail: `当前约 ${mainActionCount} 个主动作、${strongActionCount} 个强动作、${reactionCount} 个反应，成本 ${cost.toFixed(2)}，命中动作词：${keywordSummary}。建议拆镜：镜头A保留起始状态+核心强动作，镜头B承接结果状态+反应/结尾状态。`
    }
  }
  return {
    status: 'pass',
    title: `动作预算通过：${duration} 秒镜头`,
    detail: `当前约 ${mainActionCount} 个主动作、${strongActionCount} 个强动作、${reactionCount} 个反应，处于稳定生成范围；命中动作词：${keywordSummary}。`
  }
}

function removeContainedPropNames(names: string[]) {
  return names.filter((name) => !names.some((other) => other.length > name.length && other.includes(name)))
}

function detectShotRequiredPropNames(shot?: AivideoShot) {
  const text = shotRelationshipText(shot)
  if (!text) {
    return []
  }
  const names: string[] = []
  requiredPropKeywords.forEach((keyword) => {
    if (!keyword || !text.includes(keyword)) {
      return
    }
    if (keyword === '剑' && (text.includes('寒光剑') || text.includes('长剑') || text.includes('光剑'))) {
      return
    }
    if (!names.includes(keyword)) {
      names.push(keyword)
    }
  })
  return removeContainedPropNames(names)
}

function findMatchedPropByName(requiredName: string) {
  return props.value.find((prop) => {
    const propName = String(prop.propName || '').trim()
    return !!propName && (propName.includes(requiredName) || requiredName.includes(propName))
  })
}

function requiresLockedPropReference(shot: AivideoShot | undefined, requiredName: string) {
  const text = shotRelationshipText(shot)
  return /剑|刀|枪|法杖|魔杖|盾|弓|匕首/.test(requiredName)
    || /拔出|拔剑|出鞘|持|握住|举起|挥动|指向|递给|接过|交给|传给|发光|亮起/.test(text)
}

function autoShotRequiredProps(shot?: AivideoShot) {
  return detectShotRequiredPropNames(shot)
    .map((name) => ({ name, prop: findMatchedPropByName(name) }))
}

function buildShotVideoPreflightItems(shot?: AivideoShot): ShotVideoPreflightItem[] {
  if (!shot) {
    return []
  }
  const items: ShotVideoPreflightItem[] = []
  const actionBudgetItem = inspectShotActionBudget(shot)
  if (actionBudgetItem) {
    items.push(actionBudgetItem)
  }
  autoShotRequiredProps(shot).forEach(({ name, prop }) => {
    if (!prop) {
      items.push({
        status: 'fail',
        title: `道具资产缺失：${name}`,
        detail: '当前分镜出现了武器、手持物、发光物、交接物或剧情推进物，但资产里没有同名道具。请重新提取资产或先补道具资产，否则视频里容易变色、变形或凭空消失。'
      })
      return
    }
    if (requiresLockedPropReference(shot, name) && !prop.lockedMediaId) {
      items.push({
        status: 'fail',
        title: `道具图未锁定：${prop.propName || name}`,
        detail: '该道具参与持有、拔出、指向、挥动、交接或发光动作，必须先生成并确认道具图；系统会在非上一尾帧首帧模式下自动作为道具锚点传入视频请求。'
      })
      return
    }
    items.push({
      status: prop.lockedMediaId ? 'pass' : 'warn',
      title: prop.lockedMediaId ? `道具图已锁定：${prop.propName || name} #${prop.lockedMediaId}` : `道具资产已关联：${prop.propName || name}`,
      detail: prop.lockedMediaId
        ? '系统会把已确认道具图作为颜色、材质、形状、尺寸和交接关系锚点，减少跨镜漂移。'
        : '当前道具已有资产，但暂未锁图；如果它不是关键手持/交接/发光道具可以继续，否则建议先补道具图。'
    })
  })
  const transitionType = normalizedShotTransitionType(shot)
  const previousShot = findPreviousShot(shot)
  items.push({
    status: transitionType === 'CONTINUE' ? 'pass' : 'warn',
    title: `镜头衔接：${shotTransitionLabel(shot)}`,
    detail: transitionType === 'CONTINUE'
      ? '当前分镜按连续镜头处理，视频生成会优先继承上一镜尾帧。'
      : `当前分镜是明确转场边界：${shotTransitionDesc(shot)}；后期拼接时应在 ${previousShot?.shotNo || '-'} -> ${shot.shotNo || '-'} 之间加转场。`
  })
  const scene = findSceneById(shot.sceneId)
  if (!scene) {
    items.push({
      status: 'fail',
      title: '场景绑定缺失',
      detail: '当前分镜没有找到对应场景，建议先回到资产阶段修正分镜场景。'
    })
  } else if (!scene.lockedMediaId) {
    items.push({
      status: 'fail',
      title: `场景图未选择：${scene.sceneName || '未命名场景'}`,
      detail: '视频生成前必须先为当前场景选择一张已确认场景图，否则空间、天气和光线锚点不稳定。'
    })
  } else {
    items.push({
      status: 'pass',
      title: `场景图已锁定：${scene.sceneName || '未命名场景'} #${scene.lockedMediaId}`,
      detail: '系统会把已确认场景图作为当前分镜空间、天气、光线和道具锚点，并作为视频请求参考图实际传入。'
    })
  }

  const onscreenCharacterIds = parseShotCharacterIds(shot.characterIds)
  const referenceCharacterIds = resolveEffectiveShotCharacterIds(shot)
  const unresolvedCharacterTokens = unresolvedShotCharacterTokens(shot.characterIds)
  const autoAddedCharacters = autoAddedShotCharacters(shot)
  const screenRule = buildShotScreenCharacterRule(shot)
  if (autoAddedCharacters.length) {
    items.push({
      status: 'pass',
      title: `关系参考角色：${autoAddedCharacters.map((item) => item.characterName || `角色${item.characterId}`).join('、')}`,
      detail: '动作、转场、台词或旁白中提到了该角色，系统会把对应角色图作为参考锚点传入；这不等于画内人物已绑定，如需入画仍必须在资产分镜中写入画内角色。'
    })
  }
  if (screenRule.onscreenCharacters.length) {
    items.push({
      status: 'pass',
      title: `画内人物已规定：${screenRule.onscreenCharacters.map(characterDisplayName).join('、')}`,
      detail: screenRule.positionRequirement
    })
  }
  if (screenRule.expectedOnscreenCount > 0
    && screenRule.onscreenCharacters.length < screenRule.expectedOnscreenCount) {
    items.push({
      status: 'fail',
      title: `画内角色绑定不足：需要 ${screenRule.expectedOnscreenCount} 人，当前 ${screenRule.onscreenCharacters.length} 人`,
      detail: `分镜文案${screenRule.expectedOnscreenCountReason}，但画内只绑定了 ${screenRule.onscreenCharacters.map(characterDisplayName).join('、') || '未绑定'}。请回资产分镜补齐全部角色名，禁止用“其余三人/全员/同伴”让模型自行补人。`
    })
  }
  if (screenRule.missingCharacters.length) {
    items.push({
      status: 'fail',
      title: `文案提到但未绑定画内角色：${screenRule.missingCharacters.map(characterDisplayName).join('、')}`,
      detail: '这些角色出现在动作、台词、旁白或提示词里，但没有写入本镜画内角色。临时追加参考素材只能提供外观锚点，不能替代画内人物确认；如果确实要入画，请回资产分镜补角色。'
    })
  }
  if (screenRule.previousMissingCharacters.length) {
    items.push({
      status: 'fail',
      title: `上一镜角色疑似无说明消失：${screenRule.previousMissingCharacters.map(characterDisplayName).join('、')}`,
      detail: '当前镜头与上一镜仍属同场景/非硬切，但上一镜画内角色未在本镜继续出现，也没有针对该角色写明离场、画外、单人反应或裁切说明；“同伴/两人/对方/画外同伴”不算有效说明，必须点名角色，例如“狗小汪在画外右侧近旁不入画”。'
    })
  }
  if (!onscreenCharacterIds.length) {
    items.push({
      status: 'warn',
      title: '分镜未绑定角色',
      detail: '如果这个镜头确实是纯场景可以继续；如果有角色动作，请先在资产分镜中绑定角色。'
    })
  }
  if (unresolvedCharacterTokens.length) {
    items.push({
      status: 'fail',
      title: `角色绑定异常：${Array.from(new Set(unresolvedCharacterTokens)).join('、')}`,
      detail: '分镜 characterIds/characterNames 中存在无法匹配到角色资产的名称或旧 ID。请重新提取资产，或在角色资产中补齐这些角色；如果只是职业/阵营/群演描述，不要写入分镜绑定角色。'
    })
  }
  if (referenceCharacterIds.length > 4) {
    items.push({
      status: 'fail',
      title: '参考角色超过 4 个',
      detail: '火山提示词指南中参考人物过多会降低稳定性，建议拆镜或按角色分组后再生成。'
    })
  }

  const characterIdSet = new Set(referenceCharacterIds)
  const shotCharacters = characters.value.filter((item) => characterIdSet.has(String(item.characterId)))
  referenceCharacterIds
    .filter((id) => !shotCharacters.some((item) => String(item.characterId) === id))
    .forEach((id) => {
      items.push({
        status: 'fail',
        title: `角色不存在：${id}`,
        detail: '分镜绑定了不存在的角色ID，请重新提取或修正资产。'
      })
    })
  shotCharacters.forEach((character) => {
    if (!character.lockedMediaId) {
      items.push({
        status: 'fail',
        title: `角色图未选择：${character.characterName || '未命名角色'}`,
        detail: '视频生成前建议先为出场角色选择一张已确认角色图，否则容易换脸、换服装、换体型或丢失标志物。'
      })
    } else {
      items.push({
        status: 'pass',
        title: `角色图已锁定：${character.characterName || '未命名角色'} #${character.lockedMediaId}`,
        detail: '系统会把已确认角色图作为当前分镜角色锚点实际传入视频请求，锁定身份、外观、服装/毛色和标志物。'
      })
    }
  })

  const effectiveType = resolveEffectiveCharacterDesignType()
  const inferred = params.characterDesignType === 'AUTO' && effectiveType !== 'AUTO'
  items.push({
    status: 'warn',
    title: `角色造型人工复核：${characterDesignLabel(effectiveType)}${inferred ? '（由当前视觉风格自动推断）' : ''}`,
    detail: characterDesignPreflightRule(effectiveType)
  })

  const referenceCount = currentShotVideoReferenceMediaIds().length
  const hasReferenceVideo = shotVideoReferenceOptions.value.some((item) => item.mediaKind === 'video')
  const hasReferenceAudio = shotVideoReferenceOptions.value.some((item) => item.mediaKind === 'audio')
  const manualReferenceCount = shotManualReferenceMediaIds(shot).length
  if (referenceCount > 5) {
    items.push({
      status: 'warn',
      title: `参考图数量偏多：${referenceCount} 张`,
      detail: '火山指南建议不要堆满素材，过多参考图会造成主体识别和风格优先级冲突；建议保留关键角色图1-2张和场景图1张。'
    })
  } else if (referenceCount > 0) {
    items.push({
      status: 'pass',
      title: `参考图数量：${referenceCount} 张`,
      detail: '当前参考图数量处于可控范围；如果出现风格冲突，优先减少非必要参考图。'
    })
  }
  if (manualReferenceCount > 0) {
    items.push({
      status: 'pass',
      title: `手动追加参考素材：${manualReferenceCount} 张`,
      detail: '这些素材只作用于当前分镜本次视频生成；自动场景/角色/上一镜锚点仍保持优先。'
    })
  }
  if (hasReferenceVideo) {
    items.push({
      status: 'pass',
      title: '上一镜参考视频：已纳入',
      detail: '当前分镜会由后端自动传入上一镜已选视频作为 reference_video，用于承接动作逻辑、运镜节奏和空间关系。'
    })
  }
  if (String(params.audioMode || '').toUpperCase() === 'REFERENCE_AUDIO') {
    if (!previousShot) {
      items.push({
        status: 'warn',
        title: '参考音频：首镜种子音频',
        detail: '当前分镜没有上一镜；后端允许本镜生成第一段种子音频，确认后会提取为后续分镜 reference_audio。'
      })
    } else if (shouldUsePreviousAudioReference(shot, previousShot)) {
      items.push({
        status: hasReferenceAudio ? 'pass' : 'warn',
        title: hasReferenceAudio ? '上一镜参考音频：已纳入' : '上一镜参考音频：未就绪',
        detail: hasReferenceAudio
          ? '当前分镜会继承上一镜音色、语速、口吻和环境声风格，但仍按当前台词生成新声音。'
          : '当前声音模式需要上一镜提取音频；如果上一镜视频已选但音频未归档，本次后端会强制不让模型随机发声。'
      })
    } else {
      items.push({
        status: 'pass',
        title: '参考音频：当前镜头不继承',
        detail: '当前分镜是开场、切场或非连续关系，音频可以作为新的声音边界，不强行继承上一镜。'
      })
    }
  }
  return items
}

function preflightTagType(status: ShotVideoPreflightItem['status']) {
  if (status === 'pass') {
    return 'success'
  }
  if (status === 'fail') {
    return 'danger'
  }
  return 'warning'
}

function preflightStatusText(status: ShotVideoPreflightItem['status']) {
  if (status === 'pass') {
    return '通过'
  }
  if (status === 'fail') {
    return '不合格'
  }
  return '复核'
}

function escapeHtml(value: string) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function confirmShotVideoPreflight() {
  const items = shotVideoPreflightItems.value
  const riskyItems = items.filter((item) => item.status !== 'pass')
  if (!riskyItems.length) {
    return true
  }
  const hasFail = riskyItems.some((item) => item.status === 'fail')
  const html = `
    <div class="shot-video-preflight-dialog">
      <p>${hasFail ? '存在不合格项，建议先重新生成或补齐参考图。' : '系统检查通过基础锚点，但仍需要人工复核图片是否符合火山视频生成规则。'}</p>
      <ul>
        ${riskyItems.map((item) => `
          <li>
            <strong>[${escapeHtml(preflightStatusText(item.status))}] ${escapeHtml(item.title)}</strong>
            <span>${escapeHtml(item.detail)}</span>
          </li>
        `).join('')}
      </ul>
    </div>
  `
  try {
    await ElMessageBox.confirm(html, '参考图规则预检', {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '继续生成',
      cancelButtonText: hasFail ? '先去补图/重生' : '先检查图片',
      type: hasFail ? 'warning' : 'info',
      distinguishCancelAndClose: true
    })
    return true
  } catch {
    return false
  }
}

async function handleOptimizeShotScriptFromPreflight() {
  const shot = selectedShotForVideo.value
  if (!shot || shotScriptOptimizing.value) {
    return
  }
  const failures = shotVideoFailedPreflightItems.value.map((item) => `${item.title}：${item.detail}`)
  if (!failures.length) {
    ElMessage.info('当前没有需要优化的不合格项')
    return
  }
  const failureHtml = failures.map((item) => `<li>${escapeHtml(item)}</li>`).join('')
  let customPrompt = ''
  try {
    const result = await ElMessageBox.prompt(
      `
        <div class="shot-video-preflight-dialog">
          <p>系统会根据以下不合格项优化当前分镜脚本，保存后会自动重新评估：</p>
          <ul>${failureHtml}</ul>
          <p>也可以补充本次优化要求，例如“保持单人镜头，只补画外/裁切说明”。</p>
        </div>
      `,
      '优化分镜脚本',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '开始优化',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '补充你希望大模型遵守的优化要求，可不填',
        inputValue: '',
        distinguishCancelAndClose: true
      }
    )
    customPrompt = String(result.value || '').trim()
  } catch {
    return
  }
  shotScriptOptimizing.value = true
  try {
    const response = await optimizeAivideoShotScript({
      projectId: projectId.value,
      shotId: shot.shotId,
      customPrompt,
      preflightFailures: failures
    })
    if (response.data?.shot) {
      mergeOptimizedShot(response.data.shot)
    }
    await loadDetailInternal(true)
    selectedShotForVideo.value = shots.value.find((item) => String(item.shotId) === String(shot.shotId))
      || selectedShotForVideo.value
    await refreshShotVideoPromptPreview()
    ElMessage.success('分镜脚本已优化，请复核新的预检结果')
  } catch (error: any) {
    ElMessage.error(error?.message || '分镜脚本优化失败')
  } finally {
    shotScriptOptimizing.value = false
  }
}

function mergeOptimizedShot(shot: AivideoShot) {
  const list = detail.shots || []
  const index = list.findIndex((item) => String(item.shotId) === String(shot.shotId))
  if (index >= 0) {
    list[index] = { ...list[index], ...shot }
  }
  if (selectedShotForVideo.value && String(selectedShotForVideo.value.shotId) === String(shot.shotId)) {
    selectedShotForVideo.value = { ...selectedShotForVideo.value, ...shot }
  }
}

function imageReferencePrompt(scope: PromptScope) {
  const options = scope === 'characterImage'
    ? characterSelectedReferenceOptions.value
    : scope === 'sceneImage'
      ? sceneSelectedReferenceOptions.value
      : []
  if (!options.length) {
    return ''
  }
  const references = options.map((item, index) => `图片${index + 1}：${item.sourceName}（已确认媒体 #${item.mediaId}）`).join('\n')
  if (scope === 'characterImage') {
    return `【角色参考图】\n${references}\n请严格按图片顺序使用参考图：图片1为最重要角色身份锚点，优先继承物种/脸型/体型/毛色/服装/标志性细节；后续图片只补充局部细节。禁止生成多个主体，禁止改成其他角色，最终仍必须输出单主体视频角色锚定图。`
  }
  if (scope === 'sceneImage') {
    return `【场景参考图】\n${references}\n请严格按图片顺序使用参考图：图片1为最重要场景锚点，优先继承空间结构、镜头位置、核心道具、建筑关系和色调；后续图片只补充天气、时间、光线或局部细节。禁止替换为无关场景，最终必须保持单镜头纯场景首帧参考图。`
  }
  return ''
}

function externalImageReferencePrompt(scope: PromptScope) {
  const urls = scope === 'characterImage'
    ? characterReferenceImageUrls.value
    : scope === 'sceneImage'
      ? sceneReferenceImageUrls.value
      : []
  if (!urls.length) {
    return ''
  }
  const references = urls.map((url, index) => `外部图片${index + 1}：${url}`).join('\n')
  return `【外部参考图】\n${references}\n这些图片与已确认参考图一起作为本次生成参考；不得把外部参考图当成新剧情，只继承所需的外观、空间、材质或色调锚点。`
}

function scopedCustomPrompt(scope: PromptScope) {
  return [stageStrategyPrompt(scope), imageReferencePrompt(scope), externalImageReferencePrompt(scope), promptScopes.global, promptScopes[scope]]
    .map((item) => item.trim())
    .filter(Boolean)
    .join('\n\n')
}

async function refreshShotMediaAssets(silentError = false) {
  try {
    const res = await listAivideoMedia({
      projectId: projectId.value,
      bizType: 'SHOT'
    }, { silentError })
    shotMediaAssets.value = (res.data || []).filter((item) => String(item.bizType || '').toUpperCase() === 'SHOT')
  } catch (_error) {
    shotMediaAssets.value = []
  }
}

async function loadProjectEditPreflight() {
  try {
    const res = await getAivideoProjectEditPreflight(projectId.value)
    projectEditPreflight.value = res.data
  } catch (_error) {
    projectEditPreflight.value = {
      ready: false,
      clipCount: 0,
      missingShotCount: 0,
      totalDurationSec: 0,
      audioTrackCount: 0,
      clips: [],
      warnings: [],
      errors: ['整片剪辑预检接口请求失败，请稍后刷新']
    }
  }
}

async function loadProjectEditTasks() {
  try {
    const res = await listAivideoProjectEditTasks(projectId.value)
    projectEditTasks.value = res.data || []
    scheduleProjectEditPolling()
  } catch (_error) {
    projectEditTasks.value = []
  }
}

async function loadProjectEditVideos(silentError = false) {
  try {
    const res = await listAivideoMedia({
      projectId: projectId.value,
      assetType: 'PROJECT_EDIT_VIDEO',
      bizType: 'PROJECT',
      bizId: projectId.value
    }, { silentError })
    projectEditVideos.value = res.data || []
  } catch (_error) {
    projectEditVideos.value = []
  }
}

async function refreshProjectEditPanelWithOptions(silentError = false) {
  projectEditLoading.value = true
  try {
    await Promise.all([
      loadProjectEditPreflight(),
      loadProjectEditTasks(),
      loadProjectEditVideos(silentError)
    ])
  } finally {
    projectEditLoading.value = false
  }
}

async function refreshProjectEditPanel() {
  await refreshProjectEditPanelWithOptions(false)
}

function clearProjectEditPollingTimer() {
  if (projectEditPollTimer) {
    clearTimeout(projectEditPollTimer)
    projectEditPollTimer = undefined
  }
}

function scheduleProjectEditPolling(delay = PROJECT_EDIT_POLL_INTERVAL) {
  clearProjectEditPollingTimer()
  const running = projectEditTasks.value.find(isShotVideoTaskInFlight)
  if (!running || projectEditGenerating.value) {
    return
  }
  projectEditPollTimer = setTimeout(() => {
    void pollProjectEditTask(running.taskId)
  }, delay)
}

function upsertProjectEditTask(task?: AivideoTask) {
  if (!task?.taskId) {
    return
  }
  const key = String(task.taskId)
  const next = projectEditTasks.value.filter((item) => String(item.taskId) !== key)
  projectEditTasks.value = [task, ...next]
}

async function pollProjectEditTask(taskId: string | number) {
  try {
    const res = await pollAivideoProjectEditTask(projectId.value, taskId)
    if (res.data) {
      upsertProjectEditTask(res.data)
      if (isShotVideoTaskInFlight(res.data)) {
        scheduleProjectEditPolling()
        return
      }
    }
    await refreshProjectEditPanel()
  } catch (_error) {
    scheduleProjectEditPolling(PROJECT_EDIT_POLL_INTERVAL * 2)
  }
}

function projectEditPlayableUrl(asset: AivideoMediaAsset) {
  return resolveShotVideoDirectUrl(asset)
}

function projectEditVodId(asset: AivideoMediaAsset) {
  const url = String(asset.fileUrl || '')
  return url.startsWith('vod://') ? url.slice('vod://'.length) : ''
}

function formatTimeline(ms?: number) {
  const value = Number(ms || 0)
  if (Number.isNaN(value)) {
    return '0.0s'
  }
  return `${(value / 1000).toFixed(1)}s`
}

async function handleGenerateProjectEdit() {
  if (projectEditGenerating.value || hasRunningProjectEditTask.value) {
    return
  }
  await loadProjectEditPreflight()
  if (!projectEditReady.value) {
    ElMessage.warning(projectEditErrors.value[0] || '整片剪辑预检未通过，请先补齐已选分镜视频')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将按 ${projectEditPreflight.value?.clipCount || 0} 个已选分镜视频合成为一个整片成片，总时长约 ${projectEditPreflight.value?.totalDurationSec || 0} 秒。是否继续？`,
      '生成整片成片',
      { type: 'warning', confirmButtonText: '生成整片成片', cancelButtonText: '取消' }
    )
  } catch (_error) {
    return
  }
  projectEditGenerating.value = true
  try {
    const res = await generateAivideoProjectEdit({
      projectId: projectId.value,
      videoName: `${detail.project?.projectName || 'AI短剧'} 成片`,
      includeAudio: params.audioMode !== 'SILENT'
    })
    if (res.data) {
      upsertProjectEditTask(res.data)
      scheduleProjectEditPolling(1000)
    }
    ElMessage.success('整片剪辑合成任务已提交')
    await refreshProjectEditPanel()
  } catch (error: any) {
    ElMessage.error(error?.message || '整片成片生成失败')
  } finally {
    projectEditGenerating.value = false
    scheduleProjectEditPolling()
  }
}

async function loadDetailInternal(silentError = false) {
  if (!ensureProjectContext()) {
    return
  }
  loading.value = true
  try {
    const res = await getAivideoProject(projectId.value, { silentError })
    Object.assign(detail, res.data || {})
    const settingParams = parseParamsJson(res.data.setting?.paramsJson)
    if (!paramsEditing.value) {
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
        multiRoleStrategy: strategyValue(settingParams, 'multiRoleStrategy', 'SINGLE_FIRST'),
        characterDesignType: strategyValue(settingParams, 'characterDesignType', 'AUTO')
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
    }
    await refreshShotMediaAssets(silentError)
    await refreshProjectEditPanelWithOptions(silentError)
    await refreshPolishPromptPreview()
    await refreshScriptPromptPreview()
    await refreshAssetPromptPreview()
  } catch (error: any) {
    if (shouldRedirectForInvalidProject(error)) {
      redirectToProjectList(error?.message || '项目不存在或链接已失效')
      return
    }
    throw error
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  await loadDetailInternal(false)
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
      defaultStyle: params.defaultStyle,
      referenceImageUrls: sceneReferenceImageUrls.value,
      referenceMediaIds: sceneReferenceMediaIds.value,
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
      defaultStyle: params.defaultStyle,
      characterDesignType: params.characterDesignType,
      referenceImageUrls: characterReferenceImageUrls.value,
      referenceMediaIds: characterReferenceMediaIds.value,
      customPrompt: scopedCustomPrompt('characterImage')
    })
    characterImagePromptPreviewText.value = res.data?.effectivePrompt || res.data?.userPrompt || ''
  } catch (_error) {
    characterImagePromptPreviewText.value = ''
  }
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
      defaultStyle: params.defaultStyle,
      generationStrategy: params.generationStrategy,
      audioMode: params.audioMode,
      subtitleMode: params.subtitleMode,
      referenceStrategy: params.referenceStrategy,
      actionIntensity: params.actionIntensity,
      continuityLevel: params.continuityLevel,
      multiRoleStrategy: params.multiRoleStrategy,
      characterDesignType: params.characterDesignType,
      referenceMediaIds: currentShotVideoReferenceMediaIds()
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

function normalizeReferenceMediaSelection(ids: Array<string | number>) {
  const unique = Array.from(new Set((ids || []).map((id) => String(id)).filter(Boolean)))
  if (unique.length > 9) {
    ElMessage.warning('参考图最多选择 9 张，已自动保留前 9 张')
    return unique.slice(0, 9)
  }
  return unique
}

async function updateManualShotReferenceIds(shot: AivideoShot | undefined, type: ShotReferenceAssetType, ids: Array<string | number>) {
  if (!shot?.shotId) {
    return
  }
  const shotId = String(shot.shotId)
  const autoIds = new Set(buildShotVideoAutoReferenceOptions(shot).map((item) => String(item.mediaId)))
  const typeIds = referenceOptionIdsByType(type)
  const currentIds = shotManualReferenceMediaIds(shot)
  const preservedIds = currentIds.filter((id) => !typeIds.has(String(id)))
  const selectedIds = normalizeReferenceMediaSelection(ids)
    .filter((id) => typeIds.has(String(id)) && !autoIds.has(String(id)))
  const nextIds = normalizeReferenceMediaSelection([...preservedIds, ...selectedIds])
  shotManualReferenceMediaIdsByShotId.value = {
    ...shotManualReferenceMediaIdsByShotId.value,
    [shotId]: nextIds
  }
  await loadReferencePreviewUrls(shotVideoReferenceOptions.value)
  await refreshShotVideoPromptPreview()
}

async function handleManualShotSceneReferenceChange(shot: AivideoShot | undefined, ids: Array<string | number>) {
  await updateManualShotReferenceIds(shot, 'scene', ids)
}

async function handleManualShotCharacterReferenceChange(shot: AivideoShot | undefined, ids: Array<string | number>) {
  await updateManualShotReferenceIds(shot, 'character', ids)
}

async function handleManualShotPropReferenceChange(shot: AivideoShot | undefined, ids: Array<string | number>) {
  await updateManualShotReferenceIds(shot, 'prop', ids)
}

async function removeManualShotReference(shot: AivideoShot | undefined, mediaId: string | number) {
  if (!shot?.shotId) {
    return
  }
  const shotId = String(shot.shotId)
  const nextIds = shotManualReferenceMediaIds(shot).filter((id) => String(id) !== String(mediaId))
  shotManualReferenceMediaIdsByShotId.value = {
    ...shotManualReferenceMediaIdsByShotId.value,
    [shotId]: nextIds
  }
  await loadReferencePreviewUrls(shotVideoReferenceOptions.value)
  await refreshShotVideoPromptPreview()
}

async function loadReferencePreviewUrl(mediaId: string | number) {
  const key = String(mediaId)
  if (!key || referencePreviewUrls.value[key]) {
    return
  }
  try {
    const response = await previewAivideoMedia(key)
    const blob = (response as any).data as Blob
    if (!(blob instanceof Blob) || blob.size === 0) {
      return
    }
    referencePreviewUrls.value = {
      ...referencePreviewUrls.value,
      [key]: URL.createObjectURL(blob)
    }
  } catch (_error) {
    // 缩略图失败不阻断生成；卡片会保留占位。
  }
}

async function loadReferencePreviewUrls(options: ReferenceImageOption[]) {
  await Promise.all(options.map((item) => loadReferencePreviewUrl(item.mediaId)))
}

function revokeReferencePreviewUrls() {
  Object.values(referencePreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  referencePreviewUrls.value = {}
}

async function handleSceneReferenceChange(ids: Array<string | number>) {
  sceneReferenceMediaIds.value = normalizeReferenceMediaSelection(ids)
  await loadReferencePreviewUrls(sceneSelectedReferenceOptions.value)
  await refreshSceneImagePromptPreview()
}

async function handleCharacterReferenceChange(ids: Array<string | number>) {
  characterReferenceMediaIds.value = normalizeReferenceMediaSelection(ids)
  await loadReferencePreviewUrls(characterSelectedReferenceOptions.value)
  await refreshCharacterImagePromptPreview()
}

async function openCharacterImageDrawer(character: AivideoCharacter) {
  selectedCharacterForImage.value = character
  activePromptScope.value = 'characterImage'
  characterReferenceMediaIds.value = defaultCharacterReferenceMediaIds(character)
  characterReferenceImageUrlInput.value = ''
  characterReferenceImageUrls.value = []
  characterImageDrawerVisible.value = true
  await loadReferencePreviewUrls(characterReferenceOptions.value)
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
  sceneReferenceMediaIds.value = defaultSceneReferenceMediaIds(scene)
  sceneReferenceImageUrlInput.value = ''
  sceneReferenceImageUrls.value = []
  sceneImageDrawerVisible.value = true
  await loadReferencePreviewUrls(sceneReferenceOptions.value)
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
  shotTtsText.value = defaultShotTtsText(shot)
  initializeShotTtsDraft(shot)
  const shotId = String(shot.shotId)
  shotVideoLoading.value = true
  try {
    await Promise.all([
      loadReferencePreviewUrls([
        ...shotVideoReferenceOptions.value,
        ...shotVideoAddableReferenceOptions.value
      ]),
      refreshShotVideoPromptPreview(),
      loadShotVideoTasks(),
      loadShotTtsAudios(),
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
  shotTtsAudios.value = []
  shotTtsText.value = ''
  shotTtsSpeaker.value = ''
  shotTtsVoiceType.value = ''
  shotTtsStartSec.value = 0
  shotTtsEndSec.value = 0
  shotTtsSelectingIds.value = new Set()
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

async function loadShotTtsAudios() {
  const shot = selectedShotForVideo.value
  if (!shot) {
    shotTtsAudios.value = []
    return
  }
  const shotId = String(shot.shotId)
  const res = await listAivideoMedia({
    projectId: projectId.value,
    assetType: 'SHOT_TTS_AUDIO',
    bizType: 'SHOT',
    bizId: shot.shotId
  })
  if (!isCurrentShotVideoTarget(shotId)) {
    return
  }
  const candidates = (res.data || [])
    .filter((item) => isShotVideoAssetForShot(item, shotId))
    .sort(compareMediaAssetDesc)
  shotTtsAudios.value = candidates
  await Promise.all(candidates.map((item) => loadReferencePreviewUrl(item.mediaId)))
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
        defaultStyle: params.defaultStyle,
        generationStrategy: params.generationStrategy,
        audioMode: params.audioMode,
        subtitleMode: params.subtitleMode,
        referenceStrategy: params.referenceStrategy,
        actionIntensity: params.actionIntensity,
        continuityLevel: params.continuityLevel,
        multiRoleStrategy: params.multiRoleStrategy,
        characterDesignType: params.characterDesignType,
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
    let refreshFailed = false
    try {
      await loadDetailInternal(true)
    } catch (error) {
      refreshFailed = true
      console.error('操作成功后刷新项目详情失败', error)
    }
    ElMessage.success(successMessage)
    if (refreshFailed) {
      ElMessage.warning('操作已生效，但页面刷新失败，请点击右上角刷新')
    }
  } finally {
    submitting.value = false
  }
}

function clampInteger(value: number | undefined, min: number, max: number, fallback: number) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return fallback
  }
  return Math.min(max, Math.max(min, Math.round(numeric)))
}

function captureProjectParams() {
  return {
    params: { ...params },
    promptScopes: { ...promptScopes }
  }
}

function normalizeProjectParamsForSave() {
  params.defaultShotDuration = clampInteger(params.defaultShotDuration, 5, 8, 5)
  params.imageCandidateCount = clampInteger(params.imageCandidateCount, 1, 4, 1)
  params.videoCandidateCount = clampInteger(params.videoCandidateCount, 1, 3, 1)
}

function resetSourceDraft() {
  sourceDraftDocumentId.value = undefined
  sourceDraftHydratedDocumentKey.value = ''
  sourceDraft.fileName = ''
  sourceDraft.rawText = ''
  sourceDraft.sourceType = 'TEXT'
}

function hydrateSourceDraftFromDocument(doc?: AivideoSourceDocument) {
  if (!doc || doc.confirmed === '1') {
    sourceDraftDocumentId.value = undefined
    sourceDraftHydratedDocumentKey.value = ''
    return
  }
  const documentKey = String(doc.documentId)
  if (sourceDraftHydratedDocumentKey.value === documentKey) {
    return
  }
  sourceDraftDocumentId.value = doc.documentId
  sourceDraftHydratedDocumentKey.value = documentKey
  sourceDraft.sourceType = doc.sourceType || 'TEXT'
  sourceDraft.fileName = doc.fileName || ''
  sourceDraft.rawText = doc.parsedText || doc.rawText || ''
}

function handleEditProjectParams() {
  if (savingStrategies.value) {
    return
  }
  paramsSnapshot.value = captureProjectParams()
  paramsEditing.value = true
}

function handleCancelProjectParams() {
  if (savingStrategies.value) {
    return
  }
  const snapshot = paramsSnapshot.value
  if (snapshot) {
    Object.assign(params, snapshot.params)
    Object.assign(promptScopes, snapshot.promptScopes)
  }
  paramsEditing.value = false
  paramsSnapshot.value = null
}

async function handleSaveProjectStrategies() {
  const project = detail.project
  if (!project || savingStrategies.value) {
    return
  }
  normalizeProjectParamsForSave()
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
      characterDesignType: params.characterDesignType,
      globalPrompt: promptScopes.global,
      polishPrompt: promptScopes.polish,
      scriptPrompt: promptScopes.script,
      assetPrompt: promptScopes.asset,
      characterImagePrompt: promptScopes.characterImage,
      sceneImagePrompt: promptScopes.sceneImage,
      shotVideoPrompt: promptScopes.shotVideo,
      defaultShotDuration: params.defaultShotDuration,
      candidateImageCount: params.imageCandidateCount,
      videoCandidateCount: params.videoCandidateCount,
      previewMode: params.previewMode,
      budgetLimit: project.budgetLimit,
      summary: project.summary
    })
    paramsEditing.value = false
    paramsSnapshot.value = null
    ElMessage.success('项目参数已保存，后续生成会按当前配置发送')
    await loadDetail()
  } finally {
    savingStrategies.value = false
  }
}

function handleConfirmDocument() {
  if (!ensureProjectContext('当前项目无效，无法确认原文')) {
    return
  }
  const doc = latestDocument.value
  if (!doc) return
  if (sourceDraftDocumentId.value && String(sourceDraftDocumentId.value) === String(doc.documentId) && hasSourceDraftText.value) {
    handleSaveDocument(true)
    return
  }
  withSubmit(
    () => confirmAivideoDocument({
      projectId: projectId.value,
      documentId: doc.documentId,
      parsedText: doc.parsedText || doc.rawText
    }).then(() => undefined),
    '原文已确认'
  )
}

function handleCancelConfirmDocument() {
  if (!ensureProjectContext('当前项目无效，无法取消确认原文')) {
    return
  }
  const doc = latestDocument.value
  if (!doc) return
  withSubmit(
    () => cancelConfirmAivideoDocument({
      projectId: projectId.value,
      documentId: doc.documentId,
      comment: '取消确认后重新修改原文'
    }).then(() => {
      hydrateSourceDraftFromDocument({ ...doc, confirmed: '0' })
    }),
    '原文已取消确认'
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
  if (!ensureProjectContext('当前项目无效，无法保存原文')) {
    return
  }
  if (!hasSourceDraftText.value) {
    ElMessage.warning('请先填写原文内容')
    return
  }
  const rawText = sourceDraft.rawText.trim()
  withSubmit(
    async () => {
      const res = await saveAivideoDocument({
        projectId: projectId.value,
        documentId: sourceDraftDocumentId.value,
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
      if (confirmAfterSave) {
        resetSourceDraft()
      } else {
        sourceDraftDocumentId.value = res.data
        sourceDraftHydratedDocumentKey.value = String(res.data)
      }
    },
    confirmAfterSave ? '原文已保存并确认' : '原文已保存'
  )
}

async function handleCancelAllConfirmations() {
  if (!ensureProjectContext('当前项目无效，无法取消确认')) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '将按资产、剧本、润色、原文的顺序取消已确认内容，取消后你可以重新修改上游内容。是否继续？',
      '取消全部确认',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '先不取消' }
    )
  } catch {
    return
  }
  withSubmit(
    async () => {
      if (hasApprovedAssets.value) {
        await cancelConfirmAivideoAsset({ projectId: projectId.value, targetType: 'ALL', comment: '取消全部确认' })
      }
      if (selectedScript.value) {
        await cancelConfirmAivideoScript({
          projectId: projectId.value,
          versionId: selectedScript.value.versionId,
          comment: '取消全部确认'
        })
      }
      if (selectedPolish.value) {
        await cancelConfirmAivideoPolish({
          projectId: projectId.value,
          versionId: selectedPolish.value.versionId,
          comment: '取消全部确认'
        })
      }
      const doc = latestDocument.value
      if (doc?.confirmed === '1') {
        await cancelConfirmAivideoDocument({
          projectId: projectId.value,
          documentId: doc.documentId,
          comment: '取消全部确认'
        })
        hydrateSourceDraftFromDocument({ ...doc, confirmed: '0' })
      }
      assetContextCollapsed.value = false
    },
    '已取消全部确认'
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

function clearAssetTaskPollingTimer() {
  if (assetTaskPollTimer) {
    clearTimeout(assetTaskPollTimer)
    assetTaskPollTimer = undefined
  }
}

function resolveAssetTaskId() {
  const taskId = assetStreamMeta.value.taskId
  return taskId === undefined || taskId === null || taskId === '' ? '' : String(taskId)
}

function syncAssetTaskMeta(task?: AivideoTask) {
  if (!task) {
    return
  }
  assetStreamMeta.value = {
    ...assetStreamMeta.value,
    taskId: task.taskId,
    taskStatus: task.taskStatus,
    progress: task.progress,
    errorMessage: task.errorMessage,
    updateTime: task.updateTime
  }
}

function scheduleAssetTaskPolling(taskId: string | number, delay = ASSET_TASK_POLL_INTERVAL) {
  clearAssetTaskPollingTimer()
  if (!taskId) {
    return
  }
  assetStreaming.value = true
  submitting.value = true
  assetTaskPollTimer = setTimeout(() => {
    void pollAssetTask(taskId)
  }, delay)
}

async function pollAssetTask(taskId: string | number) {
  try {
    const res = await getAivideoStudioTask(taskId)
    const task = res.data
    if (!task) {
      clearAssetTaskPollingTimer()
      assetStreaming.value = false
      submitting.value = false
      assetContextCollapsed.value = false
      ElMessage.error('资产任务不存在或已不可访问')
      return
    }
    syncAssetTaskMeta(task)
    const status = normalizeShotVideoTaskStatus(task?.taskStatus)
    if (inFlightTaskStatuses.has(status)) {
      scheduleAssetTaskPolling(taskId)
      return
    }
    clearAssetTaskPollingTimer()
    assetStreaming.value = false
    submitting.value = false
    await loadDetail()
    if (status === 'SUCCESS') {
      if (hasAssets.value) {
        ElMessage.success('资产已提取并结构化入库')
      } else {
        assetContextCollapsed.value = false
        ElMessage.warning('资产任务已完成，但没有形成可确认的结构化资产，请查看原始输出')
      }
      return
    }
    if (status === 'FAILED') {
      assetContextCollapsed.value = false
      ElMessage.error(task?.errorMessage || '资产提取失败，请查看原始输出')
    }
  } catch {
    scheduleAssetTaskPolling(taskId, ASSET_TASK_POLL_INTERVAL * 2)
  }
}

async function recoverLatestAssetTask() {
  if (assetStreaming.value) {
    return
  }
  try {
    const res = await getLatestAivideoAssetTask(projectId.value)
    const task = res.data
    if (!task || !inFlightTaskStatuses.has(normalizeShotVideoTaskStatus(task.taskStatus))) {
      return
    }
    syncAssetTaskMeta(task)
    assetContextCollapsed.value = false
    scheduleAssetTaskPolling(task.taskId, 500)
  } catch {
    // Best-effort page-load recovery only; normal asset extraction is unaffected.
  }
}

async function handleExtractAssets() {
  if (!selectedScript.value) {
    ElMessage.warning('请先确认短剧剧本')
    return
  }
  clearAssetTaskPollingTimer()
  assetStreaming.value = true
  submitting.value = true
  assetStreamText.value = ''
  assetStreamMeta.value = {}
  let pollingStarted = false
  try {
    const fullContent = await requestAiStream({
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
        assetStreamMeta.value = { ...assetStreamMeta.value, ...payload }
      },
      onError: (message) => {
        ElMessage.error(message || '资产提取失败')
      }
    })
    if (fullContent) {
      assetStreamText.value = fullContent
    }
    clearAssetTaskPollingTimer()
    await loadDetail()
    if (hasAssets.value) {
      ElMessage.success('资产已提取并结构化入库')
    } else {
      assetContextCollapsed.value = false
      ElMessage.warning('资产原始输出已返回，但没有形成可确认的结构化资产，请重新提取')
    }
  } catch (error: any) {
    const taskId = resolveAssetTaskId()
    if (taskId) {
      pollingStarted = true
      assetContextCollapsed.value = false
      ElMessage.warning('连接已断开，资产后台任务继续执行，正在轮询结果')
      scheduleAssetTaskPolling(taskId, 500)
      return
    }
    assetContextCollapsed.value = false
    ElMessage.error(error?.message || '资产提取失败')
  } finally {
    if (!pollingStarted) {
      assetStreaming.value = false
      submitting.value = false
    }
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
        defaultStyle: params.defaultStyle,
        characterDesignType: params.characterDesignType,
        referenceImageUrls: characterReferenceImageUrls.value,
        referenceMediaIds: characterReferenceMediaIds.value,
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
        defaultStyle: params.defaultStyle,
        referenceImageUrls: sceneReferenceImageUrls.value,
        referenceMediaIds: sceneReferenceMediaIds.value,
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
  const preflightConfirmed = await confirmShotVideoPreflight()
  if (!preflightConfirmed) {
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
        multiRoleStrategy: params.multiRoleStrategy,
        characterDesignType: params.characterDesignType,
        referenceMediaIds: currentShotVideoReferenceMediaIds()
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

async function handleGenerateShotTtsAudio() {
  const shot = selectedShotForVideo.value
  const text = shotTtsText.value.trim()
  if (!shot || !text || shotTtsGenerating.value) {
    return
  }
  const shotId = String(shot.shotId)
  const durationMs = shotDurationMs(shot)
  const ttsStartMs = normalizeTtsMs(shotTtsStartSec.value * 1000, 0, 0, Math.max(0, durationMs - 1))
  const ttsEndMs = normalizeTtsMs(shotTtsEndSec.value * 1000, durationMs, Math.min(durationMs, ttsStartMs + 1), durationMs)
  shotTtsGenerating.value = true
  try {
    const res = await generateAivideoShotTtsAudio({
      projectId: projectId.value,
      shotId: shot.shotId,
      text,
      speaker: shotTtsSpeaker.value.trim() || undefined,
      voiceType: shotTtsVoiceType.value.trim() || undefined,
      ttsStartMs,
      ttsEndMs
    })
    const asset = res.data
    if (asset?.mediaId && isCurrentShotVideoTarget(shotId)) {
      shotTtsAudios.value = [
        asset,
        ...shotTtsAudios.value.filter((item) => String(item.mediaId) !== String(asset.mediaId))
      ].sort(compareMediaAssetDesc)
      await loadReferencePreviewUrl(asset.mediaId)
    }
    ElMessage.success('本镜配音素材已生成并选中')
    await loadShotTtsAudios()
    await loadDetail()
  } catch (error: any) {
    ElMessage.error(error?.message || '本镜配音素材生成失败')
  } finally {
    shotTtsGenerating.value = false
  }
}

async function handleSelectShotTtsAudio(item: AivideoMediaAsset) {
  const key = String(item.mediaId)
  if (shotTtsSelectingIds.value.has(key)) {
    return
  }
  const next = new Set(shotTtsSelectingIds.value)
  next.add(key)
  shotTtsSelectingIds.value = next
  try {
    await selectAivideoMedia({
      projectId: projectId.value,
      mediaId: item.mediaId,
      bizType: 'SHOT',
      bizId: item.bizId
    })
    ElMessage.success('本镜配音素材已选定')
    await loadShotTtsAudios()
    await loadDetail()
  } finally {
    const done = new Set(shotTtsSelectingIds.value)
    done.delete(key)
    shotTtsSelectingIds.value = done
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

onMounted(async () => {
  if (!ensureProjectContext()) {
    return
  }
  await Promise.all([loadAllOptions(), loadDetail()])
  await recoverLatestAssetTask()
})

watch(projectId, async (next, previous) => {
  if (next === previous) {
    return
  }
  invalidProjectRouteHandled.value = false
  if (!ensureProjectContext()) {
    return
  }
  await loadDetail()
  await recoverLatestAssetTask()
})

watch(latestDocument, (doc) => {
  hydrateSourceDraftFromDocument(doc)
}, { immediate: true })

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
  params.multiRoleStrategy,
  params.characterDesignType
], () => {
  schedulePolishPromptPreview()
})

watch(() => [
  characterReferenceMediaIds.value.join(','),
  sceneReferenceMediaIds.value.join(','),
  characterReferenceImageUrls.value.join(','),
  sceneReferenceImageUrls.value.join(',')
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
  clearAssetTaskPollingTimer()
  clearShotVideoRecoveryTimer()
  clearProjectEditPollingTimer()
  revokeReferencePreviewUrls()
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

.shot-transition-cell {
  display: grid;
  gap: 4px;
  align-items: start;
}

.shot-sound-cue-cell {
  display: grid;
  gap: 4px;
  color: #475467;
  font-size: 12px;
  line-height: 1.45;

  p {
    margin: 0;
  }
}

.asset-mini-tag {
  margin-left: 4px;
}

.shot-transition-desc {
  display: -webkit-box;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 1.35;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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

.preflight-title-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
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

.external-reference-form {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;

  label {
    display: block;
    margin-bottom: 8px;
    color: #374151;
    font-weight: 600;
  }
}

.external-reference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.hidden-file-input {
  display: none;
}

.image-extra-prompt-form {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;

  label {
    display: block;
    margin-bottom: 8px;
    color: #1e40af;
    font-weight: 600;
  }
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

.media-register-field,
.media-mini-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.media-register-field {
  width: 100%;

  .el-input {
    flex: 1;
    min-width: 220px;
  }
}

.media-mini-actions {
  margin-top: 4px;
}

.reference-image-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
}

.reference-select-option {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  line-height: 1.25;

  img,
  .reference-select-placeholder {
    width: 40px;
    height: 40px;
    border-radius: 6px;
    object-fit: cover;
    background: #eef2ff;
  }

  .reference-select-placeholder {
    display: grid;
    place-items: center;
    color: #64748b;
    font-size: 12px;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    margin-top: 3px;
    color: #64748b;
  }
}

.reference-selected-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.reference-selected-card {
  display: grid;
  gap: 8px;
  padding: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #ffffff;

  .el-image {
    width: 100%;
    aspect-ratio: 1 / 1;
    border-radius: 6px;
    background: #f3f4f6;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: #64748b;
  }
}

.reference-empty {
  margin-top: 8px;
  padding: 8px 0;
}

.reference-summary {
  color: #475569;
  line-height: 1.5;
}

.voice-summary {
  display: grid;
  gap: 3px;
  line-height: 1.4;

  strong {
    color: #1f2937;
    font-size: 13px;
  }

  small {
    color: #667085;
  }
}

.character-voice-dialog,
.character-voice-form {
  display: grid;
  gap: 14px;
}

@media (max-width: 720px) {
  .reference-image-control {
    align-items: stretch;
    flex-direction: column;
  }

  .reference-selected-grid {
    grid-template-columns: 1fr;
  }

  .shot-screen-rule-grid {
    grid-template-columns: 1fr;
  }
}

.shot-video-gate-alert {
  margin-bottom: 12px;
}

.shot-screen-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.shot-screen-rule-panel {
  display: grid;
  gap: 10px;
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.shot-screen-rule-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.shot-screen-rule-item {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #ffffff;

  > span {
    color: #475569;
    font-size: 12px;
    font-weight: 600;
  }

  > div {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}

.shot-screen-rule-note {
  margin: 0;
  color: #334155;
  line-height: 1.6;
}

.shot-video-preflight-panel {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
}

.shot-video-preflight-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 56px minmax(0, 1fr);
    gap: 8px;
    align-items: flex-start;
  }

  strong,
  small {
    display: block;
  }

  small {
    margin-top: 3px;
    color: #64748b;
    line-height: 1.5;
  }
}

.shot-tts-panel {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
}

.shot-tts-config {
  margin-top: 2px;
}

.tts-time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}

.tts-time-cell small {
  color: #6b7280;
}

.shot-tts-actions,
.shot-tts-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.shot-tts-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.shot-tts-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #dcfce7;
  border-radius: 8px;
  background: #ffffff;

  &.selected {
    border-color: #22c55e;
    background: #f0fdf4;
  }

  audio {
    width: 100%;
  }
}

.shot-tts-placeholder {
  display: grid;
  place-items: center;
  min-height: 42px;
  color: #94a3b8;
  font-size: 12px;
  background: #f8fafc;
  border-radius: 6px;
}

:global(.shot-video-preflight-dialog) {
  ul {
    display: grid;
    gap: 8px;
    margin: 10px 0 0;
    padding-left: 18px;
  }

  li span {
    display: block;
    margin-top: 3px;
    color: #64748b;
    line-height: 1.5;
  }
}

.shot-reference-panel {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.shot-reference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.shot-reference-card {
  position: relative;
  display: grid;
  grid-template-columns: 136px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 110px;
  padding: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #ffffff;
}

.shot-reference-thumb {
  width: 136px;
  height: 92px;
  border-radius: 6px;
  background: #f3f4f6;
  cursor: zoom-in;
}

.shot-reference-video {
  display: block;
  object-fit: cover;
  cursor: default;
}

.shot-reference-audio {
  display: grid;
  place-items: center;
  cursor: default;

  audio {
    width: 124px;
  }

  span {
    color: #94a3b8;
    font-size: 12px;
  }
}

.shot-reference-placeholder {
  display: grid;
  place-items: center;
}

.shot-reference-text {
  min-width: 0;

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  strong {
    white-space: nowrap;
  }

  small {
    margin-top: 6px;
    color: #64748b;
    line-height: 1.5;
    display: -webkit-box;
    white-space: normal;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

.shot-reference-remove {
  position: absolute;
  right: 8px;
  bottom: 6px;
}

.shot-reference-add-card {
  display: grid;
  place-items: center;
  gap: 4px;
  min-height: 110px;
  border: 1px dashed #93c5fd;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #2563eb;
    background: #dbeafe;
  }

  span {
    font-size: 28px;
    line-height: 1;
  }

  strong {
    color: #1d4ed8;
    font-size: 14px;
  }

  small {
    color: #64748b;
  }
}

.shot-reference-add-panel {
  display: grid;
  gap: 10px;

  label {
    color: #111827;
    font-weight: 600;
  }

  small {
    color: #64748b;
    line-height: 1.5;
  }
}

:global(.shot-reference-add-popper) {
  padding: 12px;
}

.shot-reference-add-option {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 4px 0;

  img,
  .shot-reference-add-placeholder {
    width: 56px;
    height: 42px;
    border-radius: 6px;
    object-fit: cover;
    background: #f3f4f6;
  }

  .shot-reference-add-placeholder {
    display: grid;
    place-items: center;
    color: #94a3b8;
    font-size: 12px;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #111827;
    font-size: 13px;
  }

  small {
    margin-top: 3px;
    color: #667085;
    font-size: 12px;
  }
}

.shot-media-cell,
.shot-video-candidate-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
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

.project-edit-panel {
  display: grid;
  gap: 14px;
}

.project-edit-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h4 {
    margin: 0 0 4px;
  }

  p {
    margin: 0;
    color: #6b7280;
    font-size: 13px;
  }
}

.project-edit-actions {
  display: flex;
  gap: 8px;
}

.project-edit-alert {
  ul {
    margin: 0;
    padding-left: 18px;
  }
}

.project-edit-summary,
.project-edit-table {
  margin-top: 2px;
}

.project-edit-subsection {
  display: grid;
  gap: 10px;

  h4 {
    margin: 4px 0 0;
  }
}

.project-edit-task-list {
  display: grid;
  gap: 10px;
}

.project-edit-task-card {
  display: grid;
  gap: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fbff;
}

.task-card-head,
.project-edit-video-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.muted-line,
.task-error {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
}

.task-error {
  color: #dc2626;
}

.project-edit-video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.project-edit-video-card {
  display: grid;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
  background: #fff;

  video {
    width: 100%;
    aspect-ratio: 9 / 16;
    object-fit: cover;
    border-radius: 6px;
    background: #111827;
  }
}

.project-edit-vod-placeholder {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 260px;
  color: #6b7280;
  border-radius: 6px;
  background: #f3f4f6;
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

.params-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 18px;
  }

  small {
    display: block;
    margin-top: 4px;
    color: #64748b;
    line-height: 1.4;
  }
}

.params-actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
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
