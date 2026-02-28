<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="套餐名称" prop="packageName">
          <el-input v-model="queryParams.packageName" placeholder="请输入套餐名称" clearable @keyup.enter="handleQuery" />
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

    <!-- 数据列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>套餐列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="packageList">
        <el-table-column label="套餐ID" prop="packageId" width="80" />
        <el-table-column label="套餐名称" prop="packageName" />
        <el-table-column label="关联租户数" prop="tenantCount" width="120" align="center" />
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
        <el-table-column label="创建时间" prop="createTime" width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link :icon="Menu" @click="handleMenus(row)">菜单</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="套餐名称" prop="packageName">
          <el-input v-model="form.packageName" placeholder="请输入套餐名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 菜单分配对话框 -->
    <el-dialog v-model="menuDialogVisible" title="分配菜单" width="500px" destroy-on-close>
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="checkedMenuIds"
        :default-expand-all="true"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuLoading" @click="handleMenuSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Menu } from '@element-plus/icons-vue'
import {
  listTenantPackage, getTenantPackage, addTenantPackage, updateTenantPackage,
  deleteTenantPackage, changeTenantPackageStatus, getPackageMenus, updatePackageMenus
} from '@/api/system/tenantPackage'
import { getMenuTree } from '@/api/system/menu'
import type { TenantPackage, TenantPackageForm } from '@/api/system/tenantPackage'
import type { FormInstance, FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'

const loading = ref(false)
const packageList = ref<TenantPackage[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const submitLoading = ref(false)
const menuLoading = ref(false)
const menuTree = ref<any[]>([])
const checkedMenuIds = ref<number[]>([])
const currentPackageId = ref<number>(0)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const menuTreeRef = ref<InstanceType<typeof ElTree>>()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  packageName: undefined as string | undefined,
  status: undefined as number | undefined
})

const form = reactive<TenantPackageForm>({
  packageId: undefined,
  packageName: '',
  status: 0,
  remark: ''
})

const rules: FormRules = {
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }]
}

const dialogTitle = computed(() => form.packageId ? '编辑套餐' : '新增套餐')

const getList = async () => {
  loading.value = true
  try {
    const res = await listTenantPackage(queryParams)
    packageList.value = res.data.rows
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

const resetForm = () => {
  form.packageId = undefined
  form.packageName = ''
  form.status = 0
  form.remark = ''
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: TenantPackage) => {
  resetForm()
  const res = await getTenantPackage(row.packageId)
  Object.assign(form, {
    packageId: res.data.packageId,
    packageName: res.data.packageName,
    status: res.data.status,
    remark: res.data.remark
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.packageId) {
      await updateTenantPackage(form)
      ElMessage.success('修改成功')
    } else {
      await addTenantPackage(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: TenantPackage) => {
  if (row.tenantCount > 0) {
    ElMessage.warning('该套餐下还有关联租户，无法删除')
    return
  }
  await ElMessageBox.confirm(`确定删除套餐"${row.packageName}"吗?`, '提示', { type: 'warning' })
  await deleteTenantPackage(row.packageId)
  ElMessage.success('删除成功')
  getList()
}

const handleStatusChange = async (row: TenantPackage) => {
  const text = row.status === 0 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${text}套餐"${row.packageName}"吗?`, '提示', { type: 'warning' })
    await changeTenantPackageStatus(row.packageId, row.status)
    ElMessage.success(`${text}成功`)
  } catch {
    row.status = row.status === 0 ? 1 : 0
  }
}

const handleMenus = async (row: TenantPackage) => {
  currentPackageId.value = row.packageId
  try {
    const [menuRes, checkedRes] = await Promise.all([
      getMenuTree(),
      getPackageMenus(row.packageId)
    ])
    menuTree.value = menuRes.data || []
    checkedMenuIds.value = checkedRes.data || []
    menuDialogVisible.value = true
  } catch {
    ElMessage.error('获取菜单数据失败')
  }
}

const handleMenuSubmit = async () => {
  menuLoading.value = true
  try {
    const checkedKeys = menuTreeRef.value?.getCheckedKeys(false) as number[] || []
    const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys() as number[] || []
    const allKeys = [...checkedKeys, ...halfCheckedKeys]
    await updatePackageMenus(currentPackageId.value, allKeys)
    ElMessage.success('菜单分配成功')
    menuDialogVisible.value = false
  } finally {
    menuLoading.value = false
  }
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
