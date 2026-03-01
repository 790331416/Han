package com.han.job.domain.query;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.common.core.domain.query.BaseQuery;
import com.han.job.domain.po.SysJobPo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务查询对象（采用组合模式）
 *
 * @author han Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组合SysJob实体
     */
    @JsonUnwrapped
    private SysJobPo base;
}
