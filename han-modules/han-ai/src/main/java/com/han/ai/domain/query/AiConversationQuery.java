package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI 会话查询条件。
 */
@Data
public class AiConversationQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private Long workflowId;
}
