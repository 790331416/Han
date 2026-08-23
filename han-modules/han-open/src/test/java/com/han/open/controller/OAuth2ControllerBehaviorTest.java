package com.han.open.controller;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOAuth2Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OAuth2Controller 行为测试：覆盖 authorize / authorizeConfirm / userInfo 三个 handler
 * 的登录态分支、参数透传与失败负例。不启用 Spring 容器，直接 new controller(mock(service))。
 */
class OAuth2ControllerBehaviorTest {

    private final IOAuth2Service oauth2Service = mock(IOAuth2Service.class);
    private final OAuth2Controller controller = new OAuth2Controller(oauth2Service);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private void login() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
    }

    // ---------- authorize ----------

    @Test
    void authorizeRejectsAnonymousWith401LoginRequired() {
        R<String> response = controller.authorize(
                "code", "client-1", "https://client.test/callback", null, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(Constants.UNAUTHORIZED);
        assertThat(response.getMsg()).isEqualTo("请先登录");
        assertThat(response.getData()).isNull();
        verifyNoInteractions(oauth2Service);
    }

    @Test
    void authorizeBuildsDtoAndReturnsCodeWhenLoggedIn() {
        login();
        when(oauth2Service.authorize(any(OAuth2AuthorizeDTO.class), eq(42L))).thenReturn("auth-code-123");

        R<String> response = controller.authorize(
                "code", "client-1", "https://client.test/callback",
                "openid profile", "state-xyz", "challenge-abc", "S256", "nonce-1");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getMsg()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("auth-code-123");

        ArgumentCaptor<OAuth2AuthorizeDTO> captor = ArgumentCaptor.forClass(OAuth2AuthorizeDTO.class);
        verify(oauth2Service).authorize(captor.capture(), eq(42L));
        OAuth2AuthorizeDTO dto = captor.getValue();
        assertThat(dto.getResponseType()).isEqualTo("code");
        assertThat(dto.getClientId()).isEqualTo("client-1");
        assertThat(dto.getRedirectUri()).isEqualTo("https://client.test/callback");
        assertThat(dto.getScope()).isEqualTo("openid profile");
        assertThat(dto.getState()).isEqualTo("state-xyz");
        assertThat(dto.getCodeChallenge()).isEqualTo("challenge-abc");
        assertThat(dto.getCodeChallengeMethod()).isEqualTo("S256");
        assertThat(dto.getNonce()).isEqualTo("nonce-1");
    }

    @Test
    void authorizeLeavesOptionalParamsNullWhenAbsent() {
        login();
        when(oauth2Service.authorize(any(OAuth2AuthorizeDTO.class), eq(42L))).thenReturn("min-code");

        R<String> response = controller.authorize(
                "code", "client-1", "https://client.test/callback", null, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isEqualTo("min-code");

        ArgumentCaptor<OAuth2AuthorizeDTO> captor = ArgumentCaptor.forClass(OAuth2AuthorizeDTO.class);
        verify(oauth2Service).authorize(captor.capture(), eq(42L));
        OAuth2AuthorizeDTO dto = captor.getValue();
        assertThat(dto.getResponseType()).isEqualTo("code");
        assertThat(dto.getClientId()).isEqualTo("client-1");
        assertThat(dto.getRedirectUri()).isEqualTo("https://client.test/callback");
        assertThat(dto.getScope()).isNull();
        assertThat(dto.getState()).isNull();
        assertThat(dto.getCodeChallenge()).isNull();
        assertThat(dto.getCodeChallengeMethod()).isNull();
        assertThat(dto.getNonce()).isNull();
    }

    // ---------- authorizeConfirm ----------

    @Test
    void authorizeConfirmRejectsWhenUserDeclines() {
        OAuth2AuthorizeDTO dto = new OAuth2AuthorizeDTO();

        R<String> response = controller.authorizeConfirm(dto, false);

        assertThat(response.getCode()).isEqualTo(Constants.FAIL);
        assertThat(response.getMsg()).isEqualTo("用户拒绝授权");
        assertThat(response.getData()).isNull();
        verifyNoInteractions(oauth2Service);
    }

    @Test
    void authorizeConfirmRejectsAnonymousWith401LoginRequired() {
        OAuth2AuthorizeDTO dto = new OAuth2AuthorizeDTO();

        R<String> response = controller.authorizeConfirm(dto, true);

        assertThat(response.getCode()).isEqualTo(Constants.UNAUTHORIZED);
        assertThat(response.getMsg()).isEqualTo("请先登录");
        assertThat(response.getData()).isNull();
        verifyNoInteractions(oauth2Service);
    }

    @Test
    void authorizeConfirmDelegatesDtoAndUserIdWhenApprovedAndLoggedIn() {
        login();
        OAuth2AuthorizeDTO dto = new OAuth2AuthorizeDTO();
        dto.setClientId("client-1");
        when(oauth2Service.authorize(dto, 42L)).thenReturn("confirm-code");

        R<String> response = controller.authorizeConfirm(dto, true);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getMsg()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("confirm-code");
        verify(oauth2Service).authorize(dto, 42L);
    }

    // ---------- userInfo ----------

    @Test
    void userInfoStripsBearerPrefixAndPassesAccessTokenToService() {
        OAuth2UserInfoVO userInfo = OAuth2UserInfoVO.builder().sub("42").name("alice").build();
        when(oauth2Service.getUserInfo("access-token-1")).thenReturn(userInfo);

        OAuth2UserInfoVO response = controller.userInfo("Bearer access-token-1");

        assertThat(response).isSameAs(userInfo);
        verify(oauth2Service).getUserInfo("access-token-1");
    }

    @Test
    void userInfoPropagatesAccessTokenRejectionForInvalidToken() {
        when(oauth2Service.getUserInfo("bad-token")).thenThrow(new BusinessException("AccessToken 无效或已过期"));

        assertThatThrownBy(() -> controller.userInfo("Bearer bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AccessToken 无效或已过期");
        verify(oauth2Service).getUserInfo("bad-token");
    }

    @Test
    void userInfoForwardsEmptyTokenWhenBearerPrefixHasNoRemainder() {
        OAuth2UserInfoVO userInfo = OAuth2UserInfoVO.builder().sub("42").build();
        when(oauth2Service.getUserInfo("")).thenReturn(userInfo);

        OAuth2UserInfoVO response = controller.userInfo("Bearer ");

        assertThat(response).isSameAs(userInfo);
        verify(oauth2Service).getUserInfo("");
    }
}
