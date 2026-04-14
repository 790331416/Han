package com.han.ai.domain.query;

import lombok.Data;

/**
 * Document query.
 */
@Data
public class AiDocumentQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String docName;

    private String indexStatus;
}
