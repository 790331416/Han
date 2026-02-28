import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// ===================== 流程定义 =====================

export interface ProcessDefinition {
  id: string
  key: string
  name: string
  category: string
  version: number
  deploymentId: string
  resourceName: string
  suspended: boolean
  deploymentTime: string
  description?: string
}

export interface ProcessDefinitionQuery extends PageQuery {
  name?: string
  key?: string
  category?: string
  suspended?: boolean
}

// 流程定义列表
export function listProcessDefinition(query: ProcessDefinitionQuery) {
  return get<PageResult<ProcessDefinition>>('/workflow/definition/list', query)
}

// 部署流程定义(文件)
export function deployProcessDefinition(data: FormData) {
  return post<void>('/workflow/definition/deploy', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 部署流程定义(XML)
export function deployProcessDefinitionXml(name: string, category: string, bpmnXml: string) {
  return post<void>(`/workflow/definition/deployXml?name=${name}&category=${category}`, bpmnXml, {
    headers: { 'Content-Type': 'text/plain' }
  })
}

// 激活流程定义
export function activateProcessDefinition(processDefinitionId: string) {
  return post<void>(`/workflow/definition/activate/${processDefinitionId}`)
}

// 挂起流程定义
export function suspendProcessDefinition(processDefinitionId: string) {
  return post<void>(`/workflow/definition/suspend/${processDefinitionId}`)
}

// 删除流程定义
export function deleteProcessDefinition(deploymentId: string, cascade: boolean = false) {
  return post<void>(`/workflow/definition/delete/${deploymentId}?cascade=${cascade}`)
}

// 获取流程定义XML
export function getProcessDefinitionXml(processDefinitionId: string) {
  return get<string>(`/workflow/definition/xml/${processDefinitionId}`)
}

// ===================== 流程实例 =====================

export interface ProcessInstance {
  instanceId: string
  processDefinitionId: string
  processDefinitionKey: string
  processDefinitionName: string
  businessKey?: string
  startUserId: string
  startUserName?: string
  startTime: string
  endTime?: string
  duration?: number
  status: string
  variables?: Record<string, any>
}

export interface ProcessInstanceQuery extends PageQuery {
  processDefinitionName?: string
  processDefinitionKey?: string
  startUserId?: string
  status?: string
  startTime?: string
  endTime?: string
}

export interface ProcessStartForm {
  processDefinitionKey: string
  businessKey?: string
  variables?: Record<string, any>
}

// 流程实例列表
export function listProcessInstance(query: ProcessInstanceQuery) {
  return get<PageResult<ProcessInstance>>('/workflow/instance/list', query)
}

// 启动流程实例
export function startProcessInstance(data: ProcessStartForm) {
  return post<ProcessInstance>('/workflow/instance/start', data)
}

// 终止流程实例
export function stopProcessInstance(instanceId: string, reason?: string) {
  return post<void>(`/workflow/instance/stop/${instanceId}`, { reason })
}

// 挂起流程实例
export function suspendProcessInstance(instanceId: string) {
  return post<void>(`/workflow/instance/suspend/${instanceId}`)
}

// 激活流程实例
export function activateProcessInstance(instanceId: string) {
  return post<void>(`/workflow/instance/activate/${instanceId}`)
}

// 删除流程实例
export function deleteProcessInstance(instanceId: string, reason?: string) {
  return post<void>(`/workflow/instance/delete/${instanceId}`, { reason })
}

// ===================== 任务管理 =====================

export interface TaskItem {
  taskId: string
  taskName: string
  taskDefinitionKey: string
  assignee: string
  assigneeName?: string
  processInstanceId: string
  processDefinitionId: string
  processDefinitionName: string
  processDefinitionKey: string
  businessKey?: string
  createTime: string
  claimTime?: string
  dueDate?: string
  endTime?: string
  duration?: number
  description?: string
  variables?: Record<string, any>
}

export interface TaskQuery extends PageQuery {
  taskName?: string
  processDefinitionName?: string
  assignee?: string
}

export interface TaskCompleteForm {
  taskId: string
  variables?: Record<string, any>
  comment?: string
}

// 待办任务列表
export function listTodoTask(query: TaskQuery) {
  return get<PageResult<TaskItem>>('/workflow/task/todo', query)
}

// 已办任务列表
export function listDoneTask(query: TaskQuery) {
  return get<PageResult<TaskItem>>('/workflow/task/done', query)
}

// 完成任务
export function completeTask(data: TaskCompleteForm) {
  return post<void>('/workflow/task/complete', data)
}

// 转办任务
export function transferTask(taskId: string, userId: string) {
  return post<void>('/workflow/task/transfer', { taskId, userId })
}

// 委派任务
export function delegateTask(taskId: string, userId: string) {
  return post<void>('/workflow/task/delegate', { taskId, userId })
}

// 撤回任务
export function revokeTask(taskId: string) {
  return post<void>(`/workflow/task/revoke/${taskId}`)
}

// 流程分类选项
export const categoryOptions = [
  { label: 'OA审批', value: 'OA' },
  { label: '人事管理', value: 'HR' },
  { label: '财务管理', value: 'FINANCE' },
  { label: '项目管理', value: 'PROJECT' },
  { label: '其他', value: 'OTHER' }
]

// 流程状态选项
export const instanceStatusOptions = [
  { label: '运行中', value: 'running' },
  { label: '已完成', value: 'completed' },
  { label: '已挂起', value: 'suspended' },
  { label: '已终止', value: 'terminated' }
]
