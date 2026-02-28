package com.han.job.domain.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.job.domain.entity.SysJob;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务创建/更新 DTO（采用组合模式）
 * 
 * @author han Team
 */
@Data
public class JobDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonUnwrapped
    private SysJob base;

    // ==================== 核心业务字段便捷访问 ====================

    public Long getJobId() {
        return base != null ? base.getJobId() : null;
    }

    public void setJobId(Long jobId) {
        if (base == null) {
            base = new SysJob();
        }
        base.setJobId(jobId);
    }
}
