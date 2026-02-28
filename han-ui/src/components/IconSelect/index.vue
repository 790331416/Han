<template>
  <div class="icon-select">
    <el-popover placement="bottom-start" :width="540" trigger="click" :visible="popoverVisible">
      <template #reference>
        <el-input
          :model-value="modelValue"
          placeholder="点击选择图标"
          readonly
          @click="popoverVisible = !popoverVisible"
        >
          <template #prefix>
            <el-icon v-if="modelValue && modelValue !== '#'" style="font-size: 16px">
              <component :is="modelValue" />
            </el-icon>
          </template>
          <template #suffix>
            <el-icon v-if="modelValue" style="cursor: pointer" @click.stop="handleClear">
              <CircleClose />
            </el-icon>
          </template>
        </el-input>
      </template>
      <div class="icon-select-popper">
        <el-input v-model="searchText" placeholder="搜索图标" clearable style="margin-bottom: 10px" />
        <el-scrollbar height="280px">
          <div class="icon-grid">
            <div
              v-for="icon in filteredIcons"
              :key="icon"
              class="icon-item"
              :class="{ 'is-active': modelValue === icon }"
              @click="handleSelect(icon)"
              :title="icon"
            >
              <el-icon :size="20"><component :is="icon" /></el-icon>
              <span class="icon-name">{{ icon }}</span>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const popoverVisible = ref(false)
const searchText = ref('')

const allIcons = Object.keys(ElementPlusIconsVue).filter(name => name !== 'default')

const filteredIcons = computed(() => {
  if (!searchText.value) return allIcons
  const keyword = searchText.value.toLowerCase()
  return allIcons.filter(name => name.toLowerCase().includes(keyword))
})

const handleSelect = (icon: string) => {
  emit('update:modelValue', icon)
  popoverVisible.value = false
}

const handleClear = () => {
  emit('update:modelValue', '')
}
</script>

<style lang="scss" scoped>
.icon-select {
  width: 100%;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
    color: #409eff;
    background: #ecf5ff;
  }

  &.is-active {
    border-color: #409eff;
    color: #409eff;
    background: #ecf5ff;
  }

  .icon-name {
    font-size: 10px;
    margin-top: 4px;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 70px;
  }
}
</style>
