package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI agent query.
 */
@Data
public class AiAgentQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String agentName;

    private Boolean published;

    private String status;
}
