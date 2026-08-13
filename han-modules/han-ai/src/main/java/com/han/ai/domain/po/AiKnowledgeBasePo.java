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
 * 知识库。
 * <p>
 * 约束长度与 {@code ai_knowledge_base} 的列宽对齐：超长时回可读的业务提示，
 * 而不是把数据库原生的约束冲突抛给用户。
 */
@Data
@TableName("ai_knowledge_base")
public class AiKnowledgeBasePo {

    @TableId(value = "kb_id", type = IdType.AUTO)
    private Long kbId;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 200, message = "知识库名称不能超过 200 字符")
    private String kbName;

    @Size(max = 1000, message = "知识库描述不能超过 1000 字符")
    private String description;

    @NotBlank(message = "知识库类型不能为空")
    @Size(max = 20, message = "知识库类型不能超过 20 字符")
    private String kbType;

    private Long embeddingModelId;

    private Integer documentCount;

    private Integer paragraphCount;

    private Long charCount;

    @Pattern(regexp = "^[01]$", message = "知识库状态只能是 0（启用）或 1（停用）")
    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
