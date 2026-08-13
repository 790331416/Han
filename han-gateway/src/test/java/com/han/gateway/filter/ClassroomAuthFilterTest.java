package com.han.gateway.filter;

import com.han.common.core.util.ClassroomTokenCodec;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassroomAuthFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /**
     * 旧前端标了 {@code noAuth} 的两条业务接口必须无凭证可达。
     *
     * <p>2026-08-13 把 /ktapi/ 业务流量改走 Han 网关之后，本过滤器成了这条链路的入口鉴权。
     * 这两条接口旧前端本来就不带 access-token 发（教室大屏按教室号取课、分享页未登录访问），
     * 一刀切要求凭证会直接把它们打成 401。
     */
    @Test
    void letsThroughTheTwoBusinessPathsTheLegacyFrontEndCallsWithoutAToken() {
        for (String path : new String[]{
                "/ysfz-tcapi/tb-course-info/getCourseInfoByRoomId",
                "/ysfz-tcapi/live/userCourseSharingInfo",
                "/tcapi/tb-course-info/getCourseInfoByRoomId"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build());
            AtomicBoolean forwarded = new AtomicBoolean();
            new ClassroomAuthFilter(mock(ReactiveStringRedisTemplate.class), WebClient.builder(),
                    true, SECRET, "http://127.0.0.1:1/exchange")
                    .filter(exchange, ignored -> {
                        forwarded.set(true);
                        return Mono.empty();
                    }).block();

            assertThat(forwarded).as("%s 应当无凭证放行", path).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).as("%s 不应被拒", path).isNull();
        }
    }

    /** 免认证清单是逐条精确匹配，不能让同目录下的其它接口跟着裸奔。 */
    @Test
    void doesNotLetThroughNeighbouringPathsUnderTheSameController() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ysfz-tcapi/tb-course-info/getCourseInfoList").build());

        new ClassroomAuthFilter(mock(ReactiveStringRedisTemplate.class), WebClient.builder(),
                true, SECRET, "http://127.0.0.1:1/exchange")
                .filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validatesCachedExchangeAndRelaysExternalTokenWithoutQueryLeak() {
        long now = Instant.now().getEpochSecond();
        String internalToken = ClassroomTokenCodec.issue(
                Map.of("userId", "100", "username", "Teacher"), SECRET, now, 900, "jti-1");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(ClassroomAuthFilter.exchangeCacheKey("raw-external-token", null)))
                .thenReturn(Mono.just(internalToken));
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-1")).thenReturn(Mono.just(true));
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                redis, WebClient.builder(), true, SECRET,
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/tcapi/meeting/list?access-token=raw-external-token")
                .header("access-token", "raw-external-token")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            forwarded.set(next);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("access-token"))
                .isEqualTo("raw-external-token");
        assertThat(forwarded.get().getRequest().getQueryParams()).doesNotContainKey("access-token");
    }

    @Test
    void relaysHanIssuedTokenForLocalAccountsInsteadOfRejectingIt() {
        long now = Instant.now().getEpochSecond();
        String internalToken = ClassroomTokenCodec.issue(
                Map.of("userId", "100", "username", "Teacher", "userType", "USER", "roleType", "2"),
                SECRET, now, 3600, "jti-local");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-local")).thenReturn(Mono.just(true));
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                redis, WebClient.builder(), true, SECRET,
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/ysfz-tcapi/course/list")
                .header("access-token", internalToken)
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("access-token"))
                .isEqualTo(internalToken);
    }

    @Test
    void rejectsHanIssuedTokenWhoseSessionWasRevoked() {
        long now = Instant.now().getEpochSecond();
        String internalToken = ClassroomTokenCodec.issue(
                Map.of("userId", "100"), SECRET, now, 3600, "jti-local-revoked");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-local-revoked"))
                .thenReturn(Mono.just(false));
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                redis, WebClient.builder(), true, SECRET,
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/ysfz-tcapi/course/list")
                .header("access-token", internalToken)
                .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void rejectsClassroomTrafficWhenIntegrationIsNotConfigured() {
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                mock(ReactiveStringRedisTemplate.class), WebClient.builder(), false, "",
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/tcapi/meeting/list").build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void isolatesExternalTokenCacheBySelectedIdentity() {
        assertThat(ClassroomAuthFilter.exchangeCacheKey("external-token", "identity-1"))
                .isNotEqualTo(ClassroomAuthFilter.exchangeCacheKey("external-token", "identity-2"));
    }

    @Test
    void rejectsRevokedCachedExchangeWithoutIssuingAnotherToken() {
        long now = Instant.now().getEpochSecond();
        String internalToken = ClassroomTokenCodec.issue(
                Map.of("userId", "100"), SECRET, now, 900, "jti-revoked");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(ClassroomAuthFilter.exchangeCacheKey("external-token", null)))
                .thenReturn(Mono.just(internalToken));
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-revoked"))
                .thenReturn(Mono.just(false));
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                redis, WebClient.builder(), true, SECRET,
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/tcapi/meeting/list")
                        .header("access-token", "external-token")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void doesNotConvertDownstreamFailuresIntoAuthenticationFailures() {
        long now = Instant.now().getEpochSecond();
        String internalToken = ClassroomTokenCodec.issue(
                Map.of("userId", "100"), SECRET, now, 900, "jti-downstream");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(ClassroomAuthFilter.exchangeCacheKey("external-token", null)))
                .thenReturn(Mono.just(internalToken));
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-downstream"))
                .thenReturn(Mono.just(true));
        ClassroomAuthFilter filter = new ClassroomAuthFilter(
                redis, WebClient.builder(), true, SECRET,
                "http://127.0.0.1:1/auth/external/digital-campus/classroom-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/tcapi/meeting/list")
                        .header("access-token", "external-token")
                        .build());

        assertThatThrownBy(() -> filter.filter(exchange,
                        ignored -> Mono.error(new IllegalStateException("downstream failed"))).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failed");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
