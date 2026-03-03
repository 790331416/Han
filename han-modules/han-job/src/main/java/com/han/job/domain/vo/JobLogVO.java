package com.han.job.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务日志 VO
 */
@Data
public class JobLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long jobLogId;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String jobMessage;
    private String status;
    private String exceptionInfo;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private long costTime;
}
