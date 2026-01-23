package com.xuman.open.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * OAuth2 Token请求DTO
 */
@Data
public class OAuth2TokenDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 授权类型
     */
    @NotBlank(message = "grant_type不能为空")
    private String grantType;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 授权码(authorization_code模式)
     */
    private String code;

    /**
     * 重定向URI
     */
    private String redirectUri;

    /**
     * 刷新令牌(refresh_token模式)
     */
    private String refreshToken;

    /**
     * 授权范围
     */
    private String scope;

    /**
     * PKCE code_verifier
     */
    private String codeVerifier;

    /**
     * 用户名(password模式,不推荐)
     */
    private String username;

    /**
     * 密码(password模式,不推荐)
     */
    private String password;
}
