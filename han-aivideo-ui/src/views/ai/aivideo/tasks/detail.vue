<template>
  <div class="app-container">
    <el-page-header title="返回" content="短剧任务详情" @back="router.push('/ai/aivideo/tasks')" />

    <el-card v-loading="loading" shadow="never" class="detail-card">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务ID">{{ task.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目ID">{{ task.projectId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ task.taskType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag>{{ task.taskStatus || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商任务ID">{{ task.providerTaskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ task.progress || 0 }}%</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ task.startedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ task.finishedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ task.errorCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ task.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs class="detail-tabs">
        <el-tab-pane label="参数快照">
          <pre>{{ task.paramsJson || '{}' }}</pre>
        </el-tab-pane>
        <el-tab-pane label="Prompt">
          <pre>{{ task.promptText || task.customPrompt || '' }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAivideoTask, type AivideoTask } from '@/api/aivideo'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const task = ref<AivideoTask>({} as AivideoTask)

async function loadTask() {
  loading.value = true
  try {
    const res = await getAivideoTask(String(route.params.taskId))
    task.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTask()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.detail-card {
  margin-top: 16px;
}

.detail-tabs {
  margin-top: 18px;
}

pre {
  min-height: 180px;
  margin: 0;
  padding: 14px;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
</style>
