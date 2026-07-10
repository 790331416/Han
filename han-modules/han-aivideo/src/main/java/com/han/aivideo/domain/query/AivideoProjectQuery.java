package com.han.aivideo.domain.query;

import lombok.Data;

/**
 * AI short-drama project query.
 */
@Data
public class AivideoProjectQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String projectName;

    private String projectStatus;

    private String currentStage;
}
