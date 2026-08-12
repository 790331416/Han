package com.han.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 公开分享接口限流过滤器（G1-9）— shareKey 维度 QPS 与日调用量双重限制。
 * <p>
 * 背景：{@code /ai/share/**} 免登录（AuthFilter 白名单），单靠 IP 级全局限流
 * 无法阻止分布式刷量；本过滤器按分享 key 计数，保护被分享应用背后的模型配额。
 * <p>
 * 策略：
 * <ul>
 *   <li>QPS 限制：作用于全部 /ai/share/{shareKey}/** 请求（含 profile），默认 10 次/秒。</li>
 *   <li>日调用量限制：仅作用于 POST .../chat 对话请求（真正消耗模型 token 的调用），
 *       默认 1000 次/天；key 带日期后缀按自然日重置。</li>
 * </ul>
 * Redis 计数复用 {@link RateLimitFilter#RATE_LIMIT_SCRIPT}：INCR 与 EXPIRE 同一 Lua
 * 脚本原子执行并兜底修复无 TTL 的存量 key，避免计数 key 永生导致永久封禁（参照 026206c 修法）。
 * 超限返回 429 与明确文案；Redis 异常恢复期脚本无返回时按放行处理，避免误伤正常流量。
 */
@Slf4j
@Component
public class ShareRateLimitFilter implements GlobalFilter, Ordered {

    private static final String SHARE_PATH_PREFIX = "/ai/share/";
    private static final String QPS_KEY_PREFIX = "ai:share:rate_limit:qps:";
    private static final String DAILY_KEY_PREFIX = "ai:share:rate_limit:daily:";
    private static final int QPS_WINDOW_SECONDS = 1;
    private static final int DAILY_WINDOW_SECONDS = 24 * 60 * 60;
    /** shareKey 正常为 32 位字母数字；截断超长路径段，防止恶意构造超长 Redis key */
    private static final int MAX_SHARE_KEY_LENGTH = 64;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int qpsLimit;
    private final int dailyChatLimit;

    public ShareRateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                                @Value("${han.gateway.share-rate-limit.enabled:true}") boolean enabled,
                                @Value("${han.gateway.share-rate-limit.qps:10}") int qpsLimit,
                                @Value("${han.gateway.share-rate-limit.daily-chat-limit:1000}") int dailyChatLimit) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.qpsLimit = qpsLimit;
        this.dailyChatLimit = dailyChatLimit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        String shareKey = extractShareKey(path);
        if (shareKey == null) {
            return chain.filter(exchange);
        }
        return checkQps(exchange, chain, shareKey, path);
    }

    private Mono<Void> checkQps(ServerWebExchange exchange, GatewayFilterChain chain, String shareKey, String path) {
        if (qpsLimit <= 0) {
            return checkDailyThenContinue(exchange, chain, shareKey, path);
        }
        String qpsKey = QPS_KEY_PREFIX + shareKey;
        return executeCount(qpsKey, QPS_WINDOW_SECONDS)
                .flatMap(count -> {
                    if (count > qpsLimit) {
                        log.warn("分享应用[{}]QPS超限，已限流（{}次/秒，上限{}）", shareKey, count, qpsLimit);
                        return tooManyRequests(exchange, "分享应用请求过于频繁，请稍后重试");
                    }
                    return checkDailyThenContinue(exchange, chain, shareKey, path);
                });
    }

    private Mono<Void> checkDailyThenContinue(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String shareKey, String path) {
        if (dailyChatLimit <= 0 || !isChatRequest(exchange, path)) {
            return chain.filter(exchange);
        }
        String dailyKey = DAILY_KEY_PREFIX + shareKey + ":" + LocalDate.now().format(DAY_FORMATTER);
        return executeCount(dailyKey, DAILY_WINDOW_SECONDS)
                .flatMap(count -> {
                    if (count > dailyChatLimit) {
                        log.warn("分享应用[{}]日调用量超限，已限流（今日第{}次，上限{}）", shareKey, count, dailyChatLimit);
                        return tooManyRequests(exchange, "分享应用今日调用次数已达上限，请明日再试");
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Long> executeCount(String key, int windowSeconds) {
        return redisTemplate.execute(RateLimitFilter.RATE_LIMIT_SCRIPT,
                        List.of(key), List.of(String.valueOf(windowSeconds)))
                .next()
                // 脚本无返回（如 Redis 异常恢复期）按放行处理，避免误伤正常流量
                .defaultIfEmpty(1L)
                // defaultIfEmpty 兜不住 error 信号：Redis 连接失败/超时时同样降级放行，
                // 限流是保护性设施而非安全边界，不能让它自己成为故障源
                .onErrorResume(e -> {
                    log.warn("分享限流计数失败，降级放行: key={}", key, e);
                    return Mono.just(1L);
                });
    }

    /**
     * 提取 /ai/share/{shareKey}/... 中的 shareKey 段；非分享路径返回 null。
     */
    private String extractShareKey(String path) {
        if (path == null || !path.startsWith(SHARE_PATH_PREFIX)) {
            return null;
        }
        String remainder = path.substring(SHARE_PATH_PREFIX.length());
        int slash = remainder.indexOf('/');
        String shareKey = slash >= 0 ? remainder.substring(0, slash) : remainder;
        if (shareKey.isBlank()) {
            return null;
        }
        return shareKey.length() > MAX_SHARE_KEY_LENGTH ? shareKey.substring(0, MAX_SHARE_KEY_LENGTH) : shareKey;
    }

    /**
     * 仅对话请求计入日调用量（profile 等展示类请求不消耗每日额度）。
     */
    private boolean isChatRequest(ServerWebExchange exchange, String path) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod()) && path.endsWith("/chat");
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":429,\"msg\":\"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        // 位于全局 IP 限流之后、请求日志与认证过滤器之前
        return GatewayFilterOrders.SHARE_RATE_LIMIT;
    }
}
