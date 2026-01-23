<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>角色列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="roleList">
        <el-table-column label="角色ID" prop="roleId" width="80" />
        <el-table-column label="角色名称" prop="roleName" width="150" />
        <el-table-column label="权限字符" prop="roleKey" width="150" />
        <el-table-column label="数据范围" prop="dataScope" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.dataScope === '1'">全部数据</el-tag>
            <el-tag v-else-if="row.dataScope === '2'" type="warning">自定义</el-tag>
            <el-tag v-else-if="row.dataScope === '3'" type="info">本部门</el-tag>
            <el-tag v-else-if="row.dataScope === '4'" type="info">部门及以下</el-tag>
            <el-tag v-else type="danger">仅本人</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit">编辑</el-button>
            <el-button type="primary" link :icon="User">分配用户</el-button>
            <el-button type="danger" link :icon="Delete">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete, User } from '@element-plus/icons-vue'

const loading = ref(false)
const roleList = ref([
  { roleId: 1, roleName: '超级管理员', roleKey: 'admin', dataScope: '1', status: 0, createTime: '2024-01-01 00:00:00' },
  { roleId: 2, roleName: '普通管理员', roleKey: 'common', dataScope: '2', status: 0, createTime: '2024-01-01 00:00:00' },
  { roleId: 3, roleName: '部门管理员', roleKey: 'dept_admin', dataScope: '3', status: 0, createTime: '2024-01-01 00:00:00' },
  { roleId: 4, roleName: '普通用户', roleKey: 'user', dataScope: '5', status: 0, createTime: '2024-01-01 00:00:00' }
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
