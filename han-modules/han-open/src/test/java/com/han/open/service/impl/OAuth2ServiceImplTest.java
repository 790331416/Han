package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OAuth2ServiceImplTest {

    @Test
    void rejectsScopeOutsideTheApplicationGrantBeforeIssuingAToken() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OpenAppVO app = app(List.of("edu.teacher.read"));
        when(apps.getAppByAppKey("video-platform")).thenReturn(app);
        when(apps.validateClient("video-platform", "secret")).thenReturn(true);

        OAuth2TokenDTO dto = tokenRequest("edu.student.read");

        assertThatThrownBy(() -> new OAuth2ServiceImpl(apps, redis).token(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求的 Scope 超出应用授权范围");
        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsClientCredentialsTokensInRedis() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        OpenAppVO app = app(List.of("edu.teacher.read", "edu.device.read"));
        when(apps.getAppByAppKey("video-platform")).thenReturn(app);
        when(apps.validateClient("video-platform", "secret")).thenReturn(true);

        var token = new OAuth2ServiceImpl(apps, redis).token(tokenRequest("edu.teacher.read"));

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
        assertThat(token.getScope()).isEqualTo("edu.teacher.read");
        verify(values, times(2)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoresTenantAndSchoolScopeFromRedisBackedAccessToken() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> records = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            records.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> records.get(invocation.getArgument(0)));
        OpenAppVO app = app(List.of("edu.teacher.read"));
        when(apps.getAppByAppKey("video-platform")).thenReturn(app);
        when(apps.validateClient("video-platform", "secret")).thenReturn(true);

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis);
        var token = service.token(tokenRequest("edu.teacher.read"));

        var context = service.requireAccessToken(token.getAccessToken(), "edu.teacher.read");

        assertThat(context.tenantId()).isEqualTo(1L);
        assertThat(context.schoolIds()).containsExactly(7L);
        assertThat(context.scopes()).containsExactly("edu.teacher.read");
    }

    private static OAuth2TokenDTO tokenRequest(String scope) {
        OAuth2TokenDTO dto = new OAuth2TokenDTO();
        dto.setGrantType("client_credentials");
        dto.setClientId("video-platform");
        dto.setClientSecret("secret");
        dto.setScope(scope);
        return dto;
    }

    private static OpenAppVO app(List<String> scopes) {
        OpenAppVO app = new OpenAppVO();
        app.setAppKey("video-platform");
        app.setStatus(0);
        app.setGrantTypes(List.of("client_credentials"));
        app.setScopes(scopes);
        app.setTenantId(1L);
        app.setSchoolIds(List.of(7L));
        app.setAccessTokenTtl(3600);
        app.setRefreshTokenTtl(7200);
        app.setUpdateTime(LocalDateTime.of(2026, 8, 18, 12, 0));
        return app;
    }
}
