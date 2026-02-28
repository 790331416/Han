package com.han.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> rows;

    /** 当前页码 */
    private int pageNum;

    /** 每页数量 */
    private int pageSize;

    /** 总页数 */
    private int pages;

    public PageResult(List<T> rows, long total) {
        this.rows = rows;
        this.total = total;
    }

    public PageResult(List<T> rows, long total, int pageNum, int pageSize) {
        this.rows = rows;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    /**
     * 构建分页结果
     */
    public static <T> PageResult<T> of(List<T> rows, long total) {
        return new PageResult<>(rows, total);
    }

    /**
     * 构建分页结果
     */
    public static <T> PageResult<T> of(List<T> rows, long total, int pageNum, int pageSize) {
        return new PageResult<>(rows, total, pageNum, pageSize);
    }

    /**
     * 空分页
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0);
    }
}
