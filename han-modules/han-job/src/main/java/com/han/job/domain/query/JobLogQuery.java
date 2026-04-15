package com.han.job.domain.query;

import com.han.common.core.domain.query.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务日志查询对象，直接兼容前端与 E2E 传入的扁平 query params。
 *
 * @author han Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobLogQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobName;

    private String jobGroup;

    private String status;
}
