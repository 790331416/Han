<template>
  <EducationOptionSelector
    :model-value="modelValue" :options="options" :multiple="multiple" :clearable="clearable" :disabled="disabled || !schoolId"
    :placeholder="schoolId ? placeholder : '请先选择学校'" @update:model-value="update" @change="change"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { listEducation } from '@/api/education'
import EducationOptionSelector, { type EducationSelectOption } from './EducationOptionSelector.vue'

type SelectionValue = string | number | Array<string | number> | undefined
const props = withDefaults(defineProps<{ modelValue?: SelectionValue; schoolId?: string | number; multiple?: boolean; clearable?: boolean; disabled?: boolean; placeholder?: string }>(), {
  multiple: false, clearable: true, disabled: false, placeholder: '搜索并选择科目'
})
const emit = defineEmits<{ 'update:modelValue': [value: SelectionValue]; change: [value: SelectionValue] }>()
const options = ref<EducationSelectOption[]>([])
let requestNo = 0

watch(() => props.schoolId, () => { void load() }, { immediate: true })
async function load() {
  if (!props.schoolId) { options.value = []; return }
  const current = ++requestNo
  const response = await listEducation('subjects', { schoolId: props.schoolId, status: 0, pageNum: 1, pageSize: 100 })
  if (current === requestNo) options.value = (response.data?.rows || []).map(item => ({ label: item.subjectName, value: String(item.id) }))
}
function update(value: SelectionValue) { emit('update:modelValue', value) }
function change(value: SelectionValue) { emit('change', value) }
</script>
