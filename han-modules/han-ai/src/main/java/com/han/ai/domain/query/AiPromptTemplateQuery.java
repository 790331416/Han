package com.han.ai.domain.query;

import lombok.Data;

/**
 * Prompt template query.
 */
@Data
public class AiPromptTemplateQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String templateName;

    private String category;

    private String status;
}
