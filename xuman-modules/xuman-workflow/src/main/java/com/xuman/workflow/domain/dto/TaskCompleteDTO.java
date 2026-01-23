package com.xuman.workflow.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 任务完成DTO
 */
@Data
public class TaskCompleteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 审批结果(pass通过/reject驳回/return退回)
     */
    private String result;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 下一节点审批人(多人会签)
     */
    private List<String> nextAssignees;

    /**
     * 抄送人
     */
    private List<String> copyUsers;

    /**
     * 附件ID列表
     */
    private List<Long> attachmentIds;
}
