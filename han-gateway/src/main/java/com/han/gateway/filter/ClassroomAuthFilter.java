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

/** 三个课堂请求的数字校园换票和内部短时令牌校验。 */
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
        if (!isClassroomPath(path) || isLegacyPartnerPath(path) || isSingleLogin(path)) {
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
            return error(exchange, HttpStatus.UNAUTHORIZED,
                    "Internal classroom token is not accepted by the legacy relay");
        }
        return exchangeExternalToken(exchange, chain, incomingToken, identityId);
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

    private Mono<Void> cacheAndForward(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String cacheKey, String token, String externalToken) {
        ClassroomTokenCodec.VerifiedToken verified = verify(token);
        if (verified == null) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Invalid exchanged classroom token");
        }
        long ttl = Math.max(1, verified.expiresAt() - Instant.now().getEpochSecond());
        return redisTemplate.opsForValue().set(cacheKey, token, Duration.ofSeconds(ttl))
                .then(forward(exchange, chain, externalToken));
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, String internalToken) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .uri(UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                        .replaceQueryParam(TOKEN_HEADER).build(true).toUri())
                .headers(headers -> {
                    headers.set(TOKEN_HEADER, internalToken);
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
