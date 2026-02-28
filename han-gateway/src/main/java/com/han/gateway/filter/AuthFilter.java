package com.han.gateway.filter;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.XuStrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 认证过滤器（WebFlux 响应式）
 */
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    /** 白名单路径 */
    private static final List<String> WHITE_LIST = List.of(
        "/auth/login",
        "/auth/app/login",
        "/auth/wechat/mp/login",
        "/auth/wechat/oa/login",
        "/auth/refresh",
        "/auth/logout",
        "/auth/register",
        "/auth/captcha",
        "/doc.html",
        "/swagger-resources",
        "/v3/api-docs"
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isWhitelist(path)) {
            return chain.filter(exchange);
        }

        // 获取Token
        String token = getToken(request);
        if (XuStrUtil.isBlank(token)) {
            return unauthorized(exchange, "未携带Token");
        }

        // 验证Token
        String cacheKey = CacheConstants.TOKEN_KEY + token;
        return redisTemplate.hasKey(cacheKey)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    // Token有效，传递用户信息到下游
                    ServerHttpRequest mutatedRequest = request.mutate()
                        .header(Constants.AUTHORIZATION_HEADER, token)
                        .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                } else {
                    return unauthorized(exchange, "Token无效或已过期");
                }
            });
    }

    private boolean isWhitelist(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
        if (XuStrUtil.isNotBlank(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            return token.substring(Constants.TOKEN_PREFIX.length());
        }
        return token;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"msg\":\"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
