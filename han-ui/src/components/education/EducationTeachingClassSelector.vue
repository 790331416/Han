<template>
  <EducationClassSelector
    v-if="!teacher"
    :model-value="studentValue"
    :school-id="schoolId"
    :academic-year-id="academicYearId"
    :clearable="clearable"
    :disabled="disabled"
    placeholder="搜索并选择所属班级"
    @update:model-value="changeStudent"
  />
  <el-tree-select
    v-else
    :model-value="modelValue"
    :data="tree"
    node-key="value"
    :props="treeProps"
    filterable
    multiple
    show-checkbox
    :check-strictly="false"
    :clearable="clearable"
    :disabled="disabled || !schoolId"
    :placeholder="schoolId ? '搜索并勾选任教年级或班级' : '请先选择学校'"
    :render-after-expand="false"
    style="width: 100%"
    @update:model-value="changeTeacher"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listClassTree, type EducationClassTreeNode } from '@/api/education'
import EducationClassSelector from './EducationClassSelector.vue'

type Value = string | number
type TreeOption = { value: Value; label: string; nodeType: EducationClassTreeNode['nodeType']; children: TreeOption[] }
const props = withDefaults(defineProps<{ modelValue?: Value[]; schoolId?: Value; academicYearId?: Value; teacher?: boolean; clearable?: boolean; disabled?: boolean }>(), {
  teacher: false, clearable: true, disabled: false
})
const emit = defineEmits<{ 'update:modelValue': [value: Value[]]; change: [value: Value[]] }>()
const tree = ref<TreeOption[]>([])
const treeProps = { label: 'label', children: 'children' }
const studentValue = computed<Value | undefined>(() => props.modelValue?.[0])
let requestNo = 0

watch(() => [props.schoolId, props.academicYearId], () => { void load() }, { immediate: true })

function toOption(node: EducationClassTreeNode): TreeOption {
  return {
    value: node.id,
    label: node.className,
    nodeType: node.nodeType,
    children: (node.children || []).map(toOption)
  }
}
async function load() {
  if (!props.schoolId) { tree.value = []; return }
  const current = ++requestNo
  const response = await listClassTree({ schoolId: props.schoolId, academicYearId: props.academicYearId, status: 0 })
  if (current === requestNo) tree.value = (response.data || []).map(toOption)
}
function emitValue(value: Value[]) { emit('update:modelValue', value); emit('change', value) }
function changeStudent(value: Value | Value[] | null | undefined) {
  const selected = Array.isArray(value) ? value[0] : value
  emitValue(selected === undefined || selected === null || selected === '' ? [] : [selected])
}
function changeTeacher(value: Value | Value[] | undefined) {
  const selected = new Set((Array.isArray(value) ? value : value === undefined || value === null ? [] : [value]).map(item => String(item)))
  const classes: Value[] = []
  const seen = new Set<string>()
  const visit = (node: TreeOption, inherited: boolean) => {
    const selectedHere = inherited || selected.has(String(node.value))
    if (node.nodeType === 'CLASS' && selectedHere && !seen.has(String(node.value))) {
      seen.add(String(node.value)); classes.push(node.value)
    }
    for (const child of node.children) visit(child, selectedHere)
  }
  for (const node of tree.value) visit(node, false)
  emitValue(classes)
}
</script>
