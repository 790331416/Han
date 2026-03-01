package com.han.workflow.service;

import com.han.common.core.domain.PageResult;
import com.han.workflow.domain.dto.ProcessStartDTO;
import com.han.workflow.domain.dto.TaskCompleteDTO;
import com.han.workflow.domain.dto.TaskQueryDTO;
import com.han.workflow.domain.vo.ProcessInstanceVO;
import com.han.workflow.domain.vo.TaskVO;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 流程实例服务接口
 */
public interface IProcessInstanceService {

    /**
     * 启动流程实例
     */
    ProcessInstanceVO startProcess(ProcessStartDTO dto);

    /**
     * 查询我的待办任务
     */
    PageResult<TaskVO> listMyTodoTasks(TaskQueryDTO dto);

    /**
     * 查询我的已办任务
     */
    PageResult<TaskVO> listMyDoneTasks(TaskQueryDTO dto);

    /**
     * 查询我发起的流程
     */
    PageResult<ProcessInstanceVO> listMyStartedProcess(TaskQueryDTO dto);

    /**
     * 完成任务
     */
    void completeTask(TaskCompleteDTO dto);

    /**
     * 转办任务
     */
    void transferTask(String taskId, String targetUserId, String reason);

    /**
     * 委派任务
     */
    void delegateTask(String taskId, String targetUserId, String reason);

    /**
     * 签收任务(候选人签收)
     */
    void claimTask(String taskId);

    /**
     * 取消签收
     */
    void unclaimTask(String taskId);

    /**
     * 撤回流程
     */
    void cancelProcess(String processInstanceId, String reason);

    /**
     * 挂起流程实例
     */
    void suspendProcess(String processInstanceId);

    /**
     * 激活流程实例
     */
    void activateProcess(String processInstanceId);

    /**
     * 删除流程实例
     */
    void deleteProcess(String processInstanceId, String reason);

    /**
     * 获取流程变量
     */
    Map<String, Object> getProcessVariables(String processInstanceId);

    /**
     * 设置流程变量
     */
    void setProcessVariables(String processInstanceId, Map<String, Object> variables);

    /**
     * 获取流程图(高亮当前节点)
     */
    InputStream getProcessDiagram(String processInstanceId);

    /**
     * 获取流程审批记录
     */
    List<TaskVO> getProcessHistory(String processInstanceId);
}
