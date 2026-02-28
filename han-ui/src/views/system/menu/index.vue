<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="菜单名称">
          <el-input v-model="queryParams.menuName" placeholder="请输入" clearable @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="getList">搜索</el-button>
          <el-button :icon="Refresh" @click="queryParams.menuName = ''; queryParams.status = undefined; getList()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单列表</span>
          <div>
            <el-button :icon="isExpand ? 'ArrowUp' : 'ArrowDown'" @click="toggleExpand">{{ isExpand ? '折叠' : '展开' }}</el-button>
            <el-button type="primary" :icon="Plus" @click="handleAdd()">新增菜单</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="menuList" row-key="id" :default-expand-all="isExpand" :tree-props="{ children: 'children' }">
        <el-table-column label="菜单名称" prop="menuName" width="220" />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon && row.icon !== '#'"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="70" align="center" />
        <el-table-column label="权限标识" prop="perms" min-width="180" show-overflow-tooltip />
        <el-table-column label="组件路径" prop="component" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 'M'" type="primary" size="small">目录</el-tag>
            <el-tag v-else-if="row.menuType === 'C'" type="success" size="small">菜单</el-tag>
            <el-tag v-else type="warning" size="small">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Plus" @click="handleAdd(row.id)" v-if="row.menuType !== 'F'">新增</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="上级菜单">
              <el-tree-select v-model="form.parentId" :data="menuOptions" node-key="id" :props="{ label: 'menuName', children: 'children' }"
                check-strictly filterable placeholder="选择上级菜单" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="form.menuType">
                <el-radio value="M">目录</el-radio>
                <el-radio value="C">菜单</el-radio>
                <el-radio value="F">按钮</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType !== 'F'">
            <el-form-item label="路由地址" prop="path">
              <el-input v-model="form.path" placeholder="请输入路由地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType === 'C'">
            <el-form-item label="组件路径" prop="component">
              <el-input v-model="form.component" placeholder="请输入组件路径" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限标识">
              <el-input v-model="form.perms" placeholder="如: system:user:list" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType !== 'F'">
            <el-form-item label="图标">
              <IconSelect v-model="form.icon" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">正常</el-radio>
                <el-radio :value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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
import { listMenu, getMenu, addMenu, updateMenu, deleteMenu, type Menu, type MenuForm } from '@/api/system/menu'
import IconSelect from '@/components/IconSelect/index.vue'

const loading = ref(false)
const submitLoading = ref(false)
const isExpand = ref(true)
const menuList = ref<Menu[]>([])
const menuOptions = ref<Menu[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ menuName: '', status: undefined as number | undefined })

const form = reactive<MenuForm>({ parentId: 0, menuName: '', menuType: 'M', path: '', component: '', perms: '', icon: '', sort: 0, visible: 0, status: 0, isFrame: 1, isCache: 0 })

const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listMenu(queryParams)
    menuList.value = (res as any).data || []
    menuOptions.value = [{ id: 0, parentId: -1, menuName: '主目录', menuType: 'M', sort: 0, visible: 0, status: 0, isFrame: 1, isCache: 0, children: menuList.value } as Menu]
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function toggleExpand() { isExpand.value = !isExpand.value; getList() }

function handleAdd(parentId?: number) {
  resetForm()
  form.parentId = parentId || 0
  dialogTitle.value = '新增菜单'
  dialogVisible.value = true
}

async function handleEdit(row: Menu) {
  resetForm()
  dialogTitle.value = '编辑菜单'
  try {
    const res = await getMenu(row.id)
    const d = (res as any).data
    Object.assign(form, { id: d.id, parentId: d.parentId, menuName: d.menuName, menuType: d.menuType, path: d.path, component: d.component, perms: d.perms, icon: d.icon, sort: d.sort, visible: d.visible, status: d.status, isFrame: d.isFrame, isCache: d.isCache })
  } catch { /* ignore */ }
  dialogVisible.value = true
}

async function handleDelete(row: Menu) {
  try {
    await ElMessageBox.confirm(`确认删除菜单"${row.menuName}"？`, '提示', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (form.id) {
      await updateMenu(form)
      ElMessage.success('修改成功')
    } else {
      await addMenu(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.id = undefined; form.parentId = 0; form.menuName = ''; form.menuType = 'M'; form.path = ''; form.component = ''; form.perms = ''; form.icon = ''; form.sort = 0; form.visible = 0; form.status = 0; form.isFrame = 1; form.isCache = 0
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>
