<template>
  <el-select
    :model-value="modelValue" :multiple="multiple" :clearable="clearable" :filterable="filterable"
    :collapse-tags="multiple" collapse-tags-tooltip :disabled="disabled" :placeholder="placeholder" style="width: 100%"
    @update:model-value="change"
  >
    <el-option v-for="option in options" :key="String(option.value)" :label="option.label" :value="option.value" :disabled="option.disabled" />
  </el-select>
</template>

<script setup lang="ts">
export interface EducationSelectOption { label: string; value: string | number; disabled?: boolean }
type SelectionValue = string | number | Array<string | number> | undefined

withDefaults(defineProps<{ modelValue?: SelectionValue; options: EducationSelectOption[]; multiple?: boolean; clearable?: boolean; filterable?: boolean; disabled?: boolean; placeholder?: string }>(), {
  multiple: false, clearable: true, filterable: true, disabled: false, placeholder: '请选择'
})
const emit = defineEmits<{ 'update:modelValue': [value: SelectionValue]; change: [value: SelectionValue] }>()
function change(value: SelectionValue) { emit('update:modelValue', value); emit('change', value) }
</script>
