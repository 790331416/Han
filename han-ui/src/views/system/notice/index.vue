<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="queryParams.noticeTitle" placeholder="请输入公告标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-select v-model="queryParams.noticeType">
            <el-option label="全部" value="" />
            <el-option label="通知" value="1" />
            <el-option label="公告" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status">
            <el-option label="全部" value="" />
            <el-option label="正常" :value="0" />
            <el-option label="关闭" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>通知公告</span>
          <div class="table-operations">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="noticeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="公告标题" prop="noticeTitle" min-width="200" show-overflow-tooltip />
        <el-table-column label="公告类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.noticeType === '1' ? 'warning' : 'success'">{{ row.noticeType === '1' ? '通知' : '公告' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建者" prop="createName" min-width="120" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleDetail(row)">查看</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="70%" class="notice-dialog" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="noticeTitle">
          <el-input v-model="form.noticeTitle" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-select v-model="form.noticeType" placeholder="请选择类型">
            <el-option label="通知" value="1" />
            <el-option label="公告" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="noticeContent">
          <div style="border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; width: 100%">
            <Toolbar :editor="editorRef" :defaultConfig="toolbarConfig" style="border-bottom: 1px solid #e5e7eb" />
            <Editor v-model="form.noticeContent" :defaultConfig="editorConfig" style="height: 300px; overflow-y: hidden" @onCreated="handleEditorCreated" />
          </div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="公告详情" width="70%" class="notice-dialog" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ detailData.noticeTitle }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="detailData.noticeType === '1' ? 'warning' : 'success'">{{ detailData.noticeType === '1' ? '通知' : '公告' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.status === 0 ? 'success' : 'info'">{{ detailData.status === 0 ? '正常' : '关闭' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建者">{{ detailData.createName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div class="notice-content" v-html="sanitizeHtml(detailData.noticeContent || '')"></div>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2" v-if="detailData.remark">{{ detailData.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'
import { listNotice, getNotice, addNotice, updateNotice, deleteNotice, deleteNotices } from '@/api/system/notice'
import type { Notice, NoticeForm } from '@/api/system/notice'
import { sanitizeHtml } from '@/utils/sanitize-html'
import type { FormInstance, FormRules } from 'element-plus'
import { sanitizeHtml } from '@/utils/sanitize-html'

const loading = ref(false)
const noticeList = ref<Notice[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const submitLoading = ref(false)
const detailData = ref<Notice>({} as Notice)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const editorRef = shallowRef<IDomEditor>()
const toolbarConfig = {}
const editorConfig = { placeholder: '请输入公告内容...' }
const handleEditorCreated = (editor: IDomEditor) => { editorRef.value = editor }
onBeforeUnmount(() => { editorRef.value?.destroy() })

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  noticeTitle: undefined as string | undefined,
  noticeType: '' as any,
  status: '' as any
})

const form = reactive<NoticeForm>({
  id: undefined,
  noticeTitle: '',
  noticeType: '1',
  noticeContent: '',
  status: 0,
  remark: ''
})

const rules: FormRules = {
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }]
}

const dialogTitle = computed(() => form.id ? '编辑公告' : '新增公告')

const getList = async () => {
  loading.value = true
  try {
    const res = await listNotice(queryParams)
    noticeList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handleSelectionChange = (selection: Notice[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const resetForm = () => {
  form.id = undefined
  form.noticeTitle = ''
  form.noticeType = '1'
  form.noticeContent = ''
  form.status = 0
  form.remark = ''
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: Notice) => {
  resetForm()
  const res = await getNotice(row.id)
  Object.assign(form, res.data)
  dialogVisible.value = true
}

const handleDetail = async (row: Notice) => {
  const res = await getNotice(row.id)
  detailData.value = res.data
  detailVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await updateNotice(form)
      ElMessage.success('修改成功')
    } else {
      await addNotice(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: Notice) => {
  await ElMessageBox.confirm(`确定删除公告"${row.noticeTitle}"吗?`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('删除成功')
  getList()
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}条公告吗?`, '提示', { type: 'warning' })
  await deleteNotices(selectedIds.value)
  ElMessage.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.notice-content {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.8;
}
</style>
