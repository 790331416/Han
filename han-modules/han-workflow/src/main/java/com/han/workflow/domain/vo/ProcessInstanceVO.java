package com.han.workflow.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程实例VO
 */
@Data
public class ProcessInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    private String instanceId;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程定义Key
     */
    private String processDefinitionKey;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

    /**
     * 流程定义版本
     */
    private Integer processDefinitionVersion;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 流程标题
     */
    private String title;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 发起人ID
     */
    private String startUserId;

    /**
     * 发起人名称
     */
    private String startUserName;

    /**
     * 发起时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时(毫秒)
     */
    private Long duration;

    private String status;

    /**
     * 是否挂起
     */
    private Boolean suspended;

    /**
     * 是否结束
     */
    private Boolean ended;

    /**
     * 当前节点
     */
    private String currentActivityName;

    /**
     * 当前审批人
     */
    private String currentAssignee;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}
