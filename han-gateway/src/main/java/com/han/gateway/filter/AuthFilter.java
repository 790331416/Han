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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PUBLIC_BRAND_PATH = "/system/public/brand";
    private static final String PUBLIC_BRAND_LOGO_PATH = "/system/public/brand/logo";

    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/h5/login",
            "/auth/app/login",
            "/auth/wechat/mp/login",
            "/auth/wechat/oa/login",
            "/auth/refresh",
            "/auth/logout",
            "/auth/register",
            "/auth/captcha",
            "/auth/publicKey",
            "/auth/social/",
            "/auth/external/digital-campus",
            "/tenant/all",
            "/tenant/listAllValid",
            "/tenant/domain/",
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
            // 开放目录由 han-open 校验 OAuth2 Client Credentials Token，不能按 Han 用户 Token 处理。
            "/open/api/",
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
            "/aivideo/public/",
            "/file/public/",
            "/doc.html",
            "/swagger-resources",
            "/v3/api-docs"
    );

    /**
     * 由三课堂兼容凭证而非 Han 登录态鉴权的前缀。
     *
     * <p><b>这不是免认证清单。</b>这些前缀的调用方是旧三课堂（旧 api 服务端与旧前端），
     * 手上没有 Han Token，按 Han Token 拦截会让所有调用拿到 401；
     * 它们的身份校验由 {@link ClassroomAuthFilter} 与 {@link LegacyCompatAuthFilter} 完成，
     * 两者的 order 都小于本过滤器，会先执行并在校验不过时直接拒绝。
     *
     * <p>移除这两个过滤器中的任何一个之前，必须先把对应前缀从这里删掉，否则会变成真正的裸奔。
     */
    private static final List<String> CLASSROOM_AUTHENTICATED_PREFIXES = List.of(
            "/tcapi/",
            "/ysfz-tcapi/",
            LegacyCompatAuthFilter.PREFIX
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = stripSpoofedHeaders(exchange.getRequest());
        String path = sanitizedRequest.getURI().getPath();

        if (isPublicBrandRead(sanitizedRequest) || isWhitelist(path)
                || isPublicVendorAuthRequest(sanitizedRequest)) {
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        String token = getToken(sanitizedRequest);
        if (HanStrUtil.isBlank(token)) {
            return unauthorized(exchange, "未携带 Token");
        }

        String cacheKey = CacheConstants.TOKEN_KEY + token;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(userJson -> {
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
                        log.warn("解析用户信息失败", e);
                    }
                    return chain.filter(exchange.mutate().request(reqBuilder.build()).build());
                })
                .switchIfEmpty(unauthorized(exchange, "Token 无效或已过期"));
    }

    private ServerHttpRequest stripSpoofedHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            headers.remove(Constants.USER_ID_HEADER);
            headers.remove(Constants.USERNAME_HEADER);
            headers.remove(Constants.TENANT_ID_HEADER);
            headers.remove(Constants.INNER_AUTH_CLIENT_HEADER);
            headers.remove(Constants.INNER_AUTH_TIMESTAMP_HEADER);
            headers.remove(Constants.INNER_AUTH_SIGNATURE_HEADER);
        }).build();
    }

    /** 命中即跳过 Han Token 校验：要么本就公开，要么已由三课堂兼容过滤器完成鉴权。 */
    private boolean isWhitelist(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith)
                || CLASSROOM_AUTHENTICATED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 登录页只需要读取不含敏感信息的品牌文案。
     * 必须严格限定为精确路径的 GET，不能把整个 /system/public/** 前缀放进白名单。
     */
    private boolean isPublicBrandRead(ServerHttpRequest request) {
        return HttpMethod.GET.equals(request.getMethod())
                && (PUBLIC_BRAND_PATH.equals(request.getURI().getPath())
                || PUBLIC_BRAND_LOGO_PATH.equals(request.getURI().getPath()));
    }

    /** 厂商注册只公开精确 auth 路径，不能放行整个 vendor 前缀。 */
    private boolean isPublicVendorAuthRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        if (HttpMethod.GET.equals(request.getMethod())
                && "/auth/vendor/publicKey".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.equals(request.getMethod())
                && "/auth/vendor/register".equals(path)) {
            return true;
        }
        return HttpMethod.GET.equals(request.getMethod())
                && "/auth/vendor/application/status".equals(path);
    }

    private String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
        if (HanStrUtil.isNotBlank(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            return token.substring(Constants.TOKEN_PREFIX.length());
        }
        if (HanStrUtil.isBlank(token) && isSseTokenRequest(request)) {
            token = request.getQueryParams().getFirst("token");
        }
        return token;
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
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"msg\":\"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
