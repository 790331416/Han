package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI 模型查询条件。
 */
@Data
public class AiModelQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String modelName;

    private String modelType;

    private String provider;

    private String status;
}
