package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 编排实体。
 */
@Data
@TableName("ai_workflow")
public class AiWorkflowPo {

    @TableId(value = "workflow_id", type = IdType.AUTO)
    private Long workflowId;

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 200, message = "工作流名称不能超过 200 字符")
    private String workflowName;

    @Size(max = 1000, message = "工作流描述不能超过 1000 字符")
    private String description;

    @NotBlank(message = "工作流类型不能为空")
    @Size(max = 20, message = "工作流类型不能超过 20 字符")
    private String workflowType;

    private Long modelId;

    @Size(max = 4000, message = "关联知识库配置不能超过 4000 字符")
    private String knowledgeBaseIds;

    @Size(max = 4000, message = "关联 MCP 服务配置不能超过 4000 字符")
    private String mcpServerIds;

    @Size(max = 20000, message = "系统提示词不能超过 20000 字符")
    private String systemPrompt;

    /** 画布定义无上限时，边数量也无上限，O(V×E) 图算法会成为 DoS 面 */
    @Size(max = 1_000_000, message = "编排画布定义过大，请精简节点与连线")
    private String flowConfig;

    @Size(max = 2000, message = "开场白不能超过 2000 字符")
    private String prologue;

    /**
     * 开场推荐问题（JSON 字符串数组，最多 5 条；空数组=不展示）
     */
    @Size(max = 4000, message = "推荐问题配置不能超过 4000 字符")
    private String suggestedQuestions;

    @Pattern(regexp = "^[01]$", message = "发布状态只能是 0 或 1")
    private String published;

    @Pattern(regexp = "^[01]$", message = "工作流状态只能是 0（启用）或 1（停用）")
    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
