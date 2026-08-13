package com.han.ai.domain.query;

import lombok.Data;

/**
 * 文档查询条件。
 */
@Data
public class AiDocumentQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String docName;

    private String indexStatus;
}
