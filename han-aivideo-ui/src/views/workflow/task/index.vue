<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="流程名称" prop="processDefinitionName">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
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
          <span>待办任务</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList">
        <el-table-column label="任务名称" prop="taskName" min-width="150" show-overflow-tooltip />
        <el-table-column label="所属流程" prop="processDefinitionName" min-width="150" show-overflow-tooltip />
        <el-table-column label="业务标识" prop="businessKey" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.businessKey || '-' }}</template>
        </el-table-column>
        <el-table-column label="办理人" prop="assigneeName" min-width="100">
          <template #default="{ row }">{{ row.assigneeName || row.assignee || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="到期时间" prop="dueDate" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row.dueDate) }">{{ row.dueDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Check" @click="handleComplete(row)">办理</el-button>
            <el-button type="warning" link :icon="Right" @click="handleTransfer(row)">转办</el-button>
            <el-button type="info" link :icon="Switch" @click="handleDelegate(row)">委派</el-button>
            <el-button type="danger" link :icon="RefreshLeft" @click="handleRevoke(row)">撤回</el-button>
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

    <!-- 办理对话框 -->
    <el-dialog v-model="completeVisible" title="办理任务" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="completeFormRef" :model="completeForm" label-width="100px">
        <el-form-item label="任务名称">
          <span>{{ currentTask?.taskName }}</span>
        </el-form-item>
        <el-form-item label="所属流程">
          <span>{{ currentTask?.processDefinitionName }}</span>
        </el-form-item>
        <el-form-item label="审批意见" prop="comment">
          <el-input v-model="completeForm.comment" type="textarea" :rows="4" placeholder="请输入审批意见" />
        </el-form-item>
        <el-form-item label="审批结果">
          <el-radio-group v-model="completeForm.approved">
            <el-radio :value="true">同意</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCompleteSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 转办对话框 -->
    <el-dialog v-model="transferVisible" title="转办任务" width="45%" class="dialog-sm" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="转办人">
          <el-input v-model="transferUserId" placeholder="请输入转办人用户ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleTransferSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 委派对话框 -->
    <el-dialog v-model="delegateVisible" title="委派任务" width="45%" class="dialog-sm" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="委派人">
          <el-input v-model="delegateUserId" placeholder="请输入委派人用户ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleDelegateSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Check, Right, Switch, RefreshLeft } from '@element-plus/icons-vue'
import {
  listTodoTask, completeTask, transferTask, delegateTask, revokeTask,
  type TaskItem, type TaskQuery
} from '@/api/workflow'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const taskList = ref<TaskItem[]>([])
const total = ref(0)
const completeVisible = ref(false)
const transferVisible = ref(false)
const delegateVisible = ref(false)
const submitLoading = ref(false)
const currentTask = ref<TaskItem | null>(null)
const transferUserId = ref('')
const delegateUserId = ref('')

const queryFormRef = ref<FormInstance>()
const completeFormRef = ref<FormInstance>()

const queryParams = reactive<TaskQuery>({
  pageNum: 1,
  pageSize: 10,
  taskName: undefined,
  processDefinitionName: undefined
})

const completeForm = reactive({
  comment: '',
  approved: true
})

const isOverdue = (dueDate?: string) => {
  if (!dueDate) return false
  return new Date(dueDate) < new Date()
}

const getList = async () => {
  loading.value = true
  try {
    const res = await listTodoTask(queryParams)
    taskList.value = res.data.rows
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

const handleComplete = (row: TaskItem) => {
  currentTask.value = row
  completeForm.comment = ''
  completeForm.approved = true
  completeVisible.value = true
}

const handleCompleteSubmit = async () => {
  if (!currentTask.value) return
  submitLoading.value = true
  try {
    await completeTask({
      taskId: currentTask.value.taskId,
      comment: completeForm.comment,
      variables: { approved: completeForm.approved }
    })
    ElMessage.success('办理成功')
    completeVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleTransfer = (row: TaskItem) => {
  currentTask.value = row
  transferUserId.value = ''
  transferVisible.value = true
}

const handleTransferSubmit = async () => {
  if (!currentTask.value || !transferUserId.value) {
    ElMessage.warning('请输入转办人')
    return
  }
  submitLoading.value = true
  try {
    await transferTask(currentTask.value.taskId, transferUserId.value)
    ElMessage.success('转办成功')
    transferVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelegate = (row: TaskItem) => {
  currentTask.value = row
  delegateUserId.value = ''
  delegateVisible.value = true
}

const handleDelegateSubmit = async () => {
  if (!currentTask.value || !delegateUserId.value) {
    ElMessage.warning('请输入委派人')
    return
  }
  submitLoading.value = true
  try {
    await delegateTask(currentTask.value.taskId, delegateUserId.value)
    ElMessage.success('委派成功')
    delegateVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleRevoke = async (row: TaskItem) => {
  await ElMessageBox.confirm('确定撤回该任务吗?', '提示', { type: 'warning' })
  await revokeTask(row.taskId)
  ElMessage.success('撤回成功')
  getList()
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

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.text-danger {
  color: #f56c6c;
}
</style>
