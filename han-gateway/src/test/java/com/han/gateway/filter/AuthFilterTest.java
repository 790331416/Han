package com.han.gateway.filter;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关认证过滤器测试。
 *
 * <p>覆盖：白名单边界匹配、伪造身份头剥离、Token 提取契约、会话异常与依赖故障的处理。
 */
class AuthFilterTest {

    private static final String TOKEN = "9c1d7e4b2a6f8305";

    private ReactiveValueOperations<String, String> valueOperations;
    private GatewayFilterChain chain;
    private AuthFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter = new AuthFilter(redisTemplate);
    }

    // ==================== 白名单边界匹配 ====================

    @Test
    @DisplayName("白名单精确条目免认证放行")
    void shouldPassWhitelistedExactPath() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/auth/login"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("白名单前缀条目（以 / 结尾）按前缀放行")
    void shouldPassWhitelistedPrefixPath() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/auth/social/providers"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("精确条目不再前缀放行同名开头的新接口")
    void shouldNotLeakPathsSharingWhitelistPrefix() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/auth/loginByCode"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("登录页所需的 /tenant/public/ 免认证放行")
    void shouldPassTenantPublicPaths() {
        for (String path : new String[]{"/tenant/public/options", "/tenant/public/domain/demo.example.com"}) {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path));

            filter.filter(exchange, chain).block();
        }

        verify(chain, times(2)).filter(any());
    }

    @Test
    @DisplayName("租户管理端路径已移出白名单，需认证")
    void shouldRequireAuthForTenantAdminPaths() {
        // 契约已迁到 /inner/tenant，这三条 A 层路径不再需要免认证；
        // 放回白名单会让未认证方绕过 @RequiresPermission 直达管理端接口。
        for (String path : new String[]{"/tenant/all", "/tenant/listAllValid", "/tenant/domain/demo.example.com"}) {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as("%s 必须要求认证", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("精确条目仍放行其下级路径")
    void shouldStillPassSubPathOfExactEntry() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/oauth2/authorize/confirm"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("API 文档路径已从白名单移除，需认证")
    void shouldRequireAuthForApiDocPaths() {
        for (String path : new String[]{"/doc.html", "/v3/api-docs", "/swagger-resources", "/auth/register"}) {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as("路径 %s 应要求认证", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== 伪造身份头剥离 ====================

    @Test
    @DisplayName("白名单请求同样剥离客户端自带的身份头与全部 X-Inner-* 头")
    void shouldStripSpoofedHeadersOnWhitelistedPath() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/auth/login")
                .header(Constants.USER_ID_HEADER, "1")
                .header(Constants.USERNAME_HEADER, "admin")
                .header(Constants.TENANT_ID_HEADER, "999")
                .header(Constants.CLIENT_TYPE_HEADER, "pc")
                .header(Constants.DEVICE_ID_HEADER, "device-1")
                .header(Constants.INNER_AUTH_CLIENT_HEADER, "han-auth")
                .header(Constants.INNER_AUTH_TIMESTAMP_HEADER, "1")
                .header(Constants.INNER_AUTH_SIGNATURE_HEADER, "deadbeef")
                .header("X-Inner-Sign-Version", "v2"));

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest();
        assertThat(forwarded.getHeaders().headerNames())
                .doesNotContain(Constants.USER_ID_HEADER, Constants.USERNAME_HEADER, Constants.TENANT_ID_HEADER,
                        Constants.CLIENT_TYPE_HEADER, Constants.DEVICE_ID_HEADER,
                        Constants.INNER_AUTH_CLIENT_HEADER, Constants.INNER_AUTH_TIMESTAMP_HEADER,
                        Constants.INNER_AUTH_SIGNATURE_HEADER, "X-Inner-Sign-Version");
    }

    @Test
    @DisplayName("认证通过后身份头取自会话，覆盖客户端自带值")
    void shouldInjectIdentityFromSession() {
        givenSession("{\"userId\":2,\"username\":\"alice\",\"tenantId\":5}");

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN)
                .header(Constants.USER_ID_HEADER, "1")
                .header(Constants.TENANT_ID_HEADER, "999"));

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest();
        assertThat(forwarded.getHeaders().getFirst(Constants.USER_ID_HEADER)).isEqualTo("2");
        assertThat(forwarded.getHeaders().getFirst(Constants.USERNAME_HEADER)).isEqualTo("alice");
        assertThat(forwarded.getHeaders().getFirst(Constants.TENANT_ID_HEADER)).isEqualTo("5");
    }

    /**
     * {@code chain.filter} 返回的 {@code Mono<Void>} 同样以空完成结束，
     * 用 {@code switchIfEmpty} 兜「会话不存在」会在请求已转发之后再写一次 401。
     */
    @Test
    @DisplayName("认证通过放行时不得再写入 401")
    void shouldNotWriteUnauthorizedAfterSuccessfulPassThrough() {
        givenSession("{\"userId\":2,\"username\":\"alice\",\"tenantId\":5}");

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ==================== Token 提取与异常处理 ====================

    @Test
    @DisplayName("非 Bearer 的 Authorization 头不再被当作 token")
    void shouldRejectNonBearerAuthorization() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, "Basic YWRtaW46YWRtaW4="));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("Token 无会话时返回 401")
    void shouldRejectUnknownToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("会话数据损坏时返回 401 而不是带着损坏数据继续放行")
    void shouldRejectCorruptedSession() {
        givenSession("not-a-json");

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Redis 故障时返回 503 而不是裸 500，且不放行")
    void shouldReturnServiceUnavailableWhenRedisFails() {
        when(valueOperations.get(anyString()))
                .thenReturn(Mono.error(new RedisConnectionFailureException("redis down")));

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("下游业务异常不会被认证的降级分支吞成 503")
    void shouldNotSwallowDownstreamErrors() {
        givenSession("{\"userId\":2,\"username\":\"alice\",\"tenantId\":5}");
        when(chain.filter(any())).thenReturn(Mono.error(new IllegalStateException("downstream boom")));

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/list")
                .header(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN));

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        try {
            filter.filter(exchange, chain).block();
            assertThat(false).as("下游异常应原样抛出").isTrue();
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("downstream boom");
        }
    }

    // ==================== helpers ====================

    private void givenSession(String json) {
        when(valueOperations.get(CacheConstants.TOKEN_KEY + TOKEN)).thenReturn(Mono.just(json));
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private ServerHttpRequest capturedRequest() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest();
    }
}
