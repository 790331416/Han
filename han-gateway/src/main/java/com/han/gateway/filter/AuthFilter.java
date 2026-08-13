package com.han.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.HanStrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 认证过滤器
 *
 * <p>网关是身份的唯一签发点：先剥离客户端自带的身份头与内部调用头，再按 Redis 会话回写。
 * 下游服务只认这里写出的值，任何客户端伪造的同名头都到不了下游。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 会话读取失败的哨兵值，用于把「依赖故障」与「会话不存在」区分开（真实会话 JSON 不可能等于该值） */
    private static final String SESSION_UNAVAILABLE = "\u0000session-unavailable";

    /** 会话不存在的哨兵值 */
    private static final String SESSION_MISSING = "\u0000session-missing";

    /**
     * 免认证白名单。
     *
     * <p>匹配规则见 {@link #isWhitelist}：以 {@code /} 结尾的条目按前缀匹配，
     * 其余条目要求「完全相等」或「后接 {@code /}」，避免 {@code /tenant/all} 意外放行
     * {@code /tenant/allExport} 这类新增接口。
     *
     * <p>租户相关条目的收敛已完成：{@code /tenant/all}、{@code /tenant/listAllValid}、
     * {@code /tenant/domain/} 当初进白名单，是因为跨服务契约指向了 A 层管理端路径、且客户端不带鉴权头。
     * 契约现已迁到 {@code /inner/tenant}（带 {@code @InnerAuth} 签名校验，由
     * {@code TenantServiceClientContractTest} 守住），三条 A 层路径不再需要免认证，已摘除。
     * 登录页所需的未认证租户信息改由 {@code /tenant/public/} 提供，返回体只含租户 ID 与名称。
     */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/app/login",
            "/auth/wechat/mp/login",
            "/auth/wechat/oa/login",
            "/auth/refresh",
            "/auth/logout",
            "/auth/captcha",
            "/auth/publicKey",
            "/auth/social/",
            "/tenant/public/",
            "/oauth2/authorize",
            "/oauth2/token",
            "/oauth2/revoke",
            "/oauth2/introspect",
            "/oauth2/userinfo",
            "/oauth2/.well-known/",
            "/open/oauth2/authorize",
            "/open/oauth2/token",
            "/open/oauth2/revoke",
            "/open/oauth2/introspect",
            "/open/oauth2/userinfo",
            "/open/oauth2/.well-known/",
            "/sso/login",
            "/sso/logout",
            "/sso/check",
            "/sso/validate",
            "/open/sso/login",
            "/open/sso/logout",
            "/open/sso/check",
            "/open/sso/validate",
            "/system/runtime/capabilities",
            "/ai/share/",
            "/file/public/",
            "/aivideo/public/"
    );

    /** 网关下发、客户端不得自带的头前缀（内部调用鉴权头全族） */
    private static final String INNER_HEADER_PREFIX = "x-inner-";

    /** 网关下发、客户端不得自带的固定头 */
    private static final List<String> GATEWAY_MANAGED_HEADERS = List.of(
            Constants.USER_ID_HEADER,
            Constants.USERNAME_HEADER,
            Constants.TENANT_ID_HEADER,
            Constants.CLIENT_TYPE_HEADER,
            Constants.DEVICE_ID_HEADER
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = stripSpoofedHeaders(exchange.getRequest());
        String path = sanitizedRequest.getURI().getPath();

        if (isWhitelist(path)) {
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        String token = getToken(sanitizedRequest);
        if (HanStrUtil.isBlank(token)) {
            return unauthorized(exchange, "未携带 Token");
        }

        String cacheKey = CacheConstants.TOKEN_KEY + token;
        return redisTemplate.opsForValue().get(cacheKey)
                // 只兜住会话读取本身的故障，不能连带吞掉下游业务异常
                .onErrorResume(e -> {
                    log.error("读取会话失败，认证依赖不可用: path={}", path, e);
                    return Mono.just(SESSION_UNAVAILABLE);
                })
                // 会话缺失必须在 flatMap 之前转成哨兵：flatMap 的结果是 Mono<Void>，正常放行时同样
                // 以「空完成」结束，用 switchIfEmpty 收尾会在请求已转发之后再写一次 401
                .defaultIfEmpty(SESSION_MISSING)
                .flatMap(userJson -> {
                    if (SESSION_UNAVAILABLE.equals(userJson)) {
                        // 鉴权 fail-close，但依赖故障应给 503 而不是裸 500
                        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, "认证服务暂不可用，请稍后重试");
                    }
                    if (SESSION_MISSING.equals(userJson)) {
                        return unauthorized(exchange, "Token 无效或已过期");
                    }
                    ServerHttpRequest.Builder reqBuilder = sanitizedRequest.mutate();
                    reqBuilder.headers(headers -> headers.set(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + token));
                    try {
                        JsonNode node = MAPPER.readTree(userJson);
                        if (node.has("userId")) {
                            reqBuilder.headers(headers -> headers.set(Constants.USER_ID_HEADER, node.get("userId").asText()));
                        }
                        if (node.has("username")) {
                            reqBuilder.headers(headers -> headers.set(Constants.USERNAME_HEADER, node.get("username").asText()));
                        }
                        if (node.has("tenantId") && !node.get("tenantId").isNull()) {
                            reqBuilder.headers(headers -> headers.set(Constants.TENANT_ID_HEADER, node.get("tenantId").asText()));
                        }
                    } catch (Exception e) {
                        // 会话数据损坏时放行只会让下游同样解析失败，最终表现为 500/403，排障链路更长
                        log.warn("会话数据解析失败，拒绝请求: key={}", cacheKey, e);
                        return unauthorized(exchange, "会话数据异常，请重新登录");
                    }
                    return chain.filter(exchange.mutate().request(reqBuilder.build()).build());
                });
    }

    /**
     * 剥离客户端自带的、由网关统一下发的请求头。
     *
     * <p>内部调用头按 {@code X-Inner-} 前缀整族剥离，避免新增一个内部头就漏加一次。
     */
    private ServerHttpRequest stripSpoofedHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            GATEWAY_MANAGED_HEADERS.forEach(headers::remove);
            List<String> innerHeaders = headers.headerNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(INNER_HEADER_PREFIX))
                    .toList();
            innerHeaders.forEach(headers::remove);
        }).build();
    }

    /**
     * 白名单匹配：以 {@code /} 结尾按前缀放行，否则要求精确匹配或后接路径分隔符。
     */
    private boolean isWhitelist(String path) {
        for (String entry : WHITE_LIST) {
            if (entry.endsWith("/")) {
                if (path.startsWith(entry)) {
                    return true;
                }
            } else if (path.equals(entry) || path.startsWith(entry + "/")) {
                return true;
            }
        }
        return false;
    }

    private String getToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
        if (HanStrUtil.isNotBlank(header)) {
            // 只认 Bearer；Basic 等其它方案不再被当作 token 原样拼进 Redis key
            return header.startsWith(Constants.TOKEN_PREFIX)
                    ? header.substring(Constants.TOKEN_PREFIX.length())
                    : null;
        }
        return isSseTokenRequest(request) ? request.getQueryParams().getFirst("token") : null;
    }

    /**
     * 兼容浏览器 EventSource 无法自定义 Authorization 头的场景。
     *
     * <p>仅对 SSE 请求开放 token 查询参数透传，避免放宽普通接口认证边界。
     */
    private boolean isSseTokenRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        if (!path.endsWith("/sse")) {
            return false;
        }
        List<MediaType> acceptTypes = request.getHeaders().getAccept();
        return acceptTypes.isEmpty()
                || acceptTypes.stream().anyMatch(type -> MediaType.TEXT_EVENT_STREAM.isCompatibleWith(type));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, Constants.UNAUTHORIZED, message);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"code\":" + code + ",\"msg\":\"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.AUTH;
    }
}
