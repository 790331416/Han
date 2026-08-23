package com.han.open.controller;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SsoController 行为测试：覆盖 ssoLogin / ssoLogout / checkLogin 三个 handler 的
 * 正例与负例（validateTicket 已在 {@link SsoControllerTest} 中覆盖，此处不重复）。
 */
class SsoControllerBehaviorTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final IOpenAppService openAppService = mock(IOpenAppService.class);
    private final SsoController controller = new SsoController(redisTemplate, openAppService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ssoLoginIssuesTicketForAuthenticatedUserWithValidRedirectUri() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).build());
        when(openAppService.validateRedirectUri("app", "https://client.test/callback")).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        R<String> response = controller.ssoLogin("app", "https://client.test/callback", "state-xyz");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).startsWith("ST-");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(keyCaptor.capture(), payloadCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("han:sso:ticket:ST-");
        assertThat(payloadCaptor.getValue())
                .containsEntry("userId", "42")
                .containsEntry("clientId", "app")
                .containsEntry("redirectUri", "https://client.test/callback")
                .containsEntry("state", "state-xyz");
        verify(redisTemplate).expire(eq(keyCaptor.getValue()), any(Duration.class));
    }

    @Test
    void ssoLoginRejectsWhenNotAuthenticated() {
        SecurityContextHolder.clear();

        R<String> response = controller.ssoLogin("app", "https://client.test/callback", null);

        assertThat(response.getCode()).isEqualTo(Constants.UNAUTHORIZED);
        assertThat(response.getMsg()).isEqualTo("请先登录");
        verifyNoInteractions(redisTemplate, openAppService);
    }

    @Test
    void ssoLoginRejectsWhenRedirectUriInvalid() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).build());
        when(openAppService.validateRedirectUri("app", "https://evil.test/callback")).thenReturn(false);

        R<String> response = controller.ssoLogin("app", "https://evil.test/callback", null);

        assertThat(response.getCode()).isEqualTo(Constants.FAIL);
        assertThat(response.getMsg()).isEqualTo("redirect_uri 不合法");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void ssoLogoutClearsLoginTokenWhenBearerTokenPresent() {
        LoginUser loginUser = LoginUser.builder().userId(42L).clientType(ClientType.PC).build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("han:token:the-token")).thenReturn(XuJsonUtil.toJsonString(loginUser));

        R<Void> response = controller.ssoLogout("https://client.test/logout", "Bearer the-token");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        verify(redisTemplate).delete("han:login_user:42:pc");
        verify(redisTemplate).delete("han:token:the-token");
    }

    @Test
    void ssoLogoutSkipsTokenCleanupForNonBearerAuthorization() {
        R<Void> response = controller.ssoLogout("https://client.test/logout", "the-token");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void checkLoginReturnsTrueWhenAuthenticated() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(7L).build());

        R<Boolean> response = controller.checkLogin();

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isTrue();
    }

    @Test
    void checkLoginReturnsFalseWhenNotAuthenticated() {
        SecurityContextHolder.clear();

        R<Boolean> response = controller.checkLogin();

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isFalse();
    }
}
