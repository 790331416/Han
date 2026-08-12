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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 分享限流过滤器测试（G1-9）：shareKey 维度 QPS 与日调用量双计数，
 * 计数复用与 RateLimitFilter 相同的原子 INCR+EXPIRE Lua 脚本。
 */
class ShareRateLimitFilterTest {

    private static final String SHARE_KEY = "abcDEF1234567890abcDEF1234567890";

    private ReactiveStringRedisTemplate redisTemplate;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        chain = mock(GatewayFilterChain.class);
    }

    private ShareRateLimitFilter filter(boolean enabled, int qps, int daily) {
        return new ShareRateLimitFilter(redisTemplate, enabled, qps, daily);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ignoresNonSharePathsWithoutTouchingRedis() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ai/chat/conversations"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsProfileRequestUnderQpsAndSkipsDailyQuota() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ai/share/" + SHARE_KEY + "/profile"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        // profile 只计 QPS，不消耗日调用额度
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        assertThat(keysCaptor.getValue()).containsExactly("ai:share:rate_limit:qps:" + SHARE_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsChatRequestAgainstBothQpsAndDailyQuota() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L), Flux.just(5L));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(keysCaptor.getAllValues().get(0))
                .containsExactly("ai:share:rate_limit:qps:" + SHARE_KEY);
        assertThat(keysCaptor.getAllValues().get(1))
                .containsExactly("ai:share:rate_limit:daily:" + SHARE_KEY + ":" + today);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsWhenQpsExceededWith429AndClearMessage() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(11L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":429")
                .contains("请求过于频繁");
        verify(chain, never()).filter(any());
        // QPS 已拒绝，不再消耗日调用计数
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsWhenDailyQuotaExceededWith429AndClearMessage() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L), Flux.just(1001L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":429")
                .contains("今日调用次数已达上限");
        verify(chain, never()).filter(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsThroughWhenRedisScriptReturnsEmpty() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        filter(true, 10, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void skipsEverythingWhenDisabled() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        filter(false, 10, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonPositiveLimitDisablesThatDimensionOnly() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(999L));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/share/" + SHARE_KEY + "/chat"));

        // qps<=0 表示不启用 QPS 限制，仅日调用量计数生效
        filter(true, 0, 1000).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        assertThat(keysCaptor.getValue().get(0)).startsWith("ai:share:rate_limit:daily:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordersBetweenGlobalRateLimitAndAuth() {
        ShareRateLimitFilter shareFilter = filter(true, 10, 1000);
        assertThat(shareFilter.getOrder()).isGreaterThan(-300).isLessThan(-100);
    }
}
