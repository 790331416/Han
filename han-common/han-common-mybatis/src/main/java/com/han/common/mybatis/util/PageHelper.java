package com.han.common.mybatis.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.han.common.core.domain.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页工具类
 */
public final class PageHelper {

    private PageHelper() {
    }

    /**
     * 从MyBatis-Plus分页构建结果
     */
    public static <T> PageResult<T> build(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRows(page.getRecords());
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setPages((int) page.getPages());
        return result;
    }

    /**
     * 从MyBatis-Plus分页构建结果（带转换函数）
     */
    public static <E, T> PageResult<T> build(IPage<E> page, Function<E, T> converter) {
        List<T> rows = page.getRecords().stream().map(converter).toList();
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRows(rows);
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setPages((int) page.getPages());
        return result;
    }
}
