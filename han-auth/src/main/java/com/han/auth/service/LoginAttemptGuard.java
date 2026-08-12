package com.han.auth.service;

import com.han.auth.config.LoginSecurityProperties;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 登录失败计数与账号锁定的统一出口。
 *
 * <p>密码登录（{@code /auth/login}）与社交绑定登录（{@code /auth/social/bind}）共用同一份计数，
 * 避免出现「主入口已锁定、旁路仍可继续试密码」的绕过面。
 *
 * <p>计数用 Lua 脚本把 INCR 与 EXPIRE 合并为一次原子调用，写法照抄网关的
 * {@code com.han.gateway.filter.RateLimitFilter#RATE_LIMIT_SCRIPT}：
 * 两步操作之间进程被 kill 或 Redis 抖动会留下无 TTL 的计数 key，
 * 使账号被永久锁定而提示里还在说「N 分钟后再试」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    /**
     * 原子化计数：INCR 后在同一脚本内保证 key 一定带 TTL。
     * TTL 判断使用 &lt; 0 兜底历史遗留的永生 key（TTL 返回 -1 表示无过期时间）。
     */
    static final RedisScript<Long> INCR_WITH_TTL_SCRIPT = RedisScript.of(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "end "
                    + "return current",
            Long.class);

    private static final String LOGIN_FAIL_KEY = CacheConstants.CACHE_PREFIX + "login_fail:";

    private final StringRedisTemplate redisTemplate;
    private final LoginSecurityProperties properties;

    /**
     * 账号处于锁定期时抛出业务异常。
     *
     * <p>读到无 TTL 的存量计数 key 时主动补设过期，不让历史遗留数据把账号锁死。
     */
    public void assertNotLocked(String username, Long tenantId) {
        String key = buildKey(username, tenantId);
        String failCount = redisTemplate.opsForValue().get(key);
        if (failCount == null) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(failCount);
        } catch (NumberFormatException e) {
            log.warn("登录失败计数[{}]内容非法，已重置", key);
            redisTemplate.delete(key);
            return;
        }
        if (count < properties.getMaxAttempts()) {
            return;
        }

        Duration lockout = properties.getLockoutDuration();
        Long ttl = redisTemplate.getExpire(key);
        long remainingSeconds;
        if (ttl != null && ttl > 0) {
            remainingSeconds = ttl;
        } else {
            // 永生 key 兜底：补设过期后按完整锁定时长提示，保证一定能自动解锁
            redisTemplate.expire(key, lockout);
            remainingSeconds = lockout.toSeconds();
            log.warn("登录失败计数[{}]缺少 TTL，已补设为{}分钟", key, lockout.toMinutes());
        }
        long minutes = (remainingSeconds + 59) / 60;
        throw new BusinessException("账户已锁定，请" + minutes + "分钟后再试");
    }

    /**
     * 记录一次登录失败。
     *
     * @return 锁定前的剩余尝试次数（&lt;= 0 表示本次失败后已锁定）
     */
    public int recordFailure(String username, Long tenantId) {
        long count = incrementWithTtl(buildKey(username, tenantId), properties.getLockoutDuration());
        return properties.getMaxAttempts() - (int) count;
    }

    public void clear(String username, Long tenantId) {
        redisTemplate.delete(buildKey(username, tenantId));
    }

    public int getMaxAttempts() {
        return properties.getMaxAttempts();
    }

    public Duration getLockoutDuration() {
        return properties.getLockoutDuration();
    }

    /**
     * 通用的「自增并保证带 TTL」原子计数，供社交绑定票据等其它计数场景复用。
     *
     * @return 自增后的计数值；Redis 未返回结果时按 1 处理（不误锁正常用户）
     */
    public long incrementWithTtl(String key, Duration ttl) {
        Long count = redisTemplate.execute(INCR_WITH_TTL_SCRIPT, List.of(key), String.valueOf(ttl.toSeconds()));
        return count != null ? count : 1L;
    }

    private String buildKey(String username, Long tenantId) {
        String tenantSegment = tenantId != null ? String.valueOf(tenantId) : "default";
        return LOGIN_FAIL_KEY + tenantSegment + ":" + username;
    }
}
