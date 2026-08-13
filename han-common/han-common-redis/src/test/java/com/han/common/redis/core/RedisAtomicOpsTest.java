package com.han.common.redis.core;

import com.han.common.redis.script.HanRedisScripts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RedisAtomicOps} 单元测试，覆盖工单 S-75 要求的原子能力。
 * <p>用 mock 校验脚本选择与参数编排；脚本在真实 Redis 上的原子语义需要连服务才能验证。
 */
class RedisAtomicOpsTest {

    private StringRedisTemplate redisTemplate;
    private RedisAtomicOps ops;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ops = new RedisAtomicOps(redisTemplate);
    }

    @Test
    @DisplayName("incrementWithTtl 走 INCR+TTL 脚本，一次往返完成计数与过期")
    void incrementUsesSingleScript() {
        when(redisTemplate.execute(eq(HanRedisScripts.INCREMENT_WITH_TTL), anyList(), any(Object[].class)))
                .thenReturn(3L);

        long count = ops.incrementWithTtl("han:login_fail:admin", Duration.ofMinutes(15));

        assertEquals(3L, count);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> args = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(redisTemplate)
                .execute(eq(HanRedisScripts.INCREMENT_WITH_TTL), keys.capture(), args.capture());
        assertEquals(List.of("han:login_fail:admin"), keys.getValue());
        assertEquals("900", args.getValue()[0]);
    }

    @Test
    @DisplayName("hashSetWithTtl 把 TTL 放在首位、field/value 交替排列")
    void hashSetFlattensFieldsWithTtlFirst() {
        when(redisTemplate.execute(eq(HanRedisScripts.HASH_SET_WITH_TTL), anyList(), any(Object[].class)))
                .thenReturn(2L);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("userId", "1");
        fields.put("clientId", "web");

        long written = ops.hashSetWithTtl("han:sso:ticket:ST-1", fields, Duration.ofSeconds(120));

        assertEquals(2L, written);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(redisTemplate)
                .execute(eq(HanRedisScripts.HASH_SET_WITH_TTL), anyList(), args.capture());
        assertEquals(new Object[]{"120", "userId", "1", "clientId", "web"}.length, args.getValue().length);
        assertEquals("120", args.getValue()[0]);
        assertEquals("userId", args.getValue()[1]);
        assertEquals("web", args.getValue()[4]);
    }

    @Test
    @DisplayName("hashGetAllAndDelete 把 HGETALL 的扁平数组还原为 Map")
    void hashGetAllAndDeleteRebuildsMap() {
        when(redisTemplate.execute(eq(HanRedisScripts.HASH_GET_ALL_AND_DELETE), anyList()))
                .thenReturn(List.of("userId", "1", "clientId", "web"));

        Map<String, String> result = ops.hashGetAllAndDelete("han:sso:ticket:ST-1");

        assertEquals(2, result.size());
        assertEquals("1", result.get("userId"));
        assertEquals("web", result.get("clientId"));
    }

    @Test
    @DisplayName("key 不存在时 hashGetAllAndDelete 返回空 Map 而不是 null")
    void hashGetAllAndDeleteHandlesMissingKey() {
        when(redisTemplate.execute(eq(HanRedisScripts.HASH_GET_ALL_AND_DELETE), anyList())).thenReturn(null);

        assertTrue(ops.hashGetAllAndDelete("han:sso:ticket:missing").isEmpty());
    }

    @Test
    @DisplayName("ensureTtl 只在 key 无 TTL 时返回 true")
    void ensureTtlReportsRepair() {
        when(redisTemplate.execute(eq(HanRedisScripts.ENSURE_TTL), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        assertTrue(ops.ensureTtl("han:login_fail:admin", Duration.ofMinutes(15)));
        assertFalse(ops.ensureTtl("han:login_fail:admin", Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("setIfAbsent 走原生 SET NX EX")
    void setIfAbsentDelegatesToNativeCommand() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("han:repeat:1", "v", Duration.ofSeconds(5))).thenReturn(Boolean.TRUE);

        assertTrue(ops.setIfAbsent("han:repeat:1", "v", Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("拒绝空 key 与非正数 TTL，杜绝写出没有过期时间的 key")
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> ops.incrementWithTtl("  ", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> ops.incrementWithTtl("k", null));
        assertThrows(IllegalArgumentException.class, () -> ops.incrementWithTtl("k", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ops.incrementWithTtl("k", Duration.ofSeconds(-1)));
    }
}
