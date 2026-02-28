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
          <span>租户列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增租户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tenantList">
        <el-table-column label="租户ID" prop="tenantId" width="80" align="center" />
        <el-table-column label="租户名称" prop="tenantName" width="180" />
        <el-table-column label="联系人" prop="contactName" width="120" />
        <el-table-column label="联系电话" prop="contactPhone" width="140" />
        <el-table-column label="套餐" prop="packageName" width="120" />
        <el-table-column label="用户数" width="100" align="center">
          <template #default="{ row }">
            {{ row.userCount || 0 }} / {{ row.userLimit === -1 ? '不限' : row.userLimit }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" prop="expireTime" width="180" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 0" @change="(val: any) => handleStatusChange(row, !!val)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
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
import { listTenant, getTenant, addTenant, updateTenant, deleteTenant, changeTenantStatus, type Tenant, type TenantForm } from '@/api/system/tenant'

const loading = ref(false)
const submitLoading = ref(false)
const tenantList = ref<Tenant[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ tenantName: '', contactName: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<TenantForm>({ tenantName: '', contactName: '', contactPhone: '', contactEmail: '', userLimit: -1, expireTime: '', domain: '', status: 0, remark: '' })

const rules: FormRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listTenant(queryParams)
    const data = (res as any).data
    tenantList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.tenantName = ''; queryParams.contactName = ''; queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
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
    Object.assign(form, { tenantId: d.tenantId, tenantName: d.tenantName, contactName: d.contactName, contactPhone: d.contactPhone, contactEmail: d.contactEmail, userLimit: d.userLimit, expireTime: d.expireTime, domain: d.domain, status: d.status, remark: d.remark })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Tenant) {
  try {
    await ElMessageBox.confirm(`确认删除租户"${row.tenantName}"？`, '提示', { type: 'warning' })
    await deleteTenant(row.tenantId)
    ElMessage.success('删除成功')
    getList()
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
  form.tenantId = undefined; form.tenantName = ''; form.contactName = ''; form.contactPhone = ''; form.contactEmail = ''; form.userLimit = -1; form.expireTime = ''; form.domain = ''; form.status = 0; form.remark = ''
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
