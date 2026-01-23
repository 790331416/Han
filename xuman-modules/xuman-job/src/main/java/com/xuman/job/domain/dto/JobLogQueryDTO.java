package com.xuman.job.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务日志查询 DTO
 */
@Data
public class JobLogQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobName;
    private String jobGroup;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
