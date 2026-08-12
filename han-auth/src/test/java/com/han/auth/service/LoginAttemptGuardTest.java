package com.han.auth.service;

import com.han.auth.config.LoginSecurityProperties;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数的原子性与永久锁定兜底（S-75 / 11-auth D3）。
 */
class LoginAttemptGuardTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LoginSecurityProperties properties = new LoginSecurityProperties();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(redisTemplate, properties);

    @Test
    void recordFailureIncrementsAndSetsTtlInOneRoundTrip() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(3L);

        int remaining = guard.recordFailure("admin", 1L);

        assertThat(remaining).isEqualTo(properties.getMaxAttempts() - 3);
        // INCR 与 EXPIRE 必须由脚本一次完成，不允许再出现独立的 expire 往返
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void recordFailureCountsScriptKeyByTenantAndUsername() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("han:login_fail:7:bob")), any(Object[].class)))
                .thenReturn(1L);

        assertThat(guard.recordFailure("bob", 7L)).isEqualTo(properties.getMaxAttempts() - 1);
    }

    @Test
    void assertNotLockedPassesBelowThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("4");

        assertThatCode(() -> guard.assertNotLocked("admin", 1L)).doesNotThrowAnyException();
    }

    @Test
    void assertNotLockedReportsRemainingMinutesFromTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");
        when(redisTemplate.getExpire(anyString())).thenReturn(120L);

        assertThatThrownBy(() -> guard.assertNotLocked("admin", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请2分钟后再试");
    }

    /**
     * 核心回归：INCR 成功但 EXPIRE 丢失留下的永生 key，不能把账号永久锁死。
     */
    @Test
    void assertNotLockedRepairsCounterWithoutTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("9");
        when(redisTemplate.getExpire(anyString())).thenReturn(-1L);

        assertThatThrownBy(() -> guard.assertNotLocked("admin", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请10分钟后再试");

        verify(redisTemplate).expire("han:login_fail:1:admin", properties.getLockoutDuration());
    }

    @Test
    void assertNotLockedResetsCorruptedCounter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("not-a-number");

        assertThatCode(() -> guard.assertNotLocked("admin", 1L)).doesNotThrowAnyException();
        verify(redisTemplate).delete("han:login_fail:1:admin");
    }
}
