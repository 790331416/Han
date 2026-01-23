package com.xuman.workflow.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程定义查询DTO
 */
@Data
public class ProcessDefinitionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 流程Key
     */
    private String key;

    /**
     * 是否挂起
     */
    private Boolean suspended;

    /**
     * 当前页
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;
}
