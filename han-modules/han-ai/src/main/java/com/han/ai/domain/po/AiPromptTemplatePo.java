package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt template.
 */
@Data
@TableName("ai_prompt_template")
public class AiPromptTemplatePo {

    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    private Long tenantId;

    private String templateName;

    private String category;

    private String content;

    private String variables;

    private String description;

    private Integer builtIn;

    private String status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
