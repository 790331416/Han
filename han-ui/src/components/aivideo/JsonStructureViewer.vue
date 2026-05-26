<template>
  <div v-if="payload" class="json-structure-viewer">
    <section v-if="characters.length" class="structure-section">
      <div class="structure-title">
        <h4>人物</h4>
        <el-tag type="success" effect="plain">{{ characters.length }} 个</el-tag>
      </div>
      <div class="structure-grid">
        <article v-for="(item, index) in characters" :key="`character-${index}`" class="structure-card">
          <header>
            <strong>{{ index + 1 }}. {{ item.characterName || `人物 ${index + 1}` }}</strong>
            <el-tag v-if="item.completeness" size="small" effect="plain">{{ item.completeness }}</el-tag>
          </header>
          <dl>
            <template v-for="field in characterFields" :key="field.key">
              <div v-if="formatValue(item[field.key], field.key)" class="field-row">
                <dt>{{ field.label }}</dt>
                <dd>{{ formatValue(item[field.key], field.key) }}</dd>
              </div>
            </template>
          </dl>
        </article>
      </div>
    </section>

    <section v-if="scenes.length" class="structure-section">
      <div class="structure-title">
        <h4>场景</h4>
        <el-tag type="warning" effect="plain">{{ scenes.length }} 个</el-tag>
      </div>
      <div class="structure-grid">
        <article v-for="(item, index) in scenes" :key="`scene-${index}`" class="structure-card">
          <header>
            <strong>{{ index + 1 }}. {{ item.sceneName || `场景 ${index + 1}` }}</strong>
            <el-tag v-if="item.completeness" size="small" effect="plain">{{ item.completeness }}</el-tag>
          </header>
          <dl>
            <template v-for="field in sceneFields" :key="field.key">
              <div v-if="formatValue(item[field.key], field.key)" class="field-row">
                <dt>{{ field.label }}</dt>
                <dd>{{ formatValue(item[field.key], field.key) }}</dd>
              </div>
            </template>
          </dl>
        </article>
      </div>
    </section>

    <section v-if="shots.length" class="structure-section">
      <div class="structure-title">
        <h4>分镜</h4>
        <el-tag type="primary" effect="plain">{{ shots.length }} 个</el-tag>
      </div>
      <div class="shot-list">
        <article v-for="(item, index) in shots" :key="`shot-${index}`" class="structure-card shot-card">
          <header>
            <strong>第 {{ item.episodeNo || 1 }} 集 / 镜头 {{ item.shotNo || index + 1 }}</strong>
            <el-tag v-if="item.durationSec" size="small" effect="plain">{{ item.durationSec }} 秒</el-tag>
          </header>
          <dl>
            <template v-for="field in shotFields" :key="field.key">
              <div v-if="formatValue(item[field.key], field.key)" class="field-row">
                <dt>{{ field.label }}</dt>
                <dd>{{ formatValue(item[field.key], field.key) }}</dd>
              </div>
            </template>
          </dl>
        </article>
      </div>
    </section>
  </div>
  <MarkdownViewer v-else :content="content" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownViewer from './MarkdownViewer.vue'

type JsonRecord = Record<string, any>

const props = defineProps<{
  content?: string
}>()

const fieldLabels: Record<string, string> = {
  characterName: '人物名称',
  gender: '性别',
  ageDesc: '年龄描述',
  identityDesc: '身份描述',
  personalityTags: '性格标签',
  storyRole: '剧情定位',
  relationshipDesc: '关系说明',
  appearance: '外观',
  hairStyle: '发型',
  costume: '服装',
  colorStyle: '色彩风格',
  negativeTraits: '负面限制',
  promptText: '生成提示词',
  completeness: '完整度',
  missingFields: '待补字段',
  sceneName: '场景名称',
  sceneType: '场景类型',
  episodeNo: '集数',
  timeDesc: '时间',
  weather: '天气',
  atmosphere: '氛围',
  visualFeatures: '视觉特征',
  colorTone: '色调',
  props: '道具',
  negativeElements: '负面元素',
  shotNo: '镜头号',
  durationSec: '镜头秒数',
  characterNames: '出场人物',
  shotType: '镜头类型',
  cameraPosition: '机位',
  cameraMovement: '镜头运动',
  actionDesc: '动作描述',
  dialogue: '对白',
  voiceOver: '旁白',
  emotion: '情绪',
  sceneId: '场景ID',
  characterIds: '人物ID'
}

const characterFields = fields([
  'gender', 'ageDesc', 'identityDesc', 'personalityTags', 'storyRole', 'relationshipDesc',
  'appearance', 'hairStyle', 'costume', 'colorStyle', 'negativeTraits', 'promptText', 'missingFields'
])
const sceneFields = fields([
  'sceneType', 'episodeNo', 'timeDesc', 'weather', 'atmosphere', 'visualFeatures',
  'colorTone', 'props', 'negativeElements', 'promptText', 'missingFields'
])
const shotFields = fields([
  'sceneName', 'characterNames', 'shotType', 'cameraPosition', 'cameraMovement',
  'actionDesc', 'dialogue', 'voiceOver', 'emotion', 'promptText'
])

const payload = computed<JsonRecord | null>(() => parseJsonPayload(props.content || ''))
const characters = computed<JsonRecord[]>(() => safeArray(payload.value?.characters))
const scenes = computed<JsonRecord[]>(() => safeArray(payload.value?.scenes))
const shots = computed<JsonRecord[]>(() => safeArray(payload.value?.shots))

function fields(keys: string[]) {
  return keys.map((key) => ({ key, label: fieldLabels[key] || key }))
}

function safeArray(value: unknown): JsonRecord[] {
  return Array.isArray(value) ? value.filter((item) => item && typeof item === 'object') : []
}

function parseJsonPayload(content: string): JsonRecord | null {
  const json = extractJson(content)
  if (!json) {
    return null
  }
  try {
    const parsed = JSON.parse(json)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (_error) {
    return null
  }
}

function extractJson(content: string) {
  const text = content.trim()
  if (!text) {
    return ''
  }
  const fenceMatch = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const source = fenceMatch?.[1]?.trim() || text
  const start = source.indexOf('{')
  const end = source.lastIndexOf('}')
  if (start < 0 || end <= start) {
    return ''
  }
  return source.slice(start, end + 1)
}

function formatValue(value: unknown, key: string): string {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatArrayItem(item, key)).filter(Boolean).join('、')
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  if (key === 'missingFields') {
    return fieldLabels[String(value)] || String(value)
  }
  return String(value)
}

function formatArrayItem(value: unknown, key: string) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  if (key === 'missingFields') {
    return fieldLabels[String(value)] || String(value)
  }
  return String(value)
}
</script>

<style lang="scss" scoped>
.json-structure-viewer {
  display: grid;
  gap: 14px;
}

.structure-section {
  display: grid;
  gap: 10px;
}

.structure-title {
  display: flex;
  align-items: center;
  gap: 8px;

  h4 {
    margin: 0;
    font-size: 15px;
  }
}

.structure-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.shot-list {
  display: grid;
  gap: 10px;
}

.structure-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;

  header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  strong {
    color: #111827;
  }

  dl {
    display: grid;
    gap: 7px;
    margin: 0;
  }
}

.field-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  color: #374151;
  line-height: 1.55;

  dt {
    color: #6b7280;
  }

  dd {
    margin: 0;
    word-break: break-word;
  }
}

@media (max-width: 1380px) {
  .structure-grid {
    grid-template-columns: 1fr;
  }
}
</style>
