<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable>
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

    <!-- 操作按钮 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <div class="table-operations">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
          </div>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="用户ID" prop="userId" width="80" />
        <el-table-column label="用户名" prop="username" width="120" />
        <el-table-column label="昵称" prop="nickname" width="120" />
        <el-table-column label="部门" prop="deptName" width="150" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="0"
              :inactive-value="1"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link :icon="Key" @click="handleResetPwd(row)">重置</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" :disabled="!!form.userId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20" v-if="!form.userId">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别" prop="sex">
              <el-radio-group v-model="form.sex">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
                <el-radio :value="0">未知</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">正常</el-radio>
                <el-radio :value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import { listUser, getUser, addUser, updateUser, deleteUser, deleteUsers, changeUserStatus, resetUserPwd } from '@/api/system/user'
import type { User, UserQuery, UserForm } from '@/api/system/user'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const userList = ref<User[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<UserQuery>({
  pageNum: 1,
  pageSize: 10,
  username: undefined,
  phone: undefined,
  status: undefined
})

const form = reactive<UserForm>({
  userId: undefined,
  username: '',
  nickname: '',
  password: '',
  phone: '',
  email: '',
  sex: 0,
  status: 0,
  remark: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度在2到30个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const dialogTitle = computed(() => form.userId ? '编辑用户' : '新增用户')

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await listUser(queryParams)
    userList.value = res.data.rows
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

// 多选
const handleSelectionChange = (selection: User[]) => {
  selectedIds.value = selection.map(item => item.userId)
}

// 新增
const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: User) => {
  resetForm()
  const res = await getUser(row.userId)
  Object.assign(form, res.data)
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  form.userId = undefined
  form.username = ''
  form.nickname = ''
  form.password = ''
  form.phone = ''
  form.email = ''
  form.sex = 0
  form.status = 0
  form.remark = ''
}

// 提交
const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  submitLoading.value = true
  try {
    if (form.userId) {
      await updateUser(form)
      ElMessage.success('修改成功')
    } else {
      await addUser(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

// 删除
const handleDelete = async (row: User) => {
  await ElMessageBox.confirm(`确定删除用户"${row.username}"吗?`, '提示', { type: 'warning' })
  await deleteUser(row.userId)
  ElMessage.success('删除成功')
  getList()
}

// 批量删除
const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}个用户吗?`, '提示', { type: 'warning' })
  await deleteUsers(selectedIds.value)
  ElMessage.success('删除成功')
  getList()
}

// 状态修改
const handleStatusChange = async (row: User) => {
  const text = row.status === 0 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${text}用户"${row.username}"吗?`, '提示', { type: 'warning' })
    await changeUserStatus(row.userId, row.status)
    ElMessage.success(`${text}成功`)
  } catch {
    row.status = row.status === 0 ? 1 : 0
  }
}

// 重置密码
const handleResetPwd = async (row: User) => {
  const { value } = await ElMessageBox.prompt(`请输入"${row.username}"的新密码`, '重置密码', {
    inputPattern: /^.{6,20}$/,
    inputErrorMessage: '密码长度在6到20个字符'
  })
  await resetUserPwd(row.userId, value)
  ElMessage.success('重置成功')
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
</style>
