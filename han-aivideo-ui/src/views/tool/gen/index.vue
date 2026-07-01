<template>
  <div class="app-container" data-testid="gen-page">
    <!-- 搜索 -->
    <el-card shadow="never" class="search-form">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="表名称">
          <el-input v-model="queryParams.tableName" placeholder="请输入表名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 已导入的表列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>代码生成</span>
          <el-button type="primary" :icon="Upload" data-testid="gen-import-button" @click="showImportDialog = true">导入表</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableList" data-testid="gen-table">
        <el-table-column label="表名称" prop="tableName" min-width="180" show-overflow-tooltip />
        <el-table-column label="表描述" prop="tableComment" min-width="180" show-overflow-tooltip />
        <el-table-column label="包路径" prop="packageName" min-width="200" show-overflow-tooltip />
        <el-table-column label="功能名" prop="functionName" width="140" />
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePreview(row)">预览</el-button>
            <el-button type="success" link @click="handleDownload(row)">生成</el-button>
            <el-button type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="getList"
        @current-change="getList"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </el-card>

    <!-- 导入表弹窗 -->
    <el-dialog v-model="showImportDialog" title="导入表" width="600px" destroy-on-close>
      <el-form :inline="true" style="margin-bottom: 12px;">
        <el-form-item>
          <el-input v-model="dbSearchName" placeholder="表名搜索" clearable @keyup.enter="loadDbTables" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDbTables">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="dbLoading" :data="dbTableList" @selection-change="handleDbSelectionChange" max-height="400">
        <el-table-column type="selection" width="55" />
        <el-table-column label="表名称" prop="tableName" />
        <el-table-column label="表描述" prop="tableComment" />
      </el-table>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!selectedDbTables.length" @click="handleImport">
          导入 ({{ selectedDbTables.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览代码弹窗 -->
    <el-dialog v-model="showPreviewDialog" title="代码预览" width="80%" top="5vh" destroy-on-close>
      <el-tabs v-model="previewActiveTab">
        <el-tab-pane v-for="(code, fileName) in previewCodes" :key="fileName" :label="getTabLabel(fileName)" :name="fileName">
          <pre class="code-preview"><code>{{ code }}</code></pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 编辑表配置弹窗 -->
    <el-dialog v-model="showEditDialog" title="编辑表配置" width="70%" top="5vh" destroy-on-close>
      <el-form :model="editForm" label-width="100px" style="max-width: 600px;">
        <el-form-item label="表名称">
          <el-input :model-value="editForm.tableName" disabled />
        </el-form-item>
        <el-form-item label="表描述">
          <el-input v-model="editForm.tableComment" />
        </el-form-item>
        <el-form-item label="包路径">
          <el-input v-model="editForm.packageName" />
        </el-form-item>
        <el-form-item label="模块名">
          <el-input v-model="editForm.moduleName" />
        </el-form-item>
        <el-form-item label="业务名">
          <el-input v-model="editForm.businessName" />
        </el-form-item>
        <el-form-item label="功能名">
          <el-input v-model="editForm.functionName" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editForm.author" />
        </el-form-item>
      </el-form>

      <el-divider>字段配置</el-divider>
      <el-table :data="editForm.columns" max-height="400" size="small">
        <el-table-column label="列名" prop="columnName" width="140" />
        <el-table-column label="描述" width="140">
          <template #default="{ row }">
            <el-input v-model="row.columnComment" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="Java类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.javaType" size="small">
              <el-option label="String" value="String" />
              <el-option label="Long" value="Long" />
              <el-option label="Integer" value="Integer" />
              <el-option label="Boolean" value="Boolean" />
              <el-option label="BigDecimal" value="BigDecimal" />
              <el-option label="LocalDateTime" value="LocalDateTime" />
              <el-option label="LocalDate" value="LocalDate" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Java字段" prop="javaField" width="130" />
        <el-table-column label="列表" width="60" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isList" :true-value="1" :false-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="查询" width="60" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isQuery" :true-value="1" :false-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="必填" width="60" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isRequired" :true-value="1" :false-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="显示类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.htmlType" size="small">
              <el-option label="文本框" value="input" />
              <el-option label="下拉框" value="select" />
              <el-option label="日期" value="datetime" />
              <el-option label="文本域" value="textarea" />
              <el-option label="单选" value="radio" />
              <el-option label="复选" value="checkbox" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Upload } from '@element-plus/icons-vue'
import {
  listGenTable, listDbTable, importTable, getGenTable,
  updateGenTable, deleteGenTable, previewCode, downloadCode
} from '@/api/tool/gen'
import type { GenTable, DbTableInfo } from '@/api/tool/gen'

const loading = ref(false)
const tableList = ref<GenTable[]>([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10, tableName: '' })

// 导入
const showImportDialog = ref(false)
const dbLoading = ref(false)
const dbSearchName = ref('')
const dbTableList = ref<DbTableInfo[]>([])
const selectedDbTables = ref<DbTableInfo[]>([])
const importLoading = ref(false)

// 预览
const showPreviewDialog = ref(false)
const previewCodes = ref<Record<string, string>>({})
const previewActiveTab = ref('')

// 编辑
const showEditDialog = ref(false)
const editForm = ref<GenTable>({} as GenTable)
const editLoading = ref(false)

const getList = async () => {
  loading.value = true
  try {
    const res = await listGenTable(queryParams)
    tableList.value = res.data.rows
    total.value = res.data.total
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryParams.tableName = ''; handleQuery() }

// ==================== 导入 ====================
const loadDbTables = async () => {
  dbLoading.value = true
  try {
    const res = await listDbTable(dbSearchName.value)
    dbTableList.value = (res.data as DbTableInfo[]) || []
  } catch { dbTableList.value = [] } finally {
    dbLoading.value = false
  }
}

const handleDbSelectionChange = (selection: DbTableInfo[]) => {
  selectedDbTables.value = selection
}

const handleImport = async () => {
  importLoading.value = true
  try {
    const names = selectedDbTables.value.map(t => t.tableName)
    await importTable(names)
    ElMessage.success(`成功导入 ${names.length} 张表`)
    showImportDialog.value = false
    getList()
  } catch { /* error handled */ } finally {
    importLoading.value = false
  }
}

// ==================== 预览 ====================
const handlePreview = async (row: GenTable) => {
  try {
    const res = await previewCode(row.id)
    previewCodes.value = (res.data as Record<string, string>) || {}
    const keys = Object.keys(previewCodes.value)
    previewActiveTab.value = keys.length > 0 ? keys[0] : ''
    showPreviewDialog.value = true
  } catch { /* error handled */ }
}

const getTabLabel = (fileName: string) => {
  const parts = fileName.split('/')
  return parts[parts.length - 1]
}

// ==================== 下载 ====================
const handleDownload = (row: GenTable) => {
  downloadCode(row.id)
}

// ==================== 编辑 ====================
const handleEdit = async (row: GenTable) => {
  try {
    const res = await getGenTable(row.id)
    editForm.value = res.data as GenTable
    showEditDialog.value = true
  } catch { /* error handled */ }
}

const handleSaveEdit = async () => {
  editLoading.value = true
  try {
    await updateGenTable(editForm.value)
    ElMessage.success('保存成功')
    showEditDialog.value = false
    getList()
  } catch { /* error handled */ } finally {
    editLoading.value = false
  }
}

// ==================== 删除 ====================
const handleDelete = async (row: GenTable) => {
  await ElMessageBox.confirm(`确定删除表 [${row.tableName}] 的生成配置吗?`, '提示', { type: 'warning' })
  await deleteGenTable(row.id)
  ElMessage.success('删除成功')
  getList()
}

onMounted(() => getList())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.code-preview {
  max-height: 60vh;
  overflow: auto;
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;

  code { font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace; }
}
</style>
