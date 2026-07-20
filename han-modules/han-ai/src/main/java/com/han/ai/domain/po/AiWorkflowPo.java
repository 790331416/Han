package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI workflow entity.
 */
@Data
@TableName("ai_workflow")
public class AiWorkflowPo {

    @TableId(value = "workflow_id", type = IdType.AUTO)
    private Long workflowId;

    private String workflowName;

    private String description;

    private String workflowType;

    private Long modelId;

    private String knowledgeBaseIds;

    private String mcpServerIds;

    private String systemPrompt;

    private String flowConfig;

    private String prologue;

    /**
     * 开场推荐问题（JSON 字符串数组，最多 5 条；空数组=不展示）
     */
    private String suggestedQuestions;

    private String published;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
