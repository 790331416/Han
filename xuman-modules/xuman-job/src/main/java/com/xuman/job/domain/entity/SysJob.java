package com.xuman.job.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务实体
 */
@Data
@TableName("sys_job")
public class SysJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long jobId;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务组名
     */
    private String jobGroup;

    /**
     * 调用目标(Bean名称)
     */
    private String invokeTarget;

    /**
     * cron执行表达式
     */
    private String cronExpression;

    /**
     * 计划执行错误策略(1立即执行 2执行一次 3放弃执行)
     */
    private String misfirePolicy;

    /**
     * 是否并发执行(0允许 1禁止)
     */
    private String concurrent;

    /**
     * 任务状态(0正常 1暂停)
     */
    private String status;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
