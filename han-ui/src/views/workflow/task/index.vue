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
            <el-button type="danger" link :icon="RefreshLeft" @click="handleUnclaim(row)">取消签收</el-button>
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
          <el-select
            v-model="transferUserId"
            placeholder="按用户名搜索并选择"
            filterable
            remote
            clearable
            style="width: 100%"
            :remote-method="searchUser"
            :loading="userLoading"
            data-testid="workflow-transfer-user"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="`${user.nickname || user.username}（${user.username}）`"
              :value="String(user.userId)"
            />
          </el-select>
          <div class="form-tip">转办后该任务会直接出现在对方的待办列表中。</div>
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
          <el-select
            v-model="delegateUserId"
            placeholder="按用户名搜索并选择"
            filterable
            remote
            clearable
            style="width: 100%"
            :remote-method="searchUser"
            :loading="userLoading"
            data-testid="workflow-delegate-user"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="`${user.nickname || user.username}（${user.username}）`"
              :value="String(user.userId)"
            />
          </el-select>
          <div class="form-tip">委派后由对方代办，办理完成会回到您这里。</div>
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
  listTodoTask, completeTask, transferTask, delegateTask, unclaimTask,
  type TaskItem, type TaskQuery
} from '@/api/workflow'
import { listUser, type User } from '@/api/system/user'
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
const userOptions = ref<User[]>([])
const userLoading = ref(false)

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

/** 办理人远程搜索：与流程发起页 (definition/index.vue) 的 searchAssignee 同一套写法 */
const searchUser = async (keyword: string) => {
  if (!keyword?.trim()) {
    userOptions.value = []
    return
  }
  userLoading.value = true
  try {
    const res = await listUser({ pageNum: 1, pageSize: 20, username: keyword.trim() })
    userOptions.value = (res as any).data?.rows || []
  } catch { userOptions.value = [] } finally {
    userLoading.value = false
  }
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
      // result 是后端 TaskCompleteDTO 的标准字段，会被写成 result 流程变量；
      // approved 保留是为了兼容已部署的、用 approved 做网关条件的流程定义。
      result: completeForm.approved ? 'pass' : 'reject',
      variables: { approved: completeForm.approved }
    })
    ElMessage.success('办理成功')
    completeVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    submitLoading.value = false
  }
}

const handleTransfer = (row: TaskItem) => {
  currentTask.value = row
  transferUserId.value = ''
  userOptions.value = []
  transferVisible.value = true
}

const handleTransferSubmit = async () => {
  if (!currentTask.value || !transferUserId.value) {
    ElMessage.warning('请选择转办人')
    return
  }
  submitLoading.value = true
  try {
    await transferTask(currentTask.value.taskId, transferUserId.value)
    ElMessage.success('转办成功')
    transferVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    submitLoading.value = false
  }
}

const handleDelegate = (row: TaskItem) => {
  currentTask.value = row
  delegateUserId.value = ''
  userOptions.value = []
  delegateVisible.value = true
}

const handleDelegateSubmit = async () => {
  if (!currentTask.value || !delegateUserId.value) {
    ElMessage.warning('请选择委派人')
    return
  }
  submitLoading.value = true
  try {
    await delegateTask(currentTask.value.taskId, delegateUserId.value)
    ElMessage.success('委派成功')
    delegateVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    submitLoading.value = false
  }
}

/**
 * 取消签收。
 *
 * 按钮原来写的是「撤回」，但接口 /workflow/task/revoke/{taskId} 内部执行的是 Flowable 的 unclaim，
 * 即把 assignee 置空。待办列表按 taskAssignee 查询且没有「认领候选任务」入口，
 * 所以清空 assignee 后任务会从所有人的待办里消失。这里按真实语义改名并把后果写进确认文案。
 */
const handleUnclaim = async (row: TaskItem) => {
  try {
    await ElMessageBox.confirm(
      '取消签收会清空该任务的办理人。当前系统没有「待认领任务」入口，取消后任务将不再出现在任何人的待办列表中，只能由管理员在流程实例中处理。确定继续吗？',
      '取消签收',
      { type: 'warning', confirmButtonText: '确定取消签收', cancelButtonText: '再想想' }
    )
  } catch { return }
  try {
    await unclaimTask(row.taskId)
    ElMessage.success('已取消签收')
    getList()
  } catch { /* 失败提示由请求层统一处理 */ }
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

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
