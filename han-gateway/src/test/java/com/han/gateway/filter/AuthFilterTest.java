package com.han.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFilterTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        filter = new AuthFilter(redisTemplate);
    }

    @Test
    void allowsOnlyPostApplicationAndGetStatusForPublicVendorPortal() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange submit = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/vendor/register").build());
        filter.filter(submit, chain).block();

        MockServerWebExchange publicKey = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/vendor/publicKey").build());
        filter.filter(publicKey, chain).block();

        MockServerWebExchange status = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/vendor/application/status").build());
        filter.filter(status, chain).block();

        verify(chain, org.mockito.Mockito.times(3)).filter(any());
    }

    @Test
    void doesNotMakeGetApplicationOrOtherPublicVendorPathAnonymous() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerWebExchange wrongMethod = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/vendor/register").build());
        filter.filter(wrongMethod, chain).block();
        assertThat(wrongMethod.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockServerWebExchange wrongPath = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/vendor/application/status").build());
        filter.filter(wrongPath, chain).block();
        assertThat(wrongPath.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockServerWebExchange adjacentPath = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/vendor/application/status/extra").build());
        filter.filter(adjacentPath, chain).block();
        assertThat(adjacentPath.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
