package com.han.open.domain.dto;

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
     *
     * <p>WRITE_ONLY：只接收、不序列化。当前 Token 端点没有 @OperLog，这里是防御性收口，
     * 避免以后有人给该端点加操作日志时把客户端密钥和用户口令写进库。</p>
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
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
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;
}
