package com.han.ai.domain.query;

import lombok.Data;

/**
 * AI conversation query.
 */
@Data
public class AiConversationQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
