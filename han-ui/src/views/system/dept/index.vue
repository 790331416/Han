<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="部门名称">
          <el-input v-model="queryParams.deptName" placeholder="请输入" clearable @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status">
            <el-option label="全部" value="" />
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="getList">搜索</el-button>
          <el-button :icon="Refresh" @click="queryParams.deptName = ''; queryParams.status = ''; getList()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>部门列表</span>
          <div>
            <el-button :icon="isExpand ? 'ArrowUp' : 'ArrowDown'" @click="toggleExpand">{{ isExpand ? '折叠' : '展开' }}</el-button>
            <el-button v-perm="'system:dept:add'" type="primary" :icon="Plus" @click="handleAdd()">新增部门</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="deptList" row-key="id" :default-expand-all="isExpand" :tree-props="{ children: 'children' }">
        <el-table-column label="部门名称" prop="deptName" min-width="200" />
        <el-table-column label="负责人" prop="leaderName" min-width="120">
          <template #default="{ row }">{{ row.leaderName || '—' }}</template>
        </el-table-column>
        <el-table-column label="联系电话" prop="phone" min-width="150" />
        <el-table-column label="排序" prop="sort" width="70" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button v-perm="'system:dept:add'" type="primary" link :icon="Plus" @click="handleAdd(row.id)">新增</el-button>
            <el-button v-perm="['system:dept:edit', 'system:dept:query']" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-perm="'system:dept:remove'" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="form.parentId" :data="deptOptions" node-key="id"
            :props="{ label: 'deptName', children: 'children' }"
            check-strictly filterable placeholder="选择上级部门" style="width: 100%" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderId" filterable clearable placeholder="搜索选择负责人"
            style="width: 100%" @change="onLeaderChange">
            <el-option v-for="u in userList" :key="String(u.userId)" :label="u.nickname" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
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
import { getDeptTree, getDept, addDept, updateDept, deleteDept, type Dept, type DeptForm } from '@/api/system/dept'
import { listSimpleUser, type SimpleUser } from '@/api/system/user'

const loading = ref(false)
const submitLoading = ref(false)
const isExpand = ref(true)
const deptList = ref<Dept[]>([])
const deptOptions = ref<Dept[]>([])
const userList = ref<SimpleUser[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ deptName: '', status: '' as any })

const form = reactive<DeptForm>({ deptId: undefined, parentId: 0, deptName: '', leaderId: undefined, phone: '', email: '', sort: 0, status: 0 })

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

onMounted(() => {
  getList()
  loadUserList()
})

async function getList() {
  loading.value = true
  try {
    const res = await getDeptTree(queryParams)
    deptList.value = (res as any).data || []
    deptOptions.value = [{ id: 0, parentId: -1, deptName: '顶级部门', sort: 0, status: 0, children: deptList.value } as Dept]
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

async function loadUserList() {
  try {
    const res = await listSimpleUser()
    userList.value = (res as any).data || []
  } catch { /* ignore */ }
}

function onLeaderChange(userId: string | number | undefined) {
  if (!userId) {
    form.phone = ''
    form.email = ''
    return
  }
  fillLeaderContact(userId)
}

function fillLeaderContact(leaderId: string | number) {
  const user = userList.value.find(u => String(u.userId) === String(leaderId))
  if (user) {
    form.phone = user.phone || ''
    form.email = user.email || ''
  }
}

function toggleExpand() { isExpand.value = !isExpand.value; getList() }

function handleAdd(parentId?: number) {
  resetForm()
  form.parentId = parentId || 0
  dialogTitle.value = '新增部门'
  dialogVisible.value = true
}

async function handleEdit(row: Dept) {
  resetForm()
  dialogTitle.value = '编辑部门'
  try {
    const res = await getDept(row.id)
    const d = (res as any).data
    Object.assign(form, {
      deptId: d.id,
      parentId: Number(d.parentId) || 0,
      deptName: d.deptName,
      leaderId: d.leaderId || undefined,
      phone: d.phone,
      email: d.email,
      sort: d.sort,
      status: d.status
    })
    // 回显负责人的最新联系方式
    if (d.leaderId) {
      fillLeaderContact(d.leaderId)
    }
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Dept) {
  try {
    await ElMessageBox.confirm(`确认删除部门"${row.deptName}"？`, '提示', { type: 'warning' })
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.deptId) {
      await updateDept(form)
      ElMessage.success('修改成功')
    } else {
      await addDept(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.deptId = undefined; form.parentId = 0; form.deptName = ''; form.leaderId = undefined; form.phone = ''; form.email = ''; form.sort = 0; form.status = 0
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>
