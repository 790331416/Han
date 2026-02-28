package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.context.SecurityContextHolder;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2授权控制器
 * 实现OAuth2.0授权码模式、客户端凭证模式
 */
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    /**
     * 授权端点 - 获取授权码
     * GET /oauth2/authorize?response_type=code&client_id=xxx&redirect_uri=xxx&scope=xxx&state=xxx
     */
    @GetMapping("/authorize")
    public R<String> authorize(@Validated OAuth2AuthorizeDTO dto) {
        Long userId = SecurityContextHolder.getUserId();
        String code = oauth2Service.authorize(dto, userId);
        // 返回授权码,前端拼接redirect_uri跳转
        return R.ok(code);
    }

    /**
     * 授权确认端点 - 用户确认授权
     */
    @PostMapping("/authorize/confirm")
    public R<String> authorizeConfirm(@Validated @RequestBody OAuth2AuthorizeDTO dto,
                                       @RequestParam(defaultValue = "true") Boolean approved) {
        if (!approved) {
            return R.fail("用户拒绝授权");
        }
        Long userId = SecurityContextHolder.getUserId();
        String code = oauth2Service.authorize(dto, userId);
        return R.ok(code);
    }

    /**
     * Token端点 - 获取/刷新Token
     * POST /oauth2/token
     * Content-Type: application/x-www-form-urlencoded
     * 
     * 授权码模式: grant_type=authorization_code&code=xxx&redirect_uri=xxx&client_id=xxx&client_secret=xxx
     * 客户端凭证模式: grant_type=client_credentials&client_id=xxx&client_secret=xxx&scope=xxx
     * 刷新Token: grant_type=refresh_token&refresh_token=xxx&client_id=xxx&client_secret=xxx
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuth2TokenVO token(OAuth2TokenDTO dto) {
        return oauth2Service.token(dto);
    }

    /**
     * Token端点(JSON格式)
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OAuth2TokenVO tokenJson(@RequestBody OAuth2TokenDTO dto) {
        return oauth2Service.token(dto);
    }

    /**
     * 撤销Token端点
     * POST /oauth2/revoke
     */
    @PostMapping("/revoke")
    public R<Void> revoke(@RequestParam String token,
                          @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint) {
        oauth2Service.revokeToken(token, tokenTypeHint);
        return R.ok();
    }

    /**
     * Token自省端点
     * POST /oauth2/introspect
     */
    @PostMapping("/introspect")
    public R<Object> introspect(@RequestParam String token) {
        return R.ok(oauth2Service.introspectToken(token));
    }

    /**
     * 用户信息端点 (OpenID Connect)
     * GET /oauth2/userinfo
     * Authorization: Bearer xxx
     */
    @GetMapping("/userinfo")
    public OAuth2UserInfoVO userInfo(@RequestHeader("Authorization") String authorization) {
        String accessToken = authorization.replace("Bearer ", "");
        return oauth2Service.getUserInfo(accessToken);
    }

    /**
     * OpenID Connect Discovery端点
     * GET /.well-known/openid-configuration
     */
    @GetMapping("/.well-known/openid-configuration")
    public R<Object> discovery() {
        // TODO: 返回OpenID Connect配置
        return R.ok();
    }
}
