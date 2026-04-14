package com.han.ai.domain.query;

import lombok.Data;

/**
 * Knowledge base query.
 */
@Data
public class AiKnowledgeBaseQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String kbName;

    private String kbType;

    private String status;
}
