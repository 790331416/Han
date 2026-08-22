package com.han.open.service.impl;

import com.han.common.core.util.HanJsonUtil;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OAuth2 令牌过期、自省和撤销门禁测试。 */
class OAuth2TokenLifecycleTest {

    @Test
    void introspectMarksExpiredAccessTokenInactiveAndDeletesIt() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(record("expired", Instant.now().minusSeconds(30)));
        when(apps.validateClient("legacy-client", "secret")).thenReturn(true);
        when(apps.getAppByAppKey("legacy-client")).thenReturn(app());

        Object result = new OAuth2ServiceImpl(apps, redis).introspectToken(
                "expired", "legacy-client", "secret");

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("active")).isEqualTo(false);
        verify(redis).delete("han:open:oauth2:access:expired");
    }

    @Test
    void revokeDeletesAccessAndRefreshTokenTogether() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("han:open:oauth2:access:active"))
                .thenReturn(record("refresh-for-active", Instant.now().plusSeconds(300)));
        when(apps.validateClient("legacy-client", "secret")).thenReturn(true);
        when(apps.getAppByAppKey("legacy-client")).thenReturn(app());

        new OAuth2ServiceImpl(apps, redis).revokeToken(
                "active", "access_token", "legacy-client", "secret");

        verify(redis).delete(List.of(
                "han:open:oauth2:access:active",
                "han:open:oauth2:refresh:refresh-for-active"));
    }

    private static OpenAppVO app() {
        OpenAppVO app = new OpenAppVO();
        app.setAppId(77L);
        app.setAppKey("legacy-client");
        app.setStatus(0);
        app.setGrantTypes(List.of("client_credentials"));
        app.setScopes(List.of("edu.teacher.read"));
        app.setTenantId(1L);
        app.setUpdateTime(LocalDateTime.of(2026, 8, 23, 1, 0));
        return app;
    }

    private static String record(String refreshToken, Instant expiresAt) {
        Map<String, Object> value = new HashMap<>();
        value.put("userId", 0L);
        value.put("clientId", "legacy-client");
        value.put("scope", "edu.teacher.read");
        value.put("refreshToken", refreshToken);
        value.put("tenantId", 1L);
        value.put("schoolIds", List.of(7L));
        value.put("applicationVersion", "2026-08-23T01:00");
        value.put("expiresAt", expiresAt);
        return HanJsonUtil.toJsonString(value);
    }
}
