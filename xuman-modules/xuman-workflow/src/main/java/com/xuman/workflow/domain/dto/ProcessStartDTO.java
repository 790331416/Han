package com.xuman.workflow.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;

/**
 * 流程启动DTO
 */
@Data
public class ProcessStartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程定义Key
     */
    @NotBlank(message = "流程Key不能为空")
    private String processKey;

    /**
     * 业务Key(关联业务表主键)
     */
    private String businessKey;

    /**
     * 流程标题
     */
    private String title;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 下一节点审批人(指定审批人启动)
     */
    private String nextAssignee;
}
