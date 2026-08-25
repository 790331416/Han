package com.han.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 网关开放平台白名单契约：只绕过 Han 用户 Token，业务鉴权仍由开放平台完成。 */
class AuthFilterOpenPlatformContractTest {

    @Test
    void allowsOpenOAuthDirectoryAndSsoPrefixesToReachBusinessServices() {
        AuthFilter filter = new AuthFilter(mock(ReactiveStringRedisTemplate.class));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        String[] publicPaths = {
                "/open/oauth2/token",
                "/open/oauth2/introspect",
                "/open/oauth2/revoke",
                "/open/oauth2/userinfo",
                "/open/oauth2/.well-known/openid-configuration",
                "/open/public/integration/package.zip",
                "/open/api/v1/directory/teachers",
                "/open/sso/login",
                "/open/sso/logout",
                "/open/sso/check",
                "/open/sso/validate"
        };
        for (String path : publicPaths) {
            filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get(path).build()), chain).block();
        }

        verify(chain, times(publicPaths.length)).filter(any());
    }

    @Test
    void keepsManagementEndpointsBehindHanUserToken() {
        AuthFilter filter = new AuthFilter(mock(ReactiveStringRedisTemplate.class));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/open/app/list").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicIntegrationWhitelistIsExactAndReadOnly() {
        AuthFilter filter = new AuthFilter(mock(ReactiveStringRedisTemplate.class));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange post = MockServerWebExchange.from(
                MockServerHttpRequest.post("/open/public/integration/package.zip").build());
        MockServerWebExchange other = MockServerWebExchange.from(
                MockServerHttpRequest.get("/open/public/integration/private.json").build());

        filter.filter(post, chain).block();
        filter.filter(other, chain).block();

        assertThat(post.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(other.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
