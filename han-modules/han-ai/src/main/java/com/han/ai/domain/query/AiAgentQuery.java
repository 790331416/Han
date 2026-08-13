package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI 智能体查询条件。
 */
@Data
public class AiAgentQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String agentName;

    private Boolean published;

    private String status;
}
