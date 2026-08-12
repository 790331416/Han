package com.han.common.core.domain.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分页参数的兜底上限，避免调用方漏加 @Validated 时被一次性拉全表 */
    public static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过" + MAX_PAGE_SIZE)
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    private String orderByColumn;

    private String orderDirection = "asc";

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    /**
     * 规范化后的页码：小于 1 一律按第 1 页处理。
     */
    @JsonIgnore
    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 规范化后的每页条数：小于 1 取默认值，超过 {@link #MAX_PAGE_SIZE} 钳制到上限。
     * <p>校验注解只在开启 {@code @Validated} 时生效，这里再做一次兜底，
     * 保证任何调用路径都拿不到负偏移或超大页。
     */
    @JsonIgnore
    public int getSafePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    @JsonIgnore
    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
