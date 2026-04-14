package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 授权控制器。
 */
@RestController
@RequestMapping({"/oauth2", "/open/oauth2"})
@RequiredArgsConstructor
public class OAuth2Controller {

    private static final String LOGIN_REQUIRED_MESSAGE = "请先登录";

    private final IOAuth2Service oauth2Service;

    /**
     * 授权端点，获取授权码。
     */
    @GetMapping("/authorize")
    @PermissionExempt("OAuth2 授权端点，由登录态和客户端参数共同控制")
    public R<String> authorize(@Validated OAuth2AuthorizeDTO dto) {
        if (!SecurityContextHolder.isLogin()) {
            return R.fail(401, LOGIN_REQUIRED_MESSAGE);
        }
        Long userId = SecurityContextHolder.getUserId();
        String code = oauth2Service.authorize(dto, userId);
        return R.ok(code);
    }

    /**
     * 授权确认端点，用户确认授权后签发授权码。
     */
    @PostMapping("/authorize/confirm")
    @PermissionExempt("OAuth2 授权确认端点，由登录态和客户端参数共同控制")
    public R<String> authorizeConfirm(@Validated @RequestBody OAuth2AuthorizeDTO dto,
                                      @RequestParam(defaultValue = "true") Boolean approved) {
        if (!approved) {
            return R.fail("用户拒绝授权");
        }
        if (!SecurityContextHolder.isLogin()) {
            return R.fail(401, LOGIN_REQUIRED_MESSAGE);
        }
        Long userId = SecurityContextHolder.getUserId();
        String code = oauth2Service.authorize(dto, userId);
        return R.ok(code);
    }

    /**
     * Token 端点，支持授权码、客户端凭证与刷新令牌模式。
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @PermissionExempt("OAuth2 Token 公开端点，由客户端凭证和业务参数校验控制")
    public OAuth2TokenVO token(OAuth2TokenDTO dto) {
        return oauth2Service.token(dto);
    }

    /**
     * JSON 形式的 Token 端点。
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermissionExempt("OAuth2 Token 公开端点，由客户端凭证和业务参数校验控制")
    public OAuth2TokenVO tokenJson(@RequestBody OAuth2TokenDTO dto) {
        return oauth2Service.token(dto);
    }

    /**
     * 撤销 Token。
     */
    @PostMapping("/revoke")
    @PermissionExempt("OAuth2 Token 撤销端点，由客户端凭证和业务参数校验控制")
    public R<Void> revoke(@RequestParam String token,
                          @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint) {
        oauth2Service.revokeToken(token, tokenTypeHint);
        return R.ok();
    }

    /**
     * Token 自省端点。
     */
    @PostMapping("/introspect")
    @PermissionExempt("OAuth2 Token 自省端点，由客户端凭证和业务参数校验控制")
    public R<Object> introspect(@RequestParam String token) {
        return R.ok(oauth2Service.introspectToken(token));
    }

    /**
     * OpenID Connect 用户信息端点。
     */
    @GetMapping("/userinfo")
    @PermissionExempt("OpenID Connect 用户信息端点，由访问令牌业务校验控制")
    public OAuth2UserInfoVO userInfo(@RequestHeader("Authorization") String authorization) {
        String accessToken = authorization.replace("Bearer ", "");
        return oauth2Service.getUserInfo(accessToken);
    }

    /**
     * OpenID Connect Discovery 端点。
     */
    @GetMapping("/.well-known/openid-configuration")
    @PermissionExempt("OpenID Connect Discovery 公开端点")
    public R<Object> discovery() {
        return R.ok();
    }
}
