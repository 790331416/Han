<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>部门列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="deptList" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column label="部门名称" prop="deptName" width="250" />
        <el-table-column label="负责人" prop="leader" width="150" />
        <el-table-column label="联系电话" prop="phone" width="150" />
        <el-table-column label="排序" prop="sort" width="80" align="center" />
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
const deptList = ref([
  {
    id: 100, deptName: 'XuMan科技', leader: '管理员', phone: '13800000000', sort: 0, status: 0, createTime: '2024-01-01 00:00:00',
    children: [
      { id: 101, deptName: '研发部门', leader: '张三', phone: '13800000001', sort: 1, status: 0, createTime: '2024-01-01 00:00:00' },
      { id: 102, deptName: '产品部门', leader: '李四', phone: '13800000002', sort: 2, status: 0, createTime: '2024-01-01 00:00:00' },
      { id: 103, deptName: '运营部门', leader: '王五', phone: '13800000003', sort: 3, status: 0, createTime: '2024-01-01 00:00:00' }
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
