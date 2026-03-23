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
     * @return 调度配置
     */
    @GetMapping("/config")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public JobFlowSchedulerProperties config() {
        log.info("查询 JobFlow 配置: {}", properties);
        return properties;
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
}
