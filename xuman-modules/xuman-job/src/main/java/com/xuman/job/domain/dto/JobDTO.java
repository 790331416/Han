package com.xuman.job.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务创建/更新 DTO
 */
@Data
public class JobDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID(更新时必填)
     */
    private Long jobId;

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称长度不能超过64个字符")
    private String jobName;

    /**
     * 任务组名
     */
    @NotBlank(message = "任务组名不能为空")
    @Size(max = 64, message = "任务组名长度不能超过64个字符")
    private String jobGroup;

    /**
     * 调用目标(Bean名称)
     */
    @NotBlank(message = "调用目标不能为空")
    @Size(max = 500, message = "调用目标长度不能超过500个字符")
    private String invokeTarget;

    /**
     * cron执行表达式
     */
    @NotBlank(message = "Cron表达式不能为空")
    @Size(max = 255, message = "Cron表达式长度不能超过255个字符")
    private String cronExpression;

    /**
     * 计划执行错误策略(1立即执行 2执行一次 3放弃执行)
     */
    private String misfirePolicy = "3";

    /**
     * 是否并发执行(0允许 1禁止)
     */
    private String concurrent = "1";

    /**
     * 任务状态(0正常 1暂停)
     */
    private String status = "0";

    /**
     * 备注信息
     */
    private String remark;
}
