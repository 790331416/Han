package com.han.job.controller;

import com.han.job.config.JobFlowSchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * JobFlow 监控端点。
 */
@Slf4j
@RestController
@RequestMapping("/actuator/jobflow")
@RequiredArgsConstructor
public class JobFlowMonitorController {

    private final Scheduler scheduler;
    private final JobFlowSchedulerProperties properties;

    /**
     * 获取调度器健康状态。
     *
     * @return 健康信息
     * @throws SchedulerException 调度异常
     */
    @GetMapping("/health")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public Map<String, Object> health() throws SchedulerException {
        Map<String, Object> health = new HashMap<>();
        health.put("status", scheduler.isStarted() ? "UP" : "DOWN");
        health.put("schedulerName", scheduler.getSchedulerName());
        health.put("schedulerInstanceId", scheduler.getSchedulerInstanceId());
        health.put("inStandbyMode", scheduler.isInStandbyMode());
        health.put("numberOfJobsExecuted", scheduler.getMetaData().getNumberOfJobsExecuted());
        health.put("runningSince", scheduler.getMetaData().getRunningSince());
        return health;
    }

    /**
     * 获取当前调度配置。
     *
     * @return 调度配置快照
     */
    @GetMapping("/config")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public Map<String, Object> config() {
        log.info("查询 JobFlow 配置: {}", properties);
        Map<String, Object> config = new HashMap<>();
        config.put("threadPoolSize", normalizeNumeric(properties.getThreadPoolSize()));
        config.put("timeout", normalizeNumeric(properties.getTimeout()));
        config.put("maxRetry", normalizeNumeric(properties.getMaxRetry()));
        config.put("connectTimeout", normalizeNumeric(properties.getConnectTimeout()));
        config.put("readTimeout", normalizeNumeric(properties.getReadTimeout()));
        config.put("lockTimeout", normalizeNumeric(properties.getLockTimeout()));
        config.put("compensationEnabled", properties.getCompensationEnabled());
        config.put("compensationInterval", normalizeNumeric(properties.getCompensationInterval()));
        config.put("stuckThreshold", normalizeNumeric(properties.getStuckThreshold()));
        return config;
    }

    /**
     * 获取运行指标。
     *
     * @return 调度指标
     * @throws SchedulerException 调度异常
     */
    @GetMapping("/metrics")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public Map<String, Object> metrics() throws SchedulerException {
        Map<String, Object> metrics = new HashMap<>();
        var metadata = scheduler.getMetaData();
        metrics.put("totalJobsExecuted", metadata.getNumberOfJobsExecuted());
        metrics.put("threadPoolSize", metadata.getThreadPoolSize());
        metrics.put("version", metadata.getVersion());
        metrics.put("clustered", metadata.isJobStoreClustered());
        return metrics;
    }

    private Object normalizeNumeric(Object value) {
        if (value instanceof Long longValue && longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }
        if (value instanceof String text && text.matches("-?\\d+")) {
            try {
                long parsed = Long.parseLong(text);
                if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                    return (int) parsed;
                }
                return parsed;
            } catch (NumberFormatException ex) {
                log.warn("JobFlow 配置数值转换失败: {}", text, ex);
            }
        }
        return value;
    }
}
