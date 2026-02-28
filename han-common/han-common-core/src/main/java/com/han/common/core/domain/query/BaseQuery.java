package com.han.common.core.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String orderByColumn;

    private String orderDirection = "asc";

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
