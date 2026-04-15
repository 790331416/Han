package com.han.job.domain.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志持久化对象。
 */
@Data
@TableName("sys_job_log")
public class SysJobLogPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long jobLogId;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String jobName;

    private String jobGroup;

    private String invokeTarget;

    private String traceId;

    private String jobMessage;

    private String status;

    private String exceptionInfo;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
