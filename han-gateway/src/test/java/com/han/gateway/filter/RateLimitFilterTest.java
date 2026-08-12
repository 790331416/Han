package com.han.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流过滤器测试：核心保障是 INCR 与 EXPIRE 必须在同一 Lua 脚本内原子完成，
 * 并对历史遗留的无 TTL key 兜底补设过期，杜绝「expire 丢失后 IP 永久封禁」。
 */
class RateLimitFilterTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private RateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        filter = new RateLimitFilter(redisTemplate);
    }

    /**
     * 脚本契约：INCR 与 EXPIRE 在同一脚本内；TTL 判断使用 < 0，
     * 保证即使某次 EXPIRE 丢失（网关重启 / Redis 抖动），下一次请求也会立即补设过期，
     * key 不可能长期停留在 TTL=-1 的永生状态。
     */
    @Test
    void scriptAtomicallySetsExpireAndRepairsMissingTtl() {
        String script = RateLimitFilter.RATE_LIMIT_SCRIPT.getScriptAsString();
        assertThat(script).contains("INCR");
        assertThat(script).contains("EXPIRE");
        assertThat(script)
                .as("必须兜底修复无 TTL 的存量 key，而不是只在 count==1 时设置过期")
                .contains("TTL")
                .contains("< 0");
        int incrIndex = script.indexOf("INCR");
        int expireIndex = script.indexOf("EXPIRE");
        assertThat(incrIndex).isLessThan(expireIndex);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsRequestUnderLimitViaAtomicScript() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/system/runtime/capabilities")
                        .header("X-Forwarded-For", "10.18.35.127"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        assertThat(keysCaptor.getValue()).containsExactly("gateway:rate_limit:10.18.35.127");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsRequestOverLimitWith429() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(51L));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/captcha")
                        .header("X-Forwarded-For", "10.18.35.127"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsThroughWhenRedisScriptReturnsEmpty() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/login").header("X-Forwarded-For", "10.18.35.127"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
