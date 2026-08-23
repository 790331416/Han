package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOAuth2Service;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.LinkedHashMap;
import java.util.Map;

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
    public R<String> authorize(@RequestParam("response_type") String responseType,
                               @RequestParam("client_id") String clientId,
                               @RequestParam("redirect_uri") String redirectUri,
                               @RequestParam(value = "scope", required = false) String scope,
                               @RequestParam(value = "state", required = false) String state,
                               @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                               @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                               @RequestParam(value = "nonce", required = false) String nonce) {
        if (!SecurityContextHolder.isLogin()) {
            return R.fail(401, LOGIN_REQUIRED_MESSAGE);
        }
        Long userId = SecurityContextHolder.getUserId();
        String code = oauth2Service.authorize(buildAuthorizeDto(
                responseType,
                clientId,
                redirectUri,
                scope,
                state,
                codeChallenge,
                codeChallengeMethod,
                nonce
        ), userId);
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
        return R.ok(oauth2Service.authorize(dto, userId));
    }

    /**
     * Form 形式的 Token 端点。
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @PermissionExempt("OAuth2 Token 公开端点，由客户端凭证和业务参数校验控制")
    public OAuth2TokenVO token(@RequestParam("grant_type") String grantType,
                               @RequestParam(value = "client_id", required = false) String clientId,
                               @RequestParam(value = "client_secret", required = false) String clientSecret,
                               @RequestParam(value = "code", required = false) String code,
                               @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                               @RequestParam(value = "refresh_token", required = false) String refreshToken,
                               @RequestParam(value = "scope", required = false) String scope,
                               @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                               @RequestParam(value = "username", required = false) String username,
                               @RequestParam(value = "password", required = false) String password) {
        return oauth2Service.token(buildTokenDto(
                grantType,
                clientId,
                clientSecret,
                code,
                redirectUri,
                refreshToken,
                scope,
                codeVerifier,
                username,
                password
        ));
    }

    /**
     * JSON 形式的 Token 端点。
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermissionExempt("OAuth2 Token 公开端点，由客户端凭证和业务参数校验控制")
    public OAuth2TokenVO tokenJson(@Validated @RequestBody OAuth2TokenDTO dto) {
        return oauth2Service.token(dto);
    }

    /**
     * 撤销 Token。
     */
    @PostMapping("/revoke")
    @PermissionExempt("OAuth2 Token 撤销端点，由客户端凭证和业务参数校验控制")
    public R<Void> revoke(@RequestParam String token,
                          @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint,
                          @RequestParam("client_id") String clientId,
                          @RequestParam("client_secret") String clientSecret) {
        oauth2Service.revokeToken(token, tokenTypeHint, clientId, clientSecret);
        return R.ok();
    }

    /**
     * Token 自省端点。
     */
    @PostMapping("/introspect")
    @PermissionExempt("OAuth2 Token 自省端点，由客户端凭证和业务参数校验控制")
    public R<Object> introspect(@RequestParam String token,
                                @RequestParam("client_id") String clientId,
                                @RequestParam("client_secret") String clientSecret) {
        return R.ok(oauth2Service.introspectToken(token, clientId, clientSecret));
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
    public R<Object> discovery(HttpServletRequest request) {
        String issuer = resolveIssuer(request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/authorize");
        metadata.put("token_endpoint", issuer + "/token");
        metadata.put("userinfo_endpoint", issuer + "/userinfo");
        metadata.put("revocation_endpoint", issuer + "/revoke");
        metadata.put("introspection_endpoint", issuer + "/introspect");
        metadata.put("response_types_supported", new String[]{"code"});
        metadata.put("grant_types_supported", new String[]{"authorization_code", "refresh_token", "client_credentials"});
        metadata.put("subject_types_supported", new String[]{"public"});
        metadata.put("scopes_supported", new String[]{
                "openid", "profile", "edu.teacher.read", "edu.student.read", "edu.device.read", "edu.contact.read"});
        return R.ok(metadata);
    }

    private String resolveIssuer(HttpServletRequest request) {
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String requestUri = request.getRequestURI();
        String path = requestUri.substring(contextPath.length());
        String prefix = path.contains("/open/oauth2/") ? "/open/oauth2" : "/oauth2";
        return request.getScheme() + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort())
                + contextPath + prefix;
    }

    private boolean isDefaultPort(HttpServletRequest request) {
        return ("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443);
    }

    private OAuth2AuthorizeDTO buildAuthorizeDto(String responseType,
                                                 String clientId,
                                                 String redirectUri,
                                                 String scope,
                                                 String state,
                                                 String codeChallenge,
                                                 String codeChallengeMethod,
                                                 String nonce) {
        OAuth2AuthorizeDTO dto = new OAuth2AuthorizeDTO();
        dto.setResponseType(responseType);
        dto.setClientId(clientId);
        dto.setRedirectUri(redirectUri);
        dto.setScope(scope);
        dto.setState(state);
        dto.setCodeChallenge(codeChallenge);
        dto.setCodeChallengeMethod(codeChallengeMethod);
        dto.setNonce(nonce);
        return dto;
    }

    private OAuth2TokenDTO buildTokenDto(String grantType,
                                         String clientId,
                                         String clientSecret,
                                         String code,
                                         String redirectUri,
                                         String refreshToken,
                                         String scope,
                                         String codeVerifier,
                                         String username,
                                         String password) {
        OAuth2TokenDTO dto = new OAuth2TokenDTO();
        dto.setGrantType(grantType);
        dto.setClientId(clientId);
        dto.setClientSecret(clientSecret);
        dto.setCode(code);
        dto.setRedirectUri(redirectUri);
        dto.setRefreshToken(refreshToken);
        dto.setScope(scope);
        dto.setCodeVerifier(codeVerifier);
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }
}
