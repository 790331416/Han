<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="menuList" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column label="菜单名称" prop="menuName" width="200" />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon && row.icon !== '#'"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="80" align="center" />
        <el-table-column label="权限标识" prop="perms" width="200" />
        <el-table-column label="组件路径" prop="component" width="200" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Plus">新增</el-button>
            <el-button type="primary" link :icon="Edit">编辑</el-button>
            <el-button type="danger" link :icon="Delete">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

const loading = ref(false)
const menuList = ref([
  {
    id: 1, menuName: '系统管理', icon: 'Setting', sort: 1, perms: null, component: null, status: 0,
    children: [
      { id: 100, menuName: '用户管理', icon: 'User', sort: 1, perms: 'system:user:list', component: 'system/user/index', status: 0 },
      { id: 101, menuName: '角色管理', icon: 'UserFilled', sort: 2, perms: 'system:role:list', component: 'system/role/index', status: 0 },
      { id: 102, menuName: '菜单管理', icon: 'Menu', sort: 3, perms: 'system:menu:list', component: 'system/menu/index', status: 0 }
    ]
  }
])

const handleAdd = () => {
  // TODO: 实现新增
}

onMounted(() => {
  // TODO: 获取列表
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
