package com.han.job.shard;

import com.han.job.context.TraceContext;
import com.han.job.lock.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 分片执行器
 * JobFlow 核心特性：带分布式锁的分片执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShardExecutor {

    private final RedisDistributedLock distributedLock;

    /**
     * 执行分片任务（带分布式锁保护）
     *
     * @param shardRange 分片范围
     * @param processor 业务处理逻辑
     * @param lockTimeout 锁超时时间（秒）
     */
    public void executeWithLock(ShardRange shardRange, Consumer<ShardRange> processor, long lockTimeout) {
        String lockKey = shardRange.getLockKey();
        String lockValue = TraceContext.getTraceId(); // 使用 traceId 作为锁的值

        log.info("准备执行分片任务: {}", shardRange);

        // 尝试获取分布式锁
        boolean locked = distributedLock.tryLock(lockKey, lockValue, lockTimeout, TimeUnit.SECONDS);

        if (!locked) {
            log.warn("分片任务已被其他实例锁定，跳过执行: {}", shardRange);
            return;
        }

        try {
            // 设置分片索引到 MDC
            TraceContext.setShardIndex(shardRange.getShardIndex());

            log.info("开始处理分片数据: {}", shardRange);

            // 执行业务逻辑
            processor.accept(shardRange);

            log.info("分片任务执行成功: {}", shardRange);

        } catch (Exception e) {
            log.error("分片任务执行失败: {}", shardRange, e);
            throw e;
        } finally {
            // 释放锁
            boolean unlocked = distributedLock.unlock(lockKey, lockValue);
            if (!unlocked) {
                log.warn("释放分片锁失败，可能已超时: lockKey={}", lockKey);
            }
        }
    }

    /**
     * 执行分片任务（默认锁超时60秒）
     */
    public void executeWithLock(ShardRange shardRange, Consumer<ShardRange> processor) {
        executeWithLock(shardRange, processor, 60);
    }
}
