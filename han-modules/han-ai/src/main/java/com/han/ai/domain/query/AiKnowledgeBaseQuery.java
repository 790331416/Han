package com.han.ai.domain.query;

import lombok.Data;

/**
 * 知识库查询条件。
 */
@Data
public class AiKnowledgeBaseQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String kbName;

    private String kbType;

    private String status;
}
