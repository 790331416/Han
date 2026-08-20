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
import { listPlaceTree, type EducationPlaceTreeNode } from '@/api/education'

type SelectionValue = string | number | Array<string | number> | undefined
type TreeOption = { value: string | number; label: string; disabled: boolean; children: TreeOption[] }
const props = withDefaults(defineProps<{ modelValue?: SelectionValue; schoolId?: string | number; multiple?: boolean; clearable?: boolean; disabled?: boolean; placeholder?: string; selectableTypes?: Array<EducationPlaceTreeNode['nodeType']> }>(), {
  multiple: false, clearable: true, disabled: false, placeholder: '搜索并选择场所'
})
const emit = defineEmits<{ 'update:modelValue': [value: SelectionValue]; change: [value: SelectionValue] }>()
const tree = ref<TreeOption[]>([])
const treeProps = { label: 'label', children: 'children', disabled: 'disabled' }
let requestNo = 0

watch(() => props.schoolId, () => { void load() }, { immediate: true })

function toOption(node: EducationPlaceTreeNode): TreeOption {
  const extra = node.aliasName ? `（${node.aliasName}）` : ''
  return { value: node.id, label: `${node.roomName}${extra}`, disabled: !(props.selectableTypes || ['PLACE']).includes(node.nodeType), children: (node.children || []).map(toOption) }
}
async function load() {
  if (!props.schoolId) { tree.value = []; return }
  const current = ++requestNo
  const response = await listPlaceTree({ schoolId: props.schoolId, status: 0 })
  if (current === requestNo) tree.value = (response.data || []).map(toOption)
}
function change(value: SelectionValue) { emit('update:modelValue', value); emit('change', value) }
</script>
