package com.han.job.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务创建/更新 DTO，直接对齐前端扁平字段结构。
 */
@Data
public class JobDTO implements Serializable {

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

    private String updateBy;

    private LocalDateTime updateTime;
}
