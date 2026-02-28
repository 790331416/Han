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

    /**
     * 流程分类
     */
    private String category;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 当前页
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;
}
