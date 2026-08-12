package com.han.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局限流过滤器 — 基于 IP 的 Redis 滑动窗口限流
 * <p>默认每个 IP 每秒最多 50 次请求，超出返回 429
 * <p>INCR 与 EXPIRE 通过 Lua 脚本原子执行，避免两步操作间网关重启或 Redis 抖动
 * 导致 key 无 TTL 而把 IP 永久封禁；脚本内对存量 TTL&lt;0 的 key 做兜底补设过期。
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int MAX_REQUESTS_PER_SECOND = 50;
    private static final int WINDOW_SECONDS = 1;
    private static final String RATE_LIMIT_PREFIX = "gateway:rate_limit:";

    /**
     * 原子化计数：INCR 后在同一脚本内保证 key 一定带 TTL。
     * TTL 判断使用 < 0 兜底历史遗留的永生 key（TTL 返回 -1 表示无过期时间）。
     */
    static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "end "
                    + "return current",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange.getRequest());
        String key = RATE_LIMIT_PREFIX + ip;

        return redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), List.of(String.valueOf(WINDOW_SECONDS)))
                .next()
                // 脚本无返回（如 Redis 异常恢复期）按放行处理，避免误伤正常流量
                .defaultIfEmpty(1L)
                .flatMap(count -> {
                    if (count > MAX_REQUESTS_PER_SECOND) {
                        log.warn("IP[{}]请求过于频繁，已限流（{}次/秒）", ip, count);
                        return tooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        return ip;
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":429,\"msg\":\"请求过于频繁，请稍后重试\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
