package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI workflow query.
 */
@Data
public class AiWorkflowQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String workflowName;

    private String workflowType;

    private String status;
}
