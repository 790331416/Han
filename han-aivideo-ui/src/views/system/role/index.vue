<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="queryParams.roleName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="queryParams.roleKey" placeholder="请输入" clearable @keyup.enter="handleQuery" />
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
          <span>角色列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增角色</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="roleList">
        <el-table-column label="角色名称" prop="roleName" min-width="150" show-overflow-tooltip />
        <el-table-column label="权限字符" prop="roleKey" min-width="150" show-overflow-tooltip />
        <el-table-column label="排序" prop="roleSort" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 0" @change="(val: any) => handleStatusChange(row, !!val)" :disabled="row.id === 1" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)" :disabled="row.id === 1">编辑</el-button>
            <el-button type="primary" link :icon="User" @click="handleAuthUser(row)" :disabled="row.id === 1">分配用户</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)" :disabled="row.id === 1">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="65%" class="dialog-lg" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="请输入权限字符" />
        </el-form-item>
        <el-form-item label="排序" prop="roleSort">
          <el-input-number v-model="form.roleSort" :min="0" />
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
        <el-form-item label="菜单权限">
          <div style="border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; width: 100%; max-height: 450px; overflow-y: auto;">
            <el-tree ref="menuTreeRef" :data="menuTreeData" show-checkbox node-key="id" :props="{ label: 'menuName', children: 'children' }" :default-checked-keys="checkedMenuIds" />
          </div>
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
import { Search, Refresh, Plus, Edit, Delete, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { listRole, getRole, addRole, updateRole, deleteRole, changeRoleStatus, getRoleMenuIds, type Role, type RoleForm } from '@/api/system/role'
import { getMenuTree, type Menu } from '@/api/system/menu'
import type { ElTree } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const roleList = ref<Role[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const menuTreeData = ref<Menu[]>([])
const checkedMenuIds = ref<(string | number)[]>([])

const queryParams = reactive({ roleName: '', roleKey: '', status: '' as any, pageNum: 1, pageSize: 10 })

const form = reactive<RoleForm>({ roleId: undefined, roleName: '', roleKey: '', roleSort: 0, status: 0, remark: '' })

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入权限字符', trigger: 'blur' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listRole(queryParams)
    const data = (res as any).data
    roleList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.roleName = ''; queryParams.roleKey = ''; queryParams.status = undefined
  handleQuery()
}

async function handleAdd() {
  resetForm()
  checkedMenuIds.value = []
  dialogTitle.value = '新增角色'
  await loadMenuTree()
  dialogVisible.value = true
}

async function handleEdit(row: Role) {
  resetForm()
  dialogTitle.value = '编辑角色'
  await loadMenuTree()
  try {
    const res = await getRole(row.id)
    const data = (res as any).data
    Object.assign(form, { roleId: data.id, roleName: data.roleName, roleKey: data.roleKey, roleSort: data.roleSort, status: data.status, remark: data.remark })
    const menuRes = await getRoleMenuIds(row.id)
    const allMenuIds: (string | number)[] = (menuRes as any).data || []
    checkedMenuIds.value = filterLeafIds(allMenuIds, menuTreeData.value)
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function loadMenuTree() {
  try {
    const res = await getMenuTree()
    menuTreeData.value = (res as any).data || []
  } catch { /* ignore */ }
}

async function handleDelete(row: Role) {
  try {
    await ElMessageBox.confirm(`确认删除角色"${row.roleName}"？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function handleStatusChange(row: Role, val: boolean) {
  const newStatus = val ? 0 : 1
  try {
    await changeRoleStatus(row.id, newStatus)
    ElMessage.success(val ? '启用成功' : '停用成功')
    getList()
  } catch { /* ignore */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const checked = menuTreeRef.value?.getCheckedKeys(false) as (string | number)[] || []
    const halfChecked = menuTreeRef.value?.getHalfCheckedKeys() as (string | number)[] || []
    const menuIds = [...checked, ...halfChecked]
    const submitData = { ...form, menuIds }
    if (form.roleId) {
      await updateRole(submitData)
      ElMessage.success('修改成功')
    } else {
      await addRole(submitData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.roleId = undefined; form.roleName = ''; form.roleKey = ''; form.roleSort = 0; form.status = 0; form.remark = ''
}

function filterLeafIds(ids: (string | number)[], tree: Menu[]): (string | number)[] {
  const parentIds = new Set<string>()
  function collectParents(nodes: Menu[]) {
    for (const node of nodes) {
      if (node.children && node.children.length > 0) {
        parentIds.add(String(node.id))
        collectParents(node.children)
      }
    }
  }
  collectParents(tree)
  return ids.filter(id => !parentIds.has(String(id)))
}

function handleAuthUser(row: Role) {
  router.push({ path: '/system/role/authUser', query: { roleId: row.id } })
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
