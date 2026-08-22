package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanJsonUtil;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.service.OpenAppAuthorizationService;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
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

    @Test
    @SuppressWarnings("unchecked")
    void issuesEnvironmentCredentialAndChecksItsVendorGrant() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setAppKey("legacy-key");
        app.setVendorId(77L);
        app.setLifecycleStatus(2);
        when(authorization.validateCredentialContext("sandbox-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "sandbox-client", "SANDBOX"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "SANDBOX", "edu.teacher.read"))
                .thenReturn("");

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("sandbox-client");
        OAuth2TokenVO token = service.token(request);

        OpenAccessTokenContext context = service.requireAccessToken(token.getAccessToken(), "edu.teacher.read");
        assertThat(context.appId()).isEqualTo(55L);
        assertThat(context.environment()).isEqualTo("SANDBOX");
        verify(authorization).resolveAuthorizedDataScope(1L, 55L, "SANDBOX", "edu.teacher.read");
    }

    @Test
    void rejectsVendorBearerWithoutAnActiveGrant() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setVendorId(77L);
        app.setLifecycleStatus(5);
        when(authorization.validateCredentialContext("prod-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "prod-client", "PROD"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read"))
                .thenReturn(null);

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("prod-client");
        OAuth2TokenVO token = service.token(request);

        assertThatThrownBy(() -> service.requireAccessToken(token.getAccessToken(), "edu.teacher.read"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用未获授权资源或授权已失效");
    }

    @Test
    void grantDataScopeOverridesTokenSchoolScope() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setVendorId(77L);
        app.setLifecycleStatus(5);
        when(authorization.validateCredentialContext("prod-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "prod-client", "PROD"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read"))
                .thenReturn("{\"schoolIds\":[9,10]}");

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("prod-client");
        OAuth2TokenVO token = service.token(request);

        assertThat(service.requireAccessToken(token.getAccessToken(), "edu.teacher.read").schoolIds())
                .containsExactly(9L, 10L);
    }

    @Test
    void rejectsMalformedGrantDataScope() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setVendorId(77L);
        app.setLifecycleStatus(5);
        when(authorization.validateCredentialContext("prod-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "prod-client", "PROD"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read"))
                .thenReturn("{\"schoolIds\":\"not-a-list\"}");

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("prod-client");
        OAuth2TokenVO token = service.token(request);

        assertThatThrownBy(() -> service.requireAccessToken(token.getAccessToken(), "edu.teacher.read"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("授权学校范围格式非法");
    }

    @Test
    void acceptsLegacyRedisTokenWithoutAppIdOrEnvironment() {
        IOpenAppService apps = mock(IOpenAppService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(44L);
        when(apps.getAppByAppKey("video-platform")).thenReturn(app);
        Map<String, Object> oldRecord = new HashMap<>();
        oldRecord.put("userId", 0L);
        oldRecord.put("clientId", "video-platform");
        oldRecord.put("scope", "edu.teacher.read");
        oldRecord.put("refreshToken", "refresh-token");
        oldRecord.put("tenantId", 1L);
        oldRecord.put("schoolIds", List.of(7L));
        oldRecord.put("applicationVersion", app.getUpdateTime().toString());
        oldRecord.put("expiresAt", Instant.now().plusSeconds(300));
        setRedisValue(redis, "han:open:oauth2:access:legacy-token", HanJsonUtil.toJsonString(oldRecord));

        OpenAccessTokenContext context = new OAuth2ServiceImpl(apps, redis)
                .requireAccessToken("legacy-token", "edu.teacher.read");

        assertThat(context.appId()).isEqualTo(44L);
        assertThat(context.environment()).isEqualTo("PROD");
    }

    @Test
    void rejectsDisabledVendorApplicationDuringBearerValidation() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setVendorId(77L);
        app.setLifecycleStatus(5);
        when(authorization.validateCredentialContext("prod-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "prod-client", "PROD"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read"))
                .thenReturn("");
        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("prod-client");
        OAuth2TokenVO token = service.token(request);

        app.setStatus(1);
        assertThatThrownBy(() -> service.requireAccessToken(token.getAccessToken(), "edu.teacher.read"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户端不存在或已停用");
    }

    @Test
    void sameScopeCannotCrossToAnotherResourceWithResourceSpecificValidation() {
        IOpenAppService apps = mock(IOpenAppService.class);
        OpenAppAuthorizationService authorization = mock(OpenAppAuthorizationService.class);
        StringRedisTemplate redis = recordingRedis();
        OpenAppVO app = app(List.of("edu.teacher.read"));
        app.setAppId(55L);
        app.setVendorId(77L);
        app.setLifecycleStatus(5);
        when(authorization.validateCredentialContext("prod-client", "secret"))
                .thenReturn(new OpenAppAuthorizationService.CredentialContext(55L, "prod-client", "PROD"));
        when(apps.selectVoById(55L)).thenReturn(app);
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read"))
                .thenReturn("");
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read", "directory.teachers.read"))
                .thenReturn("");
        when(authorization.resolveAuthorizedDataScope(1L, 55L, "PROD", "edu.teacher.read", "directory.students.read"))
                .thenReturn(null);

        OAuth2ServiceImpl service = new OAuth2ServiceImpl(apps, redis, authorization);
        OAuth2TokenDTO request = tokenRequest("edu.teacher.read");
        request.setClientId("prod-client");
        OAuth2TokenVO token = service.token(request);

        assertThat(service.requireAccessToken(token.getAccessToken(), "edu.teacher.read", "directory.teachers.read"))
                .isNotNull();
        assertThatThrownBy(() -> service.requireAccessToken(
                token.getAccessToken(), "edu.teacher.read", "directory.students.read"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用未获授权资源或授权已失效");
        verify(authorization).resolveAuthorizedDataScope(
                1L, 55L, "PROD", "edu.teacher.read", "directory.students.read");
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

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate recordingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> records = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            records.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> records.get(invocation.getArgument(0)));
        return redis;
    }

    @SuppressWarnings("unchecked")
    private static void setRedisValue(StringRedisTemplate redis, String key, String value) {
        ValueOperations<String, String> values = redis.opsForValue();
        doAnswer(invocation -> null).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(key)).thenReturn(value);
    }
}
