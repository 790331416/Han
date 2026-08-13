package com.han.open.store;

import com.han.open.domain.token.AccessTokenRecord;
import com.han.open.domain.token.AuthorizationCodeRecord;
import com.han.open.domain.token.RefreshTokenRecord;

import java.time.Duration;

/**
 * OAuth2 授权码与令牌存储。
 *
 * <p>实现必须是集中式的：han-open 会以多副本部署在网关的 {@code lb://han-open} 后面，
 * 授权与换票请求不保证落到同一个实例。
 */
public interface IOAuth2TokenStore {

    /**
     * 保存授权码。
     */
    void saveAuthorizationCode(String code, AuthorizationCodeRecord record, Duration ttl);

    /**
     * 取出并删除授权码，保证一次性消费。
     */
    AuthorizationCodeRecord consumeAuthorizationCode(String code);

    /**
     * 保存访问令牌。
     */
    void saveAccessToken(String accessToken, AccessTokenRecord record, Duration ttl);

    /**
     * 读取访问令牌。
     */
    AccessTokenRecord getAccessToken(String accessToken);

    /**
     * 删除访问令牌并返回被删除的记录。
     */
    AccessTokenRecord removeAccessToken(String accessToken);

    /**
     * 保存刷新令牌。
     */
    void saveRefreshToken(String refreshToken, RefreshTokenRecord record, Duration ttl);

    /**
     * 读取刷新令牌。
     */
    RefreshTokenRecord getRefreshToken(String refreshToken);

    /**
     * 删除刷新令牌并返回被删除的记录。
     */
    RefreshTokenRecord removeRefreshToken(String refreshToken);

    /**
     * 把访问令牌登记到「用户 + 应用」索引，用于按用户维度批量撤销。
     */
    void indexUserToken(String clientId, Long userId, String accessToken, Duration ttl);

    /**
     * 撤销某用户在某应用下的全部令牌，返回撤销的访问令牌数量。
     */
    int revokeUserTokens(String clientId, Long userId);
}
