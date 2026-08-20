<template>
  <el-tree-select
    :model-value="modelValue" :data="tree" node-key="value" :props="treeProps" filterable check-strictly
    :show-checkbox="multiple" :multiple="multiple" :clearable="clearable" :disabled="disabled || !schoolId"
    :placeholder="schoolId ? placeholder : '请先选择学校'" :render-after-expand="false" style="width: 100%"
    @update:model-value="change"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { listClassTree, type EducationClassTreeNode } from '@/api/education'

type SelectionValue = string | number | null | Array<string | number> | undefined
type TreeOption = { value: string | number; label: string; disabled: boolean; children: TreeOption[] }
const props = withDefaults(defineProps<{ modelValue?: SelectionValue; schoolId?: string | number; academicYearId?: string | number; multiple?: boolean; clearable?: boolean; disabled?: boolean; placeholder?: string; selectableTypes?: Array<EducationClassTreeNode['nodeType']> }>(), {
  multiple: false, clearable: true, disabled: false, placeholder: '搜索并选择班级'
})
const emit = defineEmits<{ 'update:modelValue': [value: SelectionValue]; change: [value: SelectionValue] }>()
const tree = ref<TreeOption[]>([])
const treeProps = { label: 'label', children: 'children', disabled: 'disabled' }
let requestNo = 0

watch(() => [props.schoolId, props.academicYearId], () => { void load() }, { immediate: true })

function toOption(node: EducationClassTreeNode): TreeOption {
  return {
    value: node.id,
    label: node.className,
    disabled: !(props.selectableTypes || ['CLASS']).includes(node.nodeType),
    children: (node.children || []).map(toOption)
  }
}
async function load() {
  if (!props.schoolId) { tree.value = []; return }
  const current = ++requestNo
  const response = await listClassTree({ schoolId: props.schoolId, academicYearId: props.academicYearId, status: 0 })
  if (current === requestNo) tree.value = (response.data || []).map(toOption)
}
function change(value: SelectionValue) { emit('update:modelValue', value); emit('change', value) }
</script>
