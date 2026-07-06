<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="文件名" prop="fileName">
          <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="fileType">
          <el-input v-model="queryParams.fileType" placeholder="如 png / pdf" clearable style="width: 140px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>文件列表</span>
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="selectedIds.length === 0"
            data-testid="file-batch-remove"
            @click="handleBatchRemove"
          >
            批量删除
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="fileList" data-testid="file-table" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="文件名" prop="fileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" prop="fileType" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110" align="center">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="存储" prop="storageType" width="90" align="center" />
        <el-table-column label="上传人" prop="createBy" width="120" align="center">
          <template #default="{ row }">{{ row.createBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="上传时间" prop="createTime" min-width="170" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" data-testid="file-preview-button" @click="handlePreview(row)">预览</el-button>
            <el-button type="success" link :icon="Download" data-testid="file-download-button" @click="handleDownload(row)">下载</el-button>
            <el-button type="danger" link :icon="Delete" data-testid="file-remove-button" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="[previewUrl]"
      teleported
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, Download } from '@element-plus/icons-vue'
import { listFile, removeFile, type SysFile, type SysFileQuery } from '@/api/file'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const fileList = ref<SysFile[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const timeRange = ref<[string, string] | null>(null)
const previewVisible = ref(false)
const previewUrl = ref('')

const queryFormRef = ref<FormInstance>()
const queryParams = reactive<SysFileQuery>({ pageNum: 1, pageSize: 10 })

const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp']

const formatSize = (bytes?: number) => {
  if (bytes === undefined || bytes === null) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listFile({
      ...queryParams,
      beginTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    fileList.value = (res as any).data?.rows || []
    total.value = (res as any).data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  timeRange.value = null
  handleQuery()
}

const handleSelectionChange = (rows: SysFile[]) => {
  selectedIds.value = rows.map((row) => row.id)
}

/** 预览：图片走大图预览，其余类型新窗口打开公开代理地址。 */
const handlePreview = (row: SysFile) => {
  if (!row.fileUrl) {
    ElMessage.warning('该文件没有可访问地址')
    return
  }
  const type = (row.fileType || '').toLowerCase()
  if (IMAGE_TYPES.includes(type)) {
    previewUrl.value = row.fileUrl
    previewVisible.value = true
  } else {
    window.open(row.fileUrl, '_blank', 'noopener')
  }
}

/** 下载：blob 另存保文件名（publicAccess 为公开代理地址，fetch 即可）。 */
const handleDownload = async (row: SysFile) => {
  if (!row.fileUrl) {
    ElMessage.warning('该文件没有可访问地址')
    return
  }
  try {
    const response = await fetch(row.fileUrl)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = row.fileName || `file-${row.id}`
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (e: any) {
    ElMessage.error('下载失败: ' + (e.message || '未知错误'))
  }
}

const doRemove = async (ids: (string | number)[]) => {
  await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个文件吗? 对象存储中的文件将一并清理!`, '提示', { type: 'warning' })
  await removeFile(ids)
  ElMessage.success('删除成功')
  getList()
}

const handleRemove = (row: SysFile) => { void doRemove([row.id]).catch(() => { /* 取消 */ }) }
const handleBatchRemove = () => { void doRemove(selectedIds.value).catch(() => { /* 取消 */ }) }

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
