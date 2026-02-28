<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="岗位编码" prop="postCode">
          <el-input v-model="queryParams.postCode" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="queryParams.postName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
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
          <span>岗位列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增岗位</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="postList">
        <el-table-column label="岗位ID" prop="postId" width="80" align="center" />
        <el-table-column label="岗位编码" prop="postCode" width="150" />
        <el-table-column label="岗位名称" prop="postName" width="200" />
        <el-table-column label="排序" prop="postSort" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="form.postName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位编码" prop="postCode">
          <el-input v-model="form.postCode" placeholder="请输入岗位编码" />
        </el-form-item>
        <el-form-item label="排序" prop="postSort">
          <el-input-number v-model="form.postSort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listPost, getPost, addPost, updatePost, deletePost, type Post, type PostForm } from '@/api/system/post'

const loading = ref(false)
const submitLoading = ref(false)
const postList = ref<Post[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive({ postCode: '', postName: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<PostForm>({ postName: '', postCode: '', postSort: 0, status: 0, remark: '' })

const rules: FormRules = {
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listPost(queryParams)
    const data = (res as any).data
    postList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.postCode = ''; queryParams.postName = ''; queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增岗位'
  dialogVisible.value = true
}

async function handleEdit(row: Post) {
  resetForm()
  dialogTitle.value = '编辑岗位'
  try {
    const res = await getPost(row.postId)
    const data = (res as any).data
    Object.assign(form, { postId: data.postId, postName: data.postName, postCode: data.postCode, postSort: data.postSort, status: data.status, remark: data.remark })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Post) {
  try {
    await ElMessageBox.confirm(`确认删除岗位"${row.postName}"？`, '提示', { type: 'warning' })
    await deletePost(row.postId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.postId) {
      await updatePost(form)
      ElMessage.success('修改成功')
    } else {
      await addPost(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.postId = undefined; form.postName = ''; form.postCode = ''; form.postSort = 0; form.status = 0; form.remark = ''
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
