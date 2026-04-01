package com.han.job.domain.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.han.common.core.domain.query.BaseQuery;
import com.han.job.domain.po.SysJobPo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 定时任务查询对象，兼容 query params 与内部组合结构。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private SysJobPo base;

    @JsonIgnore
    public SysJobPo getBase() {
        return base;
    }

    @JsonIgnore
    public void setBase(SysJobPo base) {
        this.base = base;
    }

    public String getJobName() {
        return base != null ? base.getJobName() : null;
    }

    public void setJobName(String jobName) {
        ensureBase().setJobName(jobName);
    }

    public String getJobGroup() {
        return base != null ? base.getJobGroup() : null;
    }

    public void setJobGroup(String jobGroup) {
        ensureBase().setJobGroup(jobGroup);
    }

    public String getStatus() {
        return base != null ? base.getStatus() : null;
    }

    public void setStatus(String status) {
        ensureBase().setStatus(status);
    }

    public String getInvokeTarget() {
        return base != null ? base.getInvokeTarget() : null;
    }

    public void setInvokeTarget(String invokeTarget) {
        ensureBase().setInvokeTarget(invokeTarget);
    }

    private SysJobPo ensureBase() {
        if (base == null) {
            base = new SysJobPo();
        }
        return base;
    }
}
