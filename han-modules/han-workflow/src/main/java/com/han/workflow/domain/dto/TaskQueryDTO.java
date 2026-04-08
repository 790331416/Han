package com.han.workflow.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 任务查询DTO
 */
@Data
public class TaskQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程名称
     */
    private String processName;

    private String processDefinitionName;

    private String taskName;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 业务Key
     */
    private String businessKey;

    private String status;

    private String assignee;

    /**
     * 当前页
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    public String resolveProcessName() {
        if (processDefinitionName != null && !processDefinitionName.isBlank()) {
            return processDefinitionName;
        }
        return processName;
    }
}
