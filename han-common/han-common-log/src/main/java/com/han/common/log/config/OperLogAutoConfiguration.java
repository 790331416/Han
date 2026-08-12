package com.han.common.log.config;

import com.han.common.log.aspect.OperLogAspect;
import com.han.common.log.service.IOperLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志自动配置。
 * <p>
 * 切面此前的注册条件是 {@code @ConditionalOnBean(IOperLogService.class)}，而全仓
 * {@code IOperLogService} 只有 han-system 一个实现。han-tenant / han-job / han-ai / han-open
 * 都依赖了本模块但容器里没有该 Bean，条件不成立，切面根本不会被创建 ——
 * 租户新增、修改、启停、删除、配额调整这些最敏感的操作一条日志都不产生，
 * 而且失败得完全静默：没有启动告警，也没有降级日志，开发和运维都以为审计已经生效。
 * <p>
 * 现在改为无条件注册切面：有本地实现就写库，没有就在启动时打 ERROR 并把每条审计事件
 * 降级写到本地日志。可见地降级，好过静默地什么都不做。
 */
@AutoConfiguration
@EnableConfigurationProperties(OperLogProperties.class)
@ConditionalOnProperty(prefix = "han.log.oper", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperLogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperLogAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OperLogAspect operLogAspect(ObjectProvider<IOperLogService> operLogService,
                                       OperLogProperties properties,
                                       Executor operLogExecutor) {
        IOperLogService service = operLogService.getIfAvailable();
        if (service == null) {
            log.error("[OperLog] 当前服务依赖了 han-common-log 但容器中没有 IOperLogService 实现，"
                    + "@OperLog 标注的操作将只写本地日志、不会入库。"
                    + "如果本服务有需要留痕的敏感操作，请提供实现或接入 han-system 的日志写入接口。");
        }
        return new OperLogAspect(service, properties, operLogExecutor);
    }

    /**
     * 操作日志专用线程池。
     * <p>原先直接 {@code CompletableFuture.runAsync} 用的是 {@code ForkJoinPool.commonPool()}：
     * 并行度只有 CPU 核数减一，还被 parallel stream 等共享，往里塞阻塞式 JDBC 写入会拖累整个 JVM，
     * 且提交无上限、无背压，突发流量只能堆到 OOM。
     */
    @Bean(name = "operLogExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "operLogExecutor")
    public Executor operLogExecutor(OperLogProperties properties) {
        OperLogProperties.Async async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setKeepAliveSeconds(async.getKeepAliveSeconds());
        executor.setThreadNamePrefix("oper-log-");
        // 队列满时由调用线程执行：审计日志宁可拖慢请求，也不能悄悄丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
