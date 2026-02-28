package com.han.job.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁
 * JobFlow 核心特性：分片强约束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;

    // Lua 脚本：原子性释放锁
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 尝试获取锁
     *
     * @param lockKey 锁的键
     * @param lockValue 锁的值（唯一标识）
     * @param timeout 锁的超时时间
     * @param unit 时间单位
     * @return true-获取成功，false-获取失败
     */
    public boolean tryLock(String lockKey, String lockValue, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, timeout, unit);
            
            boolean locked = Boolean.TRUE.equals(result);
            
            if (locked) {
                log.debug("获取分布式锁成功: key={}, value={}", lockKey, lockValue);
            } else {
                log.debug("获取分布式锁失败: key={}, 已被其他实例持有", lockKey);
            }
            
            return locked;
        } catch (Exception e) {
            log.error("获取分布式锁异常: key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放锁（使用 Lua 脚本保证原子性）
     *
     * @param lockKey 锁的键
     * @param lockValue 锁的值
     * @return true-释放成功，false-释放失败
     */
    public boolean unlock(String lockKey, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
            
            boolean unlocked = Long.valueOf(1).equals(result);
            
            if (unlocked) {
                log.debug("释放分布式锁成功: key={}", lockKey);
            } else {
                log.warn("释放分布式锁失败: key={}, 锁已不存在或被其他实例持有", lockKey);
            }
            
            return unlocked;
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 延长锁的过期时间
     *
     * @param lockKey 锁的键
     * @param timeout 延长的时间
     * @param unit 时间单位
     * @return true-延长成功，false-延长失败
     */
    public boolean renewLock(String lockKey, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.expire(lockKey, timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("延长锁过期时间失败: key={}", lockKey, e);
            return false;
        }
    }
}
