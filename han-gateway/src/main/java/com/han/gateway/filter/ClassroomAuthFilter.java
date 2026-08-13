package com.han.gateway.filter;

import com.han.common.core.util.ClassroomTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 三个课堂请求的入口鉴权，两条通路并存。
 *
 * <ul>
 *   <li>本地账号：请求带的就是 Han 签发的兼容凭证，验签加会话有效性后原样透传；</li>
 *   <li>数字校园：请求带的是外部 Token，换票拿到内部令牌做有效性判断，向下游仍透传外部 Token。</li>
 * </ul>
 *
 * <p>旧 api 只解 JWT payload、不验签，所以签名校验必须在这里完成，旧 api 端口不能对外暴露。
 */
@Component
public class ClassroomAuthFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_HEADER = "access-token";
    private static final String IDENTITY_HEADER = "x-identity-id";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final boolean enabled;
    private final String secret;
    private final String exchangeUri;

    public ClassroomAuthFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @LoadBalanced WebClient.Builder webClientBuilder,
            @Value("${sdfz.classroom-gateway.enabled:false}") boolean enabled,
            @Value("${sdfz.classroom-gateway.token-secret:}") String secret,
            @Value("${sdfz.classroom-gateway.exchange-uri:http://han-auth/auth/external/digital-campus/classroom-token}") String exchangeUri) {
        this.redisTemplate = redisTemplate;
        this.webClient = webClientBuilder.build();
        this.enabled = enabled;
        this.secret = secret;
        this.exchangeUri = exchangeUri;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isClassroomPath(path) || isLegacyPartnerPath(path) || isSingleLogin(path)
                || isAnonymousBusinessPath(path)) {
            return chain.filter(exchange);
        }
        if (!enabled || secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            return error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Classroom gateway is not configured");
        }

        String incomingToken = token(exchange.getRequest());
        if (incomingToken == null || incomingToken.isBlank()) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Missing classroom token");
        }
        String identityId = exchange.getRequest().getHeaders().getFirst(IDENTITY_HEADER);
        if (verify(incomingToken) != null) {
            return relayInternalToken(exchange, chain, incomingToken);
        }
        return exchangeExternalToken(exchange, chain, incomingToken, identityId);
    }

    /**
     * 放行 Han 自己签发的兼容凭证。
     *
     * <p>本地账号线没有数字校园外部 Token，凭证由 Han 直接签发；签名已在 {@link #verify} 校验，
     * 这里只再确认会话没有被主动失效，然后原样透传给旧网关——旧 api 不验签，
     * 入口校验必须在这里完成。
     */
    private Mono<Void> relayInternalToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String internalToken) {
        return isActiveInternalToken(internalToken)
                .flatMap(active -> active
                        ? forward(exchange, chain, internalToken)
                        : error(exchange, HttpStatus.UNAUTHORIZED, "Classroom session is revoked or expired"));
    }

    private Mono<Boolean> isActiveInternalToken(String token) {
        ClassroomTokenCodec.VerifiedToken verified = verify(token);
        if (verified == null) return Mono.just(false);
        return redisTemplate.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + verified.tokenId())
                .defaultIfEmpty(false);
    }

    private Mono<Void> exchangeExternalToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                             String externalToken, String identityId) {
        String cacheKey = exchangeCacheKey(externalToken, identityId);
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> isActiveInternalToken(cached)
                        .map(active -> new CachedExchange(true, active)))
                .defaultIfEmpty(new CachedExchange(false, false))
                .flatMap(cached -> {
                    if (cached.found() && cached.active()) {
                        return forward(exchange, chain, externalToken);
                    }
                    if (cached.found()) {
                        return error(exchange, HttpStatus.UNAUTHORIZED,
                                "Classroom token exchange is revoked");
                    }
                    return requestExchange(externalToken, identityId)
                            .flatMap(token -> cacheAndForward(
                                    exchange, chain, cacheKey, token, externalToken));
                })
                .onErrorResume(TokenExchangeException.class, throwable -> error(exchange, HttpStatus.UNAUTHORIZED,
                        "Digital campus token validation failed"));
    }

    private Mono<String> requestExchange(String externalToken, String identityId) {
        WebClient.RequestBodySpec request = webClient.post()
                .uri(exchangeUri)
                .header(TOKEN_HEADER, externalToken);
        if (identityId != null && !identityId.isBlank()) {
            request.header(IDENTITY_HEADER, identityId);
        }
        return request.retrieve()
                .bodyToMono(ExchangeResponse.class)
                .flatMap(response -> response.code() == 200 && response.data() != null
                                && response.data().accessToken() != null
                        ? Mono.just(response.data().accessToken())
                        : Mono.error(new TokenExchangeException("Token exchange rejected")))
                .onErrorMap(WebClientException.class,
                        throwable -> new TokenExchangeException("Token exchange request failed", throwable));
    }

    /**
     * 缓存换来的内部令牌，但继续向下游透传数字校园令牌。
     *
     * <p>内部令牌只用来判断这次换票是否仍然有效；数字校园通路的下游要拿原始外部令牌校验，
     * 两者不能互换。本地账号通路走 {@link #relayInternalToken}，透传的才是 Han 自己的兼容凭证。
     */
    private Mono<Void> cacheAndForward(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String cacheKey, String internalToken, String externalToken) {
        ClassroomTokenCodec.VerifiedToken verified = verify(internalToken);
        if (verified == null) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Invalid exchanged classroom token");
        }
        long ttl = Math.max(1, verified.expiresAt() - Instant.now().getEpochSecond());
        return redisTemplate.opsForValue().set(cacheKey, internalToken, Duration.ofSeconds(ttl))
                .then(forward(exchange, chain, externalToken));
    }

    /** @param relayedToken 实际写进下游 {@code access-token} 头的令牌，由各通路自行决定 */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, String relayedToken) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .uri(UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                        .replaceQueryParam(TOKEN_HEADER).build(true).toUri())
                .headers(headers -> {
                    headers.set(TOKEN_HEADER, relayedToken);
                    headers.remove(IDENTITY_HEADER);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private ClassroomTokenCodec.VerifiedToken verify(String token) {
        try {
            return ClassroomTokenCodec.verify(token, secret, Instant.now().getEpochSecond());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String token(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(TOKEN_HEADER);
        return token != null && !token.isBlank()
                ? token : request.getQueryParams().getFirst(TOKEN_HEADER);
    }

    private static boolean isClassroomPath(String path) {
        return path.startsWith("/tcapi/") || path.startsWith("/ysfz-tcapi/");
    }

    /**
     * 旧前端显式标注 {@code noAuth: true} 的业务接口，逐条列举、不使用通配。
     *
     * <p>这两条在旧前端里就是不带 {@code access-token} 发出的（见 `api/http/request.ts`
     * 的 {@code config.noAuth} 分支），要求凭证会直接把它们打成 401：
     * <ul>
     *   <li>{@code tb-course-info/getCourseInfoByRoomId}：按教室号取当前课程，
     *       教室大屏/录播设备在没有用户登录态的情况下调用；</li>
     *   <li>{@code live/userCourseSharingInfo}：课程分享页，收到分享链接的人尚未登录。</li>
     * </ul>
     *
     * <p>新增条目前请先确认调用方**确实拿不到凭证**，而不是图省事。
     * 这两条同时也必须留在旧网关的 {@code param-filter.ignoreUrls} 里，
     * 否则请求过了这里、会在旧网关因缺 token 被拒。
     */
    private static final java.util.List<String> ANONYMOUS_BUSINESS_SUFFIXES = java.util.List.of(
            "/tb-course-info/getCourseInfoByRoomId",
            "/live/userCourseSharingInfo");

    private static boolean isAnonymousBusinessPath(String path) {
        return ANONYMOUS_BUSINESS_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    private static boolean isSingleLogin(String path) {
        return path.endsWith("/user/singleLogin");
    }

    private static boolean isLegacyPartnerPath(String path) {
        return path.startsWith("/tcapi/parent/") || path.startsWith("/ysfz-tcapi/parent/");
    }

    static String exchangeCacheKey(String externalToken, String identityId) {
        String identityCachePart = identityId == null ? "" : identityId.trim();
        return ClassroomTokenCodec.EXCHANGE_KEY_PREFIX
                + ClassroomTokenCodec.sha256(externalToken + "\n" + identityCachePart);
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":" + status.value() + ",\"msg\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -110;
    }

    private record ExchangeResponse(int code, String msg, ClassroomTokenData data) { }
    private record ClassroomTokenData(String accessToken, long expiresIn) { }
    private record CachedExchange(boolean found, boolean active) { }

    private static final class TokenExchangeException extends RuntimeException {
        private TokenExchangeException(String message) {
            super(message);
        }

        private TokenExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
