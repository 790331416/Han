package com.han.workflow.domain.dto;

import lombok.Data;

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
    private String processKey;

    private String processDefinitionKey;

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

    public String resolveProcessKey() {
        if (processKey != null && !processKey.isBlank()) {
            return processKey;
        }
        return processDefinitionKey;
    }
}
