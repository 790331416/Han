package com.han.common.security.aspect;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.RateLimiter;

import com.han.common.security.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 限流切面（Redis 滑动窗口）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua 脚本：滑动窗口限流
     * KEYS[1] = 限流key
     * ARGV[1] = 窗口大小（秒）
     * ARGV[2] = 最大请求数
     * ARGV[3] = 当前时间戳（毫秒）
     * 返回 1 = 允许，0 = 拒绝
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local window = tonumber(ARGV[1]) * 1000
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local clearBefore = now - window
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
                redis.call('PEXPIRE', key, window)
                return 1
            end
            return 0
            """, Long.class);

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) {
        String combineKey = buildKey(point, rateLimiter);
        long now = System.currentTimeMillis();

        Long allowed = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(combineKey),
                String.valueOf(rateLimiter.time()),
                String.valueOf(rateLimiter.count()),
                String.valueOf(now)
        );

        if (allowed == null || allowed == 0L) {
            log.warn("限流触发: key={}, limit={}/{}", combineKey, rateLimiter.count(), rateLimiter.time());
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
    }

    private String buildKey(JoinPoint point, RateLimiter rateLimiter) {
        StringBuilder sb = new StringBuilder(CacheConstants.RATE_LIMIT_KEY);

        // 自定义key优先
        if (!rateLimiter.key().isEmpty()) {
            sb.append(rateLimiter.key());
        } else {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            sb.append(method.getDeclaringClass().getName()).append(":").append(method.getName());
        }

        // 按限流类型追加后缀
        switch (rateLimiter.limitType()) {
            case IP -> sb.append(":").append(getClientIp());
            case USER -> {
                Long userId = SecurityContextHolder.getUserId();
                if (userId != null) {
                    sb.append(":").append(userId);
                } else {
                    sb.append(":").append(getClientIp());
                }
            }
            default -> { /* 全局限流，不追加 */ }
        }

        return sb.toString();
    }

    private String getClientIp() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.substring(0, ip.indexOf(",")).trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("获取客户端IP失败", e);
        }
        return "unknown";
    }
}
