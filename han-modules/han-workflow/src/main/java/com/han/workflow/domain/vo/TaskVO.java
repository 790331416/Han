package com.han.workflow.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务VO
 */
@Data
public class TaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务Key
     */
    private String taskDefinitionKey;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 流程标题
     */
    private String title;

    /**
     * 任务执行人ID
     */
    private String assignee;

    /**
     * 任务执行人名称
     */
    private String assigneeName;

    /**
     * 候选人列表(逗号分隔)
     */
    private String candidateUsers;

    /**
     * 候选组列表(逗号分隔)
     */
    private String candidateGroups;

    /**
     * 任务创建时间
     */
    private LocalDateTime createTime;

    /**
     * 任务签收时间
     */
    private LocalDateTime claimTime;

    /**
     * 任务完成时间
     */
    private LocalDateTime endTime;

    /**
     * 任务耗时(毫秒)
     */
    private Long duration;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 审批结果
     */
    private String result;

    /**
     * 发起人ID
     */
    private String startUserId;

    /**
     * 发起人名称
     */
    private String startUserName;

    /**
     * 任务变量
     */
    private Map<String, Object> variables;
}
