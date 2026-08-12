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
 * Prompt template.
 */
@Data
@TableName("ai_prompt_template")
public class AiPromptTemplatePo {

    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    private Long tenantId;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过 100 字符")
    private String templateName;

    @NotBlank(message = "模板分类不能为空")
    @Size(max = 20, message = "模板分类不能超过 20 字符")
    private String category;

    @NotBlank(message = "模板内容不能为空")
    @Size(max = 50000, message = "模板内容不能超过 50000 字符")
    private String content;

    @Size(max = 5000, message = "变量声明不能超过 5000 字符")
    private String variables;

    @Size(max = 500, message = "模板描述不能超过 500 字符")
    private String description;

    private Integer builtIn;

    @Pattern(regexp = "^[01]$", message = "模板状态只能是 0（启用）或 1（停用）")
    private String status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
