<template>
  <div class="app-container">
    <el-row v-if="canViewSystemUsers" :gutter="16">
      <!-- 左侧部门树 -->
      <el-col v-if="canViewDept" :span="4">
        <el-card shadow="never" class="dept-tree-card">
          <template #header><span class="font-medium text-gray-800">部门</span></template>
          <el-input v-model="deptFilterText" placeholder="搜索部门" clearable class="mb-3" />
          <el-tree
            ref="deptTreeRef"
            :data="deptTreeData"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            :filter-node-method="filterDeptNode"
            @node-click="handleDeptNodeClick"
          />
        </el-card>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :span="canViewDept ? 20 : 24">
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
          <el-select v-model="queryParams.status">
            <el-option label="全部" value="" />
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canSelectTenant" label="租户" prop="tenantId">
          <el-select v-model="queryParams.tenantId" placeholder="全部租户" clearable style="width: 180px" @change="handleQuery">
            <el-option v-for="t in tenantOptions" :key="t.tenantId" :label="t.tenantName" :value="t.tenantId" />
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
            <el-button v-if="canAdd" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
            <el-button v-if="canRemove" type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
            <el-dropdown v-if="canImport" trigger="click" class="ml-3">
              <el-button type="primary" plain :icon="Download">导入<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleImport">导入用户</el-dropdown-item>
                  <el-dropdown-item @click="handleDownloadTemplate">下载模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="canExport" type="warning" plain :icon="Upload" @click="handleExport">导出</el-button>
          </div>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
        <el-table-column v-if="canRemove" type="selection" width="55" align="center" />
        <el-table-column label="用户名" prop="username" min-width="120" show-overflow-tooltip />
        <el-table-column label="昵称" prop="nickname" min-width="120" show-overflow-tooltip />
        <el-table-column label="部门" prop="deptName" min-width="130" show-overflow-tooltip />
        <el-table-column label="手机号" prop="phone" min-width="130" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-if="canEdit"
              v-model="row.status"
              :active-value="0"
              :inactive-value="1"
              @change="handleStatusChange(row)"
            />
            <el-tag v-else size="small" :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column v-if="canEdit || canResetPwd || canRemove" label="操作" min-width="180">
          <template #default="{ row }">
            <el-button v-if="canEdit" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canResetPwd" type="primary" link :icon="Key" @click="handleResetPwd(row)">重置</el-button>
            <el-button v-if="canRemove" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

      </el-col>
    </el-row>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="55%" class="dialog-md" destroy-on-close>
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
              <el-input v-model="form.password" type="password" placeholder="须含大小写字母、数字、特殊字符，8位以上" show-password />
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
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option label="正常" :value="0" />
                <el-option label="停用" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item v-if="canViewDept" label="部门">
              <el-tree-select v-model="form.deptId" :data="deptTreeData" node-key="id" :props="{ label: 'deptName', children: 'children' }" check-strictly filterable placeholder="请选择部门" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="userStore.hasPermission('system:role:list')" label="角色">
              <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
                <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" :disabled="role.status === 1" />
              </el-select>
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
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Key, Download, Upload, ArrowDown } from '@element-plus/icons-vue'
import { listUser, getUser, addUser, updateUser, deleteUser, deleteUsers, changeUserStatus, resetUserPwd, exportUser, importTemplate, importUser } from '@/api/system/user'
import { listAllRoles, type Role } from '@/api/system/role'
import { getDeptTree, type Dept } from '@/api/system/dept'
import { listTenant, type Tenant } from '@/api/system/tenant'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/request'
import type { User, UserQuery, UserForm } from '@/api/system/user'
import type { FormInstance, FormRules } from 'element-plus'

const appStore = useAppStore()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes('admin'))
const canViewSystemUsers = computed(() => userStore.hasPermission('system:user:list'))
const canViewDept = computed(() => userStore.hasPermission('system:dept:list'))
const canAdd = computed(() => userStore.hasPermission('system:user:add'))
const canEdit = computed(() => userStore.hasPermission('system:user:edit'))
const canRemove = computed(() => userStore.hasPermission('system:user:remove'))
const canImport = computed(() => userStore.hasPermission('system:user:import'))
const canExport = computed(() => userStore.hasPermission('system:user:export'))
const canResetPwd = computed(() => userStore.hasPermission('system:user:resetPwd'))
const canSelectTenant = computed(() => isAdmin.value && appStore.isFeatureEnabled('tenantSelect'))
const tenantOptions = ref<Tenant[]>([])

const loading = ref(false)
const userList = ref<User[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const roleOptions = ref<Role[]>([])
const deptTreeData = ref<Dept[]>([])
const deptFilterText = ref('')
const deptTreeRef = ref<any>()

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<UserQuery & { deptId?: number; tenantId?: string | number }>({
  pageNum: 1,
  pageSize: 10,
  username: undefined,
  phone: undefined,
  status: '' as any,
  deptId: undefined,
  tenantId: undefined,
  accountType: 'SYSTEM'
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
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度8-32位', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?])/, message: '须含大小写字母、数字和特殊字符', trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => form.userId ? '编辑用户' : '新增用户')

// 获取列表
const getList = async () => {
  if (!canViewSystemUsers.value) return
  loading.value = true
  try {
    const res = await listUser(queryParams)
    userList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
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
  queryParams.deptId = undefined
  deptTreeRef.value?.setCurrentKey(null)
  handleQuery()
}

// 多选
const handleSelectionChange = (selection: User[]) => {
  selectedIds.value = selection.map(item => item.userId)
}

// ==================== 部门树筛选 ====================

watch(deptFilterText, (val) => {
  deptTreeRef.value?.filter(val)
})

const filterDeptNode = (value: string, data: any) => {
  if (!value) return true
  return data.deptName?.includes(value)
}

const handleDeptNodeClick = (data: any) => {
  queryParams.deptId = data.id
  handleQuery()
}

async function loadDeptTree() {
  if (!canViewDept.value) return
  try {
    const res = await getDeptTree()
    deptTreeData.value = (res as any).data || []
  } catch { /* ignore */ }
}

// 加载角色和部门选项
async function loadOptions() {
  const requests: Promise<unknown>[] = []
  if (userStore.hasPermission('system:role:list')) {
    requests.push(listAllRoles().then(res => { roleOptions.value = (res as any).data || [] }))
  }
  if (canViewDept.value) {
    requests.push(getDeptTree().then(res => { deptTreeData.value = (res as any).data || [] }))
  }
  await Promise.allSettled(requests)
}

// 新增
const handleAdd = async () => {
  resetForm()
  await loadOptions()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: User) => {
  resetForm()
  await loadOptions()
  const res = await getUser(row.userId)
  const d = res.data as any
  form.userId = d.userId
  form.username = d.username
  form.nickname = d.nickname
  form.phone = d.phone
  form.email = d.email
  form.sex = d.sex
  form.status = d.status
  form.remark = d.remark ?? ''
  form.deptId = d.deptId
  form.postIds = d.postIds
  form.roleIds = d.roleIds || []
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
  form.deptId = undefined
  form.roleIds = []
}

// 提交
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch { return }
  
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
  const result = await ElMessageBox.prompt(`请输入"${row.username}"的新密码`, '重置密码', {
    inputPattern: /^.{6,20}$/,
    inputErrorMessage: '密码长度在6到20个字符'
  }) as unknown as { value: string }
  const { value } = result
  await resetUserPwd(row.userId, value)
  ElMessage.success('重置成功')
}

// ==================== 导入导出 ====================

const downloadBlob = (data: any, filename: string) => {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

const handleExport = async () => {
  try {
    const res = await exportUser(queryParams)
    downloadBlob((res as any).data, '用户数据.xlsx')
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

const handleDownloadTemplate = async () => {
  try {
    const res = await importTemplate()
    downloadBlob((res as any).data, '用户导入模板.xlsx')
  } catch { ElMessage.error('下载模板失败') }
}

const handleImport = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const res = await importUser(file, false)
      ElMessage.success((res as any).data || '导入完成')
      getList()
    } catch { ElMessage.error('导入失败') }
  }
  input.click()
}

onMounted(async () => {
  await appStore.loadRuntimeCapabilities()
  getList()
  loadDeptTree()
  if (canSelectTenant.value) {
    try {
      const res = await listTenant({ pageNum: 1, pageSize: 200 })
      const data = (res as any).data
      tenantOptions.value = Array.isArray(data) ? data : (data?.rows || [])
    } catch { /* ignore */ }
  }
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

.dept-tree-card {
  min-height: calc(100vh - 120px);

  :deep(.el-card__body) {
    padding: 12px;
  }
}
</style>
