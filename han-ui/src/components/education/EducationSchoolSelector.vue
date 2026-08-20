<template>
  <el-tree-select
    :model-value="modelValue" :data="tree" node-key="value" filterable check-strictly :render-after-expand="false"
    :disabled="disabled" :clearable="clearable" :placeholder="placeholder" @update:model-value="change"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { toSchoolTree, type SchoolTreeOption } from '@/utils/education-school-tree'
import type { EducationOrganizationNode } from '@/api/education'

const props = withDefaults(defineProps<{ modelValue?: string | number; nodes: EducationOrganizationNode[]; placeholder?: string; disabled?: boolean; clearable?: boolean }>(), { placeholder: '搜索并选择校区或独立学校', disabled: false, clearable: false })
const emit = defineEmits<{ 'update:modelValue': [value: string | number | undefined]; change: [value: string | number | undefined] }>()
const tree = computed<SchoolTreeOption[]>(() => toSchoolTree(props.nodes))
function change(value: string | number | undefined) { emit('update:modelValue', value); emit('change', value) }
</script>
