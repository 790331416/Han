package com.han.open.service;

import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;

/**
 * OAuth2授权服务接口
 */
public interface IOAuth2Service {

    /**
     * 处理授权请求,返回授权码
     */
    String authorize(OAuth2AuthorizeDTO dto, Long userId);

    /**
     * 获取Token
     */
    OAuth2TokenVO token(OAuth2TokenDTO dto);

    /**
     * 刷新Token
     */
    OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret);

    /**
     * 撤销Token
     */
    void revokeToken(String token, String tokenTypeHint);

    /**
     * Token自省(验证Token有效性)
     */
    Object introspectToken(String token);

    /**
     * 获取用户信息(OpenID Connect UserInfo)
     */
    OAuth2UserInfoVO getUserInfo(String accessToken);

    /**
     * 验证授权码
     */
    Long validateAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier);
}
