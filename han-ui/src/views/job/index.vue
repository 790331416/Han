<template>
  <div class="app-container">
    <!-- 调度监控（/actuator/jobflow 可观测卡片区） -->
    <el-card shadow="never" class="monitor-card" data-testid="jobflow-monitor-panel">
      <template #header>
        <div class="card-header">
          <span>
            调度监控
            <el-badge v-if="monitorError" is-dot type="danger" style="margin-left: 6px;" />
          </span>
          <el-button link :icon="Refresh" :loading="monitorLoading" data-testid="jobflow-monitor-refresh" @click="loadMonitor">刷新</el-button>
        </div>
      </template>
      <el-alert
        v-if="monitorError"
        type="error"
        show-icon
        :closable="false"
        class="monitor-alert"
        data-testid="jobflow-monitor-error"
      >
        {{ monitorMessage }}
      </el-alert>
      <div v-loading="monitorLoading" class="monitor-grid">
        <div class="monitor-item" data-testid="jobflow-monitor-status">
          <span class="monitor-label">调度器状态</span>
          <el-tag :type="monitorHealth?.status === 'UP' ? 'success' : 'danger'" effect="dark">
            {{ monitorError ? '获取失败' : monitorHealth?.status === 'UP' ? '运行中' : monitorHealth?.status || '--' }}
          </el-tag>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">调度器实例</span>
          <strong>{{ monitorHealth?.schedulerName || '--' }}</strong>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">已执行任务数</span>
          <strong>{{ monitorMetrics?.totalJobsExecuted ?? monitorHealth?.numberOfJobsExecuted ?? '--' }}</strong>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">线程池大小</span>
          <strong>{{ monitorMetrics?.threadPoolSize ?? monitorConfig?.threadPoolSize ?? '--' }}</strong>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">集群模式</span>
          <strong>{{ monitorMetrics?.clustered === undefined ? '--' : monitorMetrics.clustered ? '是' : '否' }}</strong>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">失败补偿</span>
          <el-tag :type="monitorConfig?.compensationEnabled ? 'success' : 'info'" effect="plain">
            {{ monitorConfig?.compensationEnabled === undefined ? '--' : monitorConfig.compensationEnabled ? '已开启' : '已关闭' }}
          </el-tag>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">补偿间隔(秒)</span>
          <strong>{{ monitorConfig?.compensationInterval ?? '--' }}</strong>
        </div>
        <div class="monitor-item">
          <span class="monitor-label">最大重试</span>
          <strong>{{ monitorConfig?.maxRetry ?? '--' }}</strong>
        </div>
      </div>
    </el-card>

    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务组名" prop="jobGroup">
          <el-select v-model="queryParams.jobGroup" placeholder="请选择" clearable style="width: 200px">
            <el-option v-for="item in jobGroupOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 200px">
            <el-option label="正常" value="0" />
            <el-option label="暂停" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>定时任务列表</span>
          <div class="table-operations">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
            <el-button type="danger" :icon="Delete" :disabled="!selectedIds.length" @click="handleBatchDelete">删除</el-button>
            <el-button type="info" :icon="Document" @click="handleLog">日志</el-button>
          </div>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="jobList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务名称" prop="jobName" min-width="150" show-overflow-tooltip />
        <el-table-column label="任务组" prop="jobGroup" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.jobGroup === 'DEFAULT' ? undefined : 'warning'">
              {{ getJobGroupLabel(row.jobGroup) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调用目标" prop="invokeTarget" min-width="200" show-overflow-tooltip />
        <el-table-column label="Cron表达式" prop="cronExpression" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="0"
              inactive-value="1"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="下次执行时间" prop="nextFireTime" min-width="180" />
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link :icon="CaretRight" @click="handleRun(row)">执行</el-button>
            <el-button type="info" link :icon="View" @click="handleDetail(row)">详情</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="70%" class="dialog-xl" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
              <el-input v-model="form.jobName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务组名" prop="jobGroup">
              <el-select v-model="form.jobGroup" placeholder="请选择">
                <el-option v-for="item in jobGroupOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="调用目标方法" prop="invokeTarget">
          <el-select
            v-model="form.invokeTarget"
            placeholder="请选择任务处理器"
            filterable
            :loading="handlerLoading"
            style="width: 100%"
          >
            <el-option-group
              v-for="group in handlerGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="item in group.options"
                :key="item.invokeTarget"
                :label="item.description + (item.hasParam ? '(参数)' : '')"
                :value="item.invokeTarget + (item.hasParam ? '()' : '')"
              >
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>{{ item.description }}</span>
                  <div>
                    <el-tag v-if="item.hasParam" size="small" type="warning" style="margin-right: 4px;">有参</el-tag>
                    <el-tag :type="item.configured ? 'success' : 'info'" size="small">
                      {{ item.configured ? '已配置' : '未配置' }}
                    </el-tag>
                  </div>
                </div>
                <div style="font-size: 12px; color: #909399;">{{ item.invokeTarget }}</div>
              </el-option>
            </el-option-group>
          </el-select>
          <div class="el-form-item__tip">
            <p>选择任务处理器，带参数的任务请在括号内填写参数值</p>
          </div>
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="请输入Cron表达式">
            <template #append>
              <el-button @click="handleCronBuilder">生成</el-button>
            </template>
          </el-input>
          <div class="el-form-item__tip">
            <p>秒 分 时 日 月 周 [年]</p>
            <p>示例：0 0/5 * * * ? 每5分钟执行</p>
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="执行策略" prop="misfirePolicy">
              <el-radio-group v-model="form.misfirePolicy">
                <el-radio-button v-for="item in misfirePolicyOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否并发" prop="concurrent">
              <el-radio-group v-model="form.concurrent">
                <el-radio-button v-for="item in concurrentOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="任务详情" width="65%" class="dialog-lg">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务ID">{{ detailData.jobId }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ detailData.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务组名">{{ getJobGroupLabel(detailData.jobGroup) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.status === '0' ? 'success' : 'danger'">
            {{ detailData.status === '0' ? '正常' : '暂停' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="调用目标" :span="2">{{ detailData.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="Cron表达式">{{ detailData.cronExpression }}</el-descriptions-item>
        <el-descriptions-item label="下次执行时间">{{ detailData.nextFireTime }}</el-descriptions-item>
        <el-descriptions-item label="执行策略">{{ getMisfirePolicyLabel(detailData.misfirePolicy) }}</el-descriptions-item>
        <el-descriptions-item label="是否并发">{{ detailData.concurrent === '0' ? '允许' : '禁止' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- Cron生成器对话框 -->
    <el-dialog v-model="cronVisible" title="Cron表达式生成器" width="70%" class="dialog-xl" destroy-on-close>
      <CronBuilder v-model="cronValue" />
      <template #footer>
        <el-button @click="cronVisible = false">取消</el-button>
        <el-button type="primary" :loading="cronChecking" data-testid="cron-confirm" @click="handleCronConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, CaretRight, View, Document } from '@element-plus/icons-vue'
import { 
  listJob, getJob, addJob, updateJob, deleteJob, deleteJobs, 
  changeJobStatus, runJob, listJobHandlers, checkCron,
  getJobFlowHealth, getJobFlowConfig, getJobFlowMetrics,
  jobGroupOptions, misfirePolicyOptions, concurrentOptions,
  type Job, type JobQuery, type JobForm, type JobHandlerInfo,
  type JobFlowHealth, type JobFlowConfig, type JobFlowMetrics
} from '@/api/job'
import type { FormInstance, FormRules } from 'element-plus'
import CronBuilder from './components/CronBuilder.vue'

const router = useRouter()
const loading = ref(false)
const jobList = ref<Job[]>([])
const total = ref(0)
const selectedIds = ref<(string | number)[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const cronVisible = ref(false)
const submitLoading = ref(false)
const cronValue = ref('')
const cronOriginal = ref('')
const cronChecking = ref(false)
const handlerLoading = ref(false)
const handlerList = ref<JobHandlerInfo[]>([])

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// ==================== JobFlow 调度监控 ====================
const monitorLoading = ref(false)
const monitorError = ref(false)
const monitorMessage = ref('')
const monitorHealth = ref<JobFlowHealth | null>(null)
const monitorConfig = ref<JobFlowConfig | null>(null)
const monitorMetrics = ref<JobFlowMetrics | null>(null)

const loadMonitor = async () => {
  monitorLoading.value = true
  try {
    const [health, config, metrics] = await Promise.all([
      getJobFlowHealth(),
      getJobFlowConfig(),
      getJobFlowMetrics()
    ])
    monitorHealth.value = health
    monitorConfig.value = config
    monitorMetrics.value = metrics
    monitorError.value = false
    monitorMessage.value = ''
  } catch (e: any) {
    // 失败时清空旧值，避免面板留着上一次的数据看起来还正常
    monitorHealth.value = null
    monitorConfig.value = null
    monitorMetrics.value = null
    monitorError.value = true
    monitorMessage.value = e?.message || '调度监控数据获取失败'
  } finally {
    monitorLoading.value = false
  }
}

const queryParams = reactive<JobQuery>({
  pageNum: 1,
  pageSize: 10,
  jobName: undefined,
  jobGroup: undefined,
  status: undefined
})

const initForm: JobForm = {
  jobId: undefined,
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cronExpression: '',
  misfirePolicy: '1',
  concurrent: '1',
  status: '0',
  remark: ''
}

const form = reactive<JobForm>({ ...initForm })
const detailData = ref<Job>({} as Job)

/**
 * 调后端 /job/checkCron 做服务端合法性校验。
 * 接口本身不可用时返回 null（无法判定），不阻断用户操作。
 */
const verifyCron = async (expression: string): Promise<boolean | null> => {
  try {
    const res = await checkCron(expression)
    return (res as any)?.data === true
  } catch {
    return null
  }
}

const cronValidator = async (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback()
    return
  }
  const valid = await verifyCron(value)
  if (valid === false) {
    callback(new Error('Cron 表达式不合法，请检查后重试'))
    return
  }
  callback()
}

const rules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  jobGroup: [{ required: true, message: '请选择任务组', trigger: 'change' }],
  invokeTarget: [{ required: true, message: '请输入调用目标', trigger: 'blur' }],
  cronExpression: [
    { required: true, message: '请输入Cron表达式', trigger: 'blur' },
    { validator: cronValidator, trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => form.jobId ? '编辑任务' : '新增任务')

// handler 按服务分组
const handlerGroups = computed(() => {
  const groups: Record<string, JobHandlerInfo[]> = {}
  for (const h of handlerList.value) {
    const key = h.serviceName || '本地服务'
    if (!groups[key]) groups[key] = []
    groups[key].push(h)
  }
  return Object.entries(groups).map(([label, options]) => ({ label, options }))
})

// 加载可用 handler 列表
const loadHandlers = async () => {
  handlerLoading.value = true
  try {
    const res = await listJobHandlers()
    handlerList.value = res.data || []
  } catch { /* 接口不可用 */ } finally {
    handlerLoading.value = false
  }
}

// 获取任务组标签
const getJobGroupLabel = (value: string) => {
  return jobGroupOptions.find(item => item.value === value)?.label || value
}

// 获取执行策略标签
const getMisfirePolicyLabel = (value: string) => {
  return misfirePolicyOptions.find(item => item.value === value)?.label || value
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await listJob(queryParams)
    jobList.value = res.data.rows
    total.value = res.data.total
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

// 多选
const handleSelectionChange = (selection: Job[]) => {
  selectedIds.value = selection.map(item => item.jobId)
}

// 新增
const handleAdd = () => {
  Object.assign(form, initForm)
  form.jobId = undefined
  dialogVisible.value = true
  loadHandlers()
}

// 编辑
const handleEdit = async (row: Job) => {
  try {
    const res = await getJob(row.jobId)
    Object.assign(form, res.data)
    dialogVisible.value = true
    loadHandlers()
  } catch { /* 失败提示由请求层统一处理 */ }
}

// 详情
const handleDetail = async (row: Job) => {
  try {
    const res = await getJob(row.jobId)
    detailData.value = res.data
    detailVisible.value = true
  } catch { /* 失败提示由请求层统一处理 */ }
}

// 提交
const handleSubmit = async () => {
  // validate() 校验失败是 reject 而不是 resolve(false)，必须 catch，否则抛未处理拒绝
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.jobId) {
      await updateJob(form)
      ElMessage.success('修改成功')
    } else {
      await addJob(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch { /* 提交失败已由请求层提示 */ } finally {
    submitLoading.value = false
  }
}

// 删除
const handleDelete = async (row: Job) => {
  try {
    await ElMessageBox.confirm(`确定删除任务"${row.jobName}"吗?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteJob(row.jobId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 失败提示由请求层统一处理 */ }
}

// 批量删除
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定删除选中的${selectedIds.value.length}个任务吗?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteJobs(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  } catch { /* 失败提示由请求层统一处理 */ }
}

// 状态修改
const handleStatusChange = async (row: Job) => {
  const text = row.status === '0' ? '启用' : '暂停'
  try {
    await ElMessageBox.confirm(`确定${text}任务"${row.jobName}"吗?`, '提示', { type: 'warning' })
    await changeJobStatus(row.jobId, row.status)
    ElMessage.success(`${text}成功`)
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 立即执行：接口成功只代表提交给了调度器，不代表业务逻辑跑完
const handleRun = async (row: Job) => {
  try {
    await ElMessageBox.confirm(`确定立即执行任务"${row.jobName}"吗?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await runJob(row.jobId)
    ElMessage.success('已触发执行，执行结果请到调度日志查看')
  } catch { /* 失败提示由请求层统一处理 */ }
}

// 查看日志
const handleLog = () => {
  router.push({ name: 'JobLog' })
}

// ==================== Cron 生成器 ====================

/** 秒字段不是固定值时，任务会在一分钟内触发多次，属于高危配置。 */
const isHighFrequency = (expression: string) => {
  const second = expression.trim().split(/\s+/)[0] || ''
  return second === '*' || second.includes('/') || second.includes('-') || second.includes(',')
}

const handleCronBuilder = () => {
  cronOriginal.value = form.cronExpression || ''
  cronValue.value = form.cronExpression || ''
  cronVisible.value = true
}

/**
 * 确认 Cron：先做服务端校验，再把「原表达式 → 新表达式」摆出来二次确认。
 * 历史缺陷：这里直接回填生成器产出的值，打开生成器点确定就会把任务改成每秒执行。
 */
const handleCronConfirm = async () => {
  const next = (cronValue.value || '').trim()
  if (!next) {
    ElMessage.warning('请先在生成器中配置 Cron 表达式')
    return
  }
  if (next === (cronOriginal.value || '').trim()) {
    cronVisible.value = false
    return
  }

  cronChecking.value = true
  try {
    const valid = await verifyCron(next)
    if (valid === false) {
      ElMessage.error('生成的 Cron 表达式不合法，请调整后重试')
      return
    }
    const lines = [
      `原表达式：${cronOriginal.value || '（空）'}`,
      `新表达式：${next}`
    ]
    if (valid === null) {
      lines.push('提示：服务端校验接口暂不可用，未能确认表达式合法性。')
    }
    if (isHighFrequency(next)) {
      lines.push('警告：该表达式在一分钟内会触发多次，可能压垮调度线程池与下游服务。')
    }
    await ElMessageBox.confirm(lines.join('\n'), '确认修改 Cron 表达式', {
      type: 'warning',
      confirmButtonText: '确认修改',
      cancelButtonText: '取消',
      customClass: 'cron-confirm-box'
    })
    form.cronExpression = next
    cronVisible.value = false
  } catch {
    // 用户取消确认，保持原表达式不变
  } finally {
    cronChecking.value = false
  }
}

onMounted(() => {
  getList()
  loadMonitor()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.monitor-card {
  margin-bottom: 20px;

  .monitor-alert {
    margin-bottom: 12px;
  }

  .monitor-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 12px;
  }

  .monitor-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 10px 12px;
    background: #f7f9fc;
    border-radius: 8px;

    .monitor-label {
      font-size: 12px;
      color: #909399;
    }

    strong {
      font-size: 14px;
      color: #303133;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .el-tag { align-self: flex-start; }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-form-item__tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
  
  p {
    margin: 0;
  }
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
