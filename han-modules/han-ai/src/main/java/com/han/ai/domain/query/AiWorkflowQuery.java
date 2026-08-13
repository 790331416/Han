package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI 编排查询条件。
 */
@Data
public class AiWorkflowQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String workflowName;

    private String workflowType;

    private String status;
}
