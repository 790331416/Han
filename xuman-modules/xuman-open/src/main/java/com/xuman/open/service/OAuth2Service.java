package com.xuman.open.service;

import com.xuman.open.domain.dto.OAuth2AuthorizeDTO;
import com.xuman.open.domain.dto.OAuth2TokenDTO;
import com.xuman.open.domain.vo.OAuth2TokenVO;
import com.xuman.open.domain.vo.OAuth2UserInfoVO;

/**
 * OAuth2授权服务接口
 */
public interface OAuth2Service {

    /**
     * 处理授权请求,返回授权码
     * @param dto 授权请求参数
     * @param userId 当前登录用户ID
     * @return 授权码
     */
    String authorize(OAuth2AuthorizeDTO dto, Long userId);

    /**
     * 获取Token
     * @param dto Token请求参数
     * @return Token响应
     */
    OAuth2TokenVO token(OAuth2TokenDTO dto);

    /**
     * 刷新Token
     * @param refreshToken 刷新令牌
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @return 新的Token响应
     */
    OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret);

    /**
     * 撤销Token
     * @param token 访问令牌或刷新令牌
     * @param tokenTypeHint 令牌类型(access_token/refresh_token)
     */
    void revokeToken(String token, String tokenTypeHint);

    /**
     * Token自省(验证Token有效性)
     * @param token 访问令牌
     * @return Token信息
     */
    Object introspectToken(String token);

    /**
     * 获取用户信息(OpenID Connect UserInfo)
     * @param accessToken 访问令牌
     * @return 用户信息
     */
    OAuth2UserInfoVO getUserInfo(String accessToken);

    /**
     * 验证授权码
     * @param code 授权码
     * @param clientId 客户端ID
     * @param redirectUri 重定向URI
     * @param codeVerifier PKCE验证码
     * @return 用户ID
     */
    Long validateAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier);
}
