package com.han.job.domain.query;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.common.core.domain.query.BaseQuery;
import com.han.job.domain.po.SysJobLogPo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务日志查询对象（采用组合模式）
 *
 * @author han Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobLogQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组合SysJobLog实体
     */
    @JsonUnwrapped
    private SysJobLogPo base;
}
