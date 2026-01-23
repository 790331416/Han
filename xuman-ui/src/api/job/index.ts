import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// 定时任务类型
export interface Job {
  jobId: number
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
  jobId?: number
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
  jobLogId: number
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

// ===================== 定时任务接口 =====================

// 获取任务列表
export function listJob(query: JobQuery) {
  return get<PageResult<Job>>('/job/list', query)
}

// 获取任务详情
export function getJob(jobId: number) {
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
export function deleteJob(jobId: number) {
  return post<void>(`/job/remove/${jobId}`)
}

// 批量删除任务
export function deleteJobs(jobIds: number[]) {
  return post<void>('/job/remove', jobIds)
}

// 修改任务状态
export function changeJobStatus(jobId: number, status: string) {
  return post<void>('/job/changeStatus', { jobId, status })
}

// 立即执行任务
export function runJob(jobId: number) {
  return post<void>(`/job/run/${jobId}`)
}

// 校验Cron表达式
export function checkCron(cronExpression: string) {
  return get<boolean>('/job/checkCron', { cronExpression })
}

// ===================== 任务日志接口 =====================

// 获取日志列表
export function listJobLog(query: JobLogQuery) {
  return get<PageResult<JobLog>>('/job/log/list', query)
}

// 获取日志详情
export function getJobLog(jobLogId: number) {
  return get<JobLog>(`/job/log/${jobLogId}`)
}

// 删除日志
export function deleteJobLog(jobLogId: number) {
  return post<void>(`/job/log/remove/${jobLogId}`)
}

// 批量删除日志
export function deleteJobLogs(jobLogIds: number[]) {
  return post<void>('/job/log/remove', jobLogIds)
}

// 清空日志
export function cleanJobLog() {
  return post<void>('/job/log/clean')
}
