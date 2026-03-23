<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="queryParams.tenantName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="queryParams.contactName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status">
            <el-option label="全部" value="" />
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
          <span>租户列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增租户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tenantList">
        <el-table-column label="租户名称" prop="tenantName" min-width="180" show-overflow-tooltip />
        <el-table-column label="联系人" prop="contactName" min-width="120" show-overflow-tooltip />
        <el-table-column label="联系电话" prop="contactPhone" min-width="140" />
        <el-table-column label="套餐" prop="packageName" min-width="120" show-overflow-tooltip />
        <el-table-column label="用户数" width="100" align="center">
          <template #default="{ row }">
            {{ row.userCount || 0 }} / {{ row.userLimit === -1 ? '不限' : row.userLimit }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" prop="expireTime" min-width="180" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 0" @change="(val: any) => handleStatusChange(row, !!val)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" />
        <el-table-column label="操作" min-width="260">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link :icon="Key" @click="handleResetPwd(row)">重置密码</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="form.tenantName" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" placeholder="请输入联系邮箱" />
        </el-form-item>
        <el-form-item label="租户套餐" prop="packageId">
          <el-select v-model="form.packageId" placeholder="请选择套餐" clearable style="width: 100%">
            <el-option v-for="pkg in packageList" :key="pkg.packageId" :label="pkg.packageName" :value="pkg.packageId" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户数限制" prop="userLimit">
          <el-input-number v-model="form.userLimit" :min="-1" placeholder="-1为不限制" />
        </el-form-item>
        <el-form-item label="过期时间" prop="expireTime">
          <el-date-picker v-model="form.expireTime" type="datetime" placeholder="选择过期时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="绑定域名" prop="domain">
          <el-input v-model="form.domain" placeholder="请输入绑定域名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="!form.tenantId">
          <el-divider content-position="left">管理员账号</el-divider>
          <el-form-item label="管理员用户名" prop="adminUsername">
            <el-input v-model="form.adminUsername" placeholder="请输入管理员用户名" />
          </el-form-item>
          <el-form-item label="管理员密码" prop="adminPassword">
            <el-input v-model="form.adminPassword" placeholder="默认密码: admin123" show-password />
          </el-form-item>
        </template>
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
import { Search, Refresh, Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listTenant, getTenant, addTenant, updateTenant, deleteTenant, changeTenantStatus, getTenantAdminUser, type Tenant, type TenantForm } from '@/api/system/tenant'
import { listAllPackage, type TenantPackage } from '@/api/system/tenantPackage'
import { resetUserPwd } from '@/api/system/user'

const loading = ref(false)
const submitLoading = ref(false)
const tenantList = ref<Tenant[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const packageList = ref<TenantPackage[]>([])

const queryParams = reactive({ tenantName: '', contactName: '', status: '' as any, pageNum: 1, pageSize: 10 })

const form = reactive<TenantForm>({ tenantName: '', contactName: '', contactPhone: '', contactEmail: '', userLimit: -1, expireTime: '', domain: '', status: 0, remark: '', adminUsername: 'admin', adminPassword: 'admin123' })

const rules: FormRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员用户名', trigger: 'blur' }],
  adminPassword: [{ required: true, message: '请输入管理员密码', trigger: 'blur' }]
}

onMounted(() => {
  getList()
  loadPackageList()
})

async function loadPackageList() {
  try {
    const res = await listAllPackage()
    packageList.value = (res as any).data || []
  } catch { /* ignore */ }
}

async function getList() {
  loading.value = true
  try {
    const res = await listTenant(queryParams)
    const data = (res as any).data
    if (Array.isArray(data)) {
      tenantList.value = data
      total.value = data.length
    } else {
      tenantList.value = data?.records || data?.rows || []
      total.value = data?.total || 0
    }
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.tenantName = ''; queryParams.contactName = ''; queryParams.status = ''
  handleQuery()
}

async function handleAdd() {
  resetForm()
  dialogTitle.value = '新增租户'
  dialogVisible.value = true
}

async function handleEdit(row: Tenant) {
  resetForm()
  dialogTitle.value = '编辑租户'
  try {
    const res = await getTenant(row.tenantId)
    const d = (res as any).data
    Object.assign(form, { tenantId: d.tenantId, id: d.id, tenantName: d.tenantName, contactName: d.contactName, contactPhone: d.contactPhone, contactEmail: d.contactEmail, packageId: d.packageId, userLimit: d.userLimit, expireTime: d.expireTime, domain: d.domain, status: d.status, remark: d.remark })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Tenant) {
  try {
    const { value } = await ElMessageBox.prompt(
      `此操作将永久删除租户「${row.tenantName}」及其所有数据（用户、角色、部门、岗位），请输入租户名称确认：`,
      '危险操作',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'error', inputPattern: new RegExp(`^${row.tenantName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), inputErrorMessage: '租户名称不匹配', confirmButtonClass: 'el-button--danger' }
    )
    if (value !== row.tenantName) return
    await deleteTenant(row.tenantId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function handleResetPwd(row: Tenant) {
  try {
    const { value: newPwd } = await ElMessageBox.prompt(
      `重置租户「${row.tenantName}」管理员密码`,
      '重置密码',
      { confirmButtonText: '确定', cancelButtonText: '取消', inputValue: 'admin123', inputPlaceholder: '请输入新密码' }
    )
    if (!newPwd) return
    const adminRes = await getTenantAdminUser(row.tenantId)
    const adminUserId = (adminRes as any).data
    if (!adminUserId) {
      ElMessage.error('未找到该租户的管理员用户')
      return
    }
    await resetUserPwd(adminUserId, newPwd)
    ElMessage.success('重置密码成功')
  } catch { /* cancel */ }
}

async function handleStatusChange(row: Tenant, val: boolean) {
  const newStatus = val ? 0 : 1
  try {
    await changeTenantStatus(row.tenantId, newStatus)
    ElMessage.success(val ? '启用成功' : '停用成功')
    getList()
  } catch { /* ignore */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.tenantId) {
      await updateTenant(form)
      ElMessage.success('修改成功')
    } else {
      await addTenant(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.tenantId = undefined; form.tenantName = ''; form.contactName = ''; form.contactPhone = ''; form.contactEmail = ''; form.packageId = undefined; form.userLimit = -1; form.expireTime = ''; form.domain = ''; form.status = 0; form.remark = ''; form.adminUsername = 'admin'; form.adminPassword = 'admin123'
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
