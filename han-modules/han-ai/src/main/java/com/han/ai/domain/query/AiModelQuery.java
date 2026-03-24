package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI model query.
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
