package com.han.job.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务信息 VO
 */
@Data
public class JobVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long jobId;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String cronExpression;
    private String misfirePolicy;
    private String concurrent;
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime nextFireTime;
}
