package com.han.ai.domain.query;

import lombok.Data;

/**
 * 提示词模板查询条件。
 */
@Data
public class AiPromptTemplateQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String templateName;

    private String category;

    private String status;
}
