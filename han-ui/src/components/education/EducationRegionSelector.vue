<template>
  <el-popover v-model:visible="visible" trigger="click" placement="bottom-start" :width="440" @show="loadRoots">
    <el-input v-model="keyword" clearable placeholder="搜索区域名称或编码" @input="scheduleSearch" />
    <el-tree
      v-loading="loading" class="region-tree" :data="nodes" node-key="id" lazy :load="loadChildren"
      :props="treeProps" :expand-on-click-node="false" @node-click="select"
    />
    <template #reference>
      <el-input :model-value="selectedLabel" readonly clearable :disabled="disabled" :placeholder="placeholder" @clear="clear" />
    </template>
  </el-popover>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { LoadFunction } from 'element-plus'
import { listRegionOptions, listRegionPath, searchRegionOptions, type EduRegionOption, type EducationRegionSearchOption } from '@/api/education'

type RegionTreeNode = EduRegionOption & { label: string; leaf: boolean }

const props = withDefaults(defineProps<{ modelValue?: string | number | null; placeholder?: string; disabled?: boolean }>(), { placeholder: '逐级选择省、市、区县', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: string | number | null | undefined]; change: [value: string | number | null | undefined] }>()
const visible = ref(false)
const keyword = ref('')
const loading = ref(false)
const nodes = ref<RegionTreeNode[]>([])
const roots = ref<RegionTreeNode[]>([])
const selectedLabel = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let requestNo = 0
const treeProps = { label: 'label', children: 'children', isLeaf: 'leaf' }

onMounted(loadRoots)
onBeforeUnmount(() => { if (timer) clearTimeout(timer) })
watch(() => props.modelValue, value => { void resolveSelected(value) }, { immediate: true })

function toNode(item: EduRegionOption | EducationRegionSearchOption, label = item.regionName): RegionTreeNode {
  return { ...item, label, leaf: (item.nodeLevel || 0) >= 3 }
}
async function loadRoots() {
  if (roots.value.length) {
    if (!keyword.value) nodes.value = roots.value
    return
  }
  loading.value = true
  try {
    const response = await listRegionOptions()
    roots.value = (response.data || []).map(item => toNode(item))
    if (!keyword.value) nodes.value = roots.value
  } finally { loading.value = false }
}
const loadChildren: LoadFunction = (node, resolve) => {
  if (node.level === 0) { resolve(nodes.value); return }
  void listRegionOptions(undefined, node.data.id).then(response => resolve((response.data || []).map(item => toNode(item)))).catch(() => resolve([]))
}
function scheduleSearch() {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { void search() }, 220)
}
async function search() {
  const value = keyword.value.trim()
  if (!value) { nodes.value = roots.value; return }
  const currentRequest = ++requestNo
  loading.value = true
  try {
    const response = await searchRegionOptions(value)
    if (currentRequest === requestNo) nodes.value = (response.data || []).map(item => toNode(item, item.pathLabel))
  } finally { if (currentRequest === requestNo) loading.value = false }
}
async function resolveSelected(value?: string | number | null) {
  if (value === undefined || value === null || value === '') { selectedLabel.value = ''; return }
  const response = await listRegionPath(value)
  const path = response.data || []
  selectedLabel.value = path.map(item => item.regionName).join(' > ') || String(value)
}
function select(node: RegionTreeNode) {
  selectedLabel.value = node.label
  visible.value = false
  emit('update:modelValue', node.id)
  emit('change', node.id)
}
function clear() {
  selectedLabel.value = ''
  emit('update:modelValue', undefined)
  emit('change', undefined)
}
</script>

<style scoped>
.region-tree { max-height: 300px; margin-top: 10px; overflow: auto; }
</style>
