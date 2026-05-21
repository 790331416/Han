package com.han.aivideo.domain.query;

import lombok.Data;

/**
 * AI short-drama generation task query.
 */
@Data
public class AivideoTaskQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private Long projectId;

    private Long tenantId;

    private String taskType;

    private String taskStatus;
}
