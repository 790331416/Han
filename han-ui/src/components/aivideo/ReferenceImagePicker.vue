<template>
  <el-form class="reference-image-form" label-position="top" :data-testid="`reference-image-picker-${scope}`">
    <el-form-item :label="label">
      <el-select
        v-model="selectedIds"
        multiple
        clearable
        filterable
        collapse-tags
        collapse-tags-tooltip
        :placeholder="placeholder"
        :disabled="disabled"
        @change="handleChange"
      >
        <el-option
          v-for="option in options"
          :key="option.mediaId"
          :label="option.label"
          :value="option.mediaId"
        >
          <div class="reference-select-option">
            <img
              v-if="previewUrls[option.mediaId]"
              :src="previewUrls[option.mediaId]"
              :alt="thumbnailAlt"
            />
            <span v-else class="reference-select-placeholder">图</span>
            <div>
              <strong>{{ option.label }}</strong>
              <small>{{ option.subtitle }}</small>
            </div>
          </div>
        </el-option>
      </el-select>
      <div v-if="selectedOptions.length" class="reference-selected-grid">
        <article
          v-for="option in selectedOptions"
          :key="option.mediaId"
          class="reference-selected-card"
          :data-testid="`reference-selected-card-${scope}`"
        >
          <el-image
            v-if="previewUrls[option.mediaId]"
            :src="previewUrls[option.mediaId]"
            :preview-src-list="selectedPreviewList"
            fit="cover"
            preview-teleported
          />
          <el-empty v-else description="缩略图加载中" />
          <div>
            <strong>{{ option.label }}</strong>
            <small>{{ option.subtitle }}</small>
          </div>
        </article>
      </div>
      <el-empty v-else class="reference-empty" :description="emptyDescription" />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface ReferenceImageOption {
  mediaId: string
  label: string
  subtitle: string
  sourceName: string
}

const props = withDefaults(defineProps<{
  modelValue: string[]
  scope: 'scene' | 'character'
  label: string
  placeholder: string
  options: ReferenceImageOption[]
  previewUrls: Record<string, string>
  thumbnailAlt: string
  emptyDescription: string
  disabled?: boolean
}>(), {
  disabled: false
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string[]): void
  (event: 'change', value: string[]): void
}>()

const selectedIds = computed({
  get: () => props.modelValue,
  set: (value: string[]) => emit('update:modelValue', toStringIds(value))
})

const optionMap = computed(() => new Map(props.options.map((item) => [item.mediaId, item])))

const selectedOptions = computed(() => props.modelValue
  .map((id) => optionMap.value.get(String(id)))
  .filter(Boolean) as ReferenceImageOption[])

const selectedPreviewList = computed(() => selectedOptions.value
  .map((item) => props.previewUrls[item.mediaId])
  .filter(Boolean))

function toStringIds(value: Array<string | number>) {
  return Array.from(new Set((value || []).map((item) => String(item)).filter(Boolean)))
}

function handleChange(value: Array<string | number>) {
  emit('change', toStringIds(value))
}
</script>

<style lang="scss" scoped>
.reference-image-form {
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
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

@media (max-width: 720px) {
  .reference-selected-grid {
    grid-template-columns: 1fr;
  }
}
</style>
