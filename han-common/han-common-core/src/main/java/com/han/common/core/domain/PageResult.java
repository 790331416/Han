package com.han.common.core.domain;

import com.han.common.core.domain.query.BaseQuery;
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
     * <p><b>注意</b>：本重载无法得知分页参数，{@code pageNum} / {@code pageSize} / {@code pages}
     * 会保持为 0，前端分页控件算不出页数。已知分页参数时请改用
     * {@link #of(List, long, int, int)} 或 {@link #of(List, long, BaseQuery)}。
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
     * 构建分页结果（分页参数取自查询对象，已做上下限钳制）
     */
    public static <T> PageResult<T> of(List<T> rows, long total, BaseQuery query) {
        if (query == null) {
            return new PageResult<>(rows, total);
        }
        return new PageResult<>(rows, total, query.getSafePageNum(), query.getSafePageSize());
    }

    /**
     * 空分页
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0);
    }
}
