package com.han.workflow.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义VO
 */
@Data
public class ProcessDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程Key
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 部署ID
     */
    private String deploymentId;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 流程图资源名称
     */
    private String diagramResourceName;

    /**
     * 是否挂起
     */
    private Boolean suspended;

    /**
     * 部署时间
     */
    private LocalDateTime deploymentTime;

    /**
     * 描述
     */
    private String description;
}
