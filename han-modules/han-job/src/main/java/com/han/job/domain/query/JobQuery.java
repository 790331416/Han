package com.han.job.domain.query;

import com.han.common.core.domain.query.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 定时任务查询对象，直接兼容前端 query params。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobName;

    private String jobGroup;

    private String status;

    private String invokeTarget;
}
