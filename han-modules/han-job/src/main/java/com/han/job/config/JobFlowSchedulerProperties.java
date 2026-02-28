package com.han.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * JobFlow 调度器配置
 * 支持 Nacos 动态配置
 */
@Data
@Component
@RefreshScope  // 支持配置动态刷新
@ConfigurationProperties(prefix = "jobflow.scheduler")
public class JobFlowSchedulerProperties {

    /**
     * 调度线程池大小
     */
    private Integer threadPoolSize = 20;

    /**
     * 默认超时时间（秒）
     */
    private Integer timeout = 300;

    /**
     * 默认重试次数
     */
    private Integer maxRetry = 3;

    /**
     * HTTP 连接超时（毫秒）
     */
    private Integer connectTimeout = 5000;

    /**
     * HTTP 读取超时（毫秒）
     */
    private Integer readTimeout = 30000;

    /**
     * 分片锁超时时间（秒）
     */
    private Integer lockTimeout = 60;

    /**
     * 补偿任务是否启用
     */
    private Boolean compensationEnabled = true;

    /**
     * 补偿任务间隔（毫秒）
     */
    private Long compensationInterval = 60000L;

    /**
     * 卡住阈值（毫秒，10分钟）
     */
    private Long stuckThreshold = 600000L;
}
