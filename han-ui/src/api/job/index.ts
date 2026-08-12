import { get, post, postParams } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// 定时任务类型
export interface Job {
  jobId: string | number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  misfirePolicy: string
  concurrent: string
  status: string
  createTime: string
  remark?: string
  nextFireTime?: string
}

// 任务查询参数
export interface JobQuery extends PageQuery {
  jobName?: string
  jobGroup?: string
  status?: string
  invokeTarget?: string
}

// 任务表单
export interface JobForm {
  jobId?: string | number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  misfirePolicy: string
  concurrent: string
  status: string
  remark?: string
}

// 任务日志类型
export interface JobLog {
  jobLogId: string | number
  jobName: string
  jobGroup: string
  invokeTarget: string
  jobMessage?: string
  status: string
  exceptionInfo?: string
  startTime: string
  stopTime: string
  costTime?: number
}

// 日志查询参数
export interface JobLogQuery extends PageQuery {
  jobName?: string
  jobGroup?: string
  status?: string
  startTime?: string
  endTime?: string
}

// 任务组选项
export const jobGroupOptions = [
  { label: '默认', value: 'DEFAULT' },
  { label: '系统', value: 'SYSTEM' }
]

// 执行策略选项
export const misfirePolicyOptions = [
  { label: '立即执行', value: '1' },
  { label: '执行一次', value: '2' },
  { label: '放弃执行', value: '3' }
]

// 是否并发选项
export const concurrentOptions = [
  { label: '允许', value: '0' },
  { label: '禁止', value: '1' }
]

// 任务处理器信息
export interface JobHandlerInfo {
  beanName: string
  methodName: string
  hasParam: boolean
  description: string
  invokeTarget: string
  serviceName: string
  configured: boolean
}

// ===================== 定时任务接口 =====================

// 获取所有可用任务处理器
export function listJobHandlers() {
  return get<JobHandlerInfo[]>('/job/handlers')
}

// 获取任务列表
export function listJob(query: JobQuery) {
  return get<PageResult<Job>>('/job/list', query)
}

// 获取任务详情
export function getJob(jobId: string | number) {
  return get<Job>(`/job/${jobId}`)
}

// 新增任务
export function addJob(data: JobForm) {
  return post<void>('/job', data)
}

// 修改任务
export function updateJob(data: JobForm) {
  return post<void>('/job/edit', data)
}

// 删除任务
export function deleteJob(jobId: string | number) {
  return post<void>(`/job/remove/${jobId}`)
}

// 批量删除任务
export function deleteJobs(jobIds: (string | number)[]) {
  return post<void>('/job/remove', jobIds)
}

// 修改任务状态
export function changeJobStatus(jobId: string | number, status: string) {
  return postParams<void>('/job/changeStatus', { jobId, status })
}

// 立即执行任务
export function runJob(jobId: string | number) {
  return post<void>(`/job/run/${jobId}`)
}

// 校验Cron表达式
export function checkCron(cronExpression: string) {
  return get<boolean>('/job/checkCron', { cronExpression })
}

// ===================== JobFlow 调度监控（/actuator/jobflow，裸 JSON 无 R 包装） =====================

export interface JobFlowHealth {
  status: string
  schedulerName?: string
  schedulerInstanceId?: string
  inStandbyMode?: boolean
  numberOfJobsExecuted?: number
  runningSince?: string
}

export interface JobFlowConfig {
  threadPoolSize?: number
  timeout?: number
  maxRetry?: number
  connectTimeout?: number
  readTimeout?: number
  lockTimeout?: number
  compensationEnabled?: boolean
  compensationInterval?: number
  stuckThreshold?: number
}

export interface JobFlowMetrics {
  totalJobsExecuted?: number
  threadPoolSize?: number
  version?: string
  clustered?: boolean
}

/**
 * 裸 JSON 端点的响应兜底。
 *
 * 请求层对「不带 R 包装」的响应直接透传，生产环境 nginx 若没有 /actuator/ 的 location，
 * 请求会落进 SPA 兜底拿到一整篇 index.html，透传后被当成正常数据，
 * 面板只会显示一排 -- 且不报错。这里强制要求是对象，否则按失败抛出。
 */
function requireJsonObject<T>(payload: unknown, endpoint: string): T {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error(`${endpoint} 未返回 JSON 数据，请检查网关或 nginx 是否已放行 /actuator/ 路径`)
  }
  return payload as T
}

export async function getJobFlowHealth(): Promise<JobFlowHealth> {
  const res = await get<never>('/actuator/jobflow/health', undefined, { silentError: true })
  return requireJsonObject<JobFlowHealth>(res, '/actuator/jobflow/health')
}

export async function getJobFlowConfig(): Promise<JobFlowConfig> {
  const res = await get<never>('/actuator/jobflow/config', undefined, { silentError: true })
  return requireJsonObject<JobFlowConfig>(res, '/actuator/jobflow/config')
}

export async function getJobFlowMetrics(): Promise<JobFlowMetrics> {
  const res = await get<never>('/actuator/jobflow/metrics', undefined, { silentError: true })
  return requireJsonObject<JobFlowMetrics>(res, '/actuator/jobflow/metrics')
}

// ===================== 任务日志接口 =====================

// 获取日志列表
export function listJobLog(query: JobLogQuery) {
  return get<PageResult<JobLog>>('/job/log/list', query)
}

// 获取日志详情
export function getJobLog(jobLogId: string | number) {
  return get<JobLog>(`/job/log/${jobLogId}`)
}

// 删除日志
export function deleteJobLog(jobLogId: string | number) {
  return post<void>(`/job/log/remove/${jobLogId}`)
}

// 批量删除日志。后端只有 POST /job/log/remove/{jobLogIds}（逗号分隔的路径变量），
// 没有接收请求体的 /job/log/remove，原来那种写法必定 404。
export function deleteJobLogs(jobLogIds: (string | number)[]) {
  return post<void>(`/job/log/remove/${jobLogIds.join(',')}`)
}

// 清空日志
export function cleanJobLog() {
  return post<void>('/job/log/clean')
}
