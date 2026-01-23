package com.xuman.common.core.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础查询对象
 */
@Data
public class BaseQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private Integer pageNum = 1;

    /** 每页数量 */
    private Integer pageSize = 10;

    /** 排序字段 */
    private String orderByColumn;

    /** 排序方向（asc/desc） */
    private String orderDirection = "asc";

    /** 开始时间 */
    private LocalDateTime beginTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
