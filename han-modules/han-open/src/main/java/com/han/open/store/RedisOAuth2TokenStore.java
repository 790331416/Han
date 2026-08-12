package com.han.open.store;

import com.han.common.core.util.HanJsonUtil;
import com.han.open.constant.OpenCacheConstants;
import com.han.open.domain.token.AccessTokenRecord;
import com.han.open.domain.token.AuthorizationCodeRecord;
import com.han.open.domain.token.RefreshTokenRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;

/**
 * 基于 Redis 的 OAuth2 存储实现。
 *
 * <p>写入一律使用 {@code SET key value EX ttl} 单命令完成，避免「写入成功但 TTL 设置失败」留下永不过期的凭证。
 */
@Component
@RequiredArgsConstructor
public class RedisOAuth2TokenStore implements IOAuth2TokenStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveAuthorizationCode(String code, AuthorizationCodeRecord record, Duration ttl) {
        save(OpenCacheConstants.AUTH_CODE_KEY + code, record, ttl);
    }

    @Override
    public AuthorizationCodeRecord consumeAuthorizationCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String json = redisTemplate.opsForValue().getAndDelete(OpenCacheConstants.AUTH_CODE_KEY + code);
        return HanJsonUtil.parseObject(json, AuthorizationCodeRecord.class);
    }

    @Override
    public void saveAccessToken(String accessToken, AccessTokenRecord record, Duration ttl) {
        save(OpenCacheConstants.ACCESS_TOKEN_KEY + accessToken, record, ttl);
    }

    @Override
    public AccessTokenRecord getAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        return HanJsonUtil.parseObject(
                redisTemplate.opsForValue().get(OpenCacheConstants.ACCESS_TOKEN_KEY + accessToken),
                AccessTokenRecord.class);
    }

    @Override
    public AccessTokenRecord removeAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        String json = redisTemplate.opsForValue().getAndDelete(OpenCacheConstants.ACCESS_TOKEN_KEY + accessToken);
        return HanJsonUtil.parseObject(json, AccessTokenRecord.class);
    }

    @Override
    public void saveRefreshToken(String refreshToken, RefreshTokenRecord record, Duration ttl) {
        save(OpenCacheConstants.REFRESH_TOKEN_KEY + refreshToken, record, ttl);
    }

    @Override
    public RefreshTokenRecord getRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        return HanJsonUtil.parseObject(
                redisTemplate.opsForValue().get(OpenCacheConstants.REFRESH_TOKEN_KEY + refreshToken),
                RefreshTokenRecord.class);
    }

    @Override
    public RefreshTokenRecord removeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String json = redisTemplate.opsForValue().getAndDelete(OpenCacheConstants.REFRESH_TOKEN_KEY + refreshToken);
        return HanJsonUtil.parseObject(json, RefreshTokenRecord.class);
    }

    @Override
    public void indexUserToken(String clientId, Long userId, String accessToken, Duration ttl) {
        if (userId == null || userId == 0L || !StringUtils.hasText(clientId) || !StringUtils.hasText(accessToken)) {
            return;
        }
        String key = userTokenIndexKey(clientId, userId);
        redisTemplate.opsForSet().add(key, accessToken);
        redisTemplate.expire(key, ttl);
    }

    @Override
    public int revokeUserTokens(String clientId, Long userId) {
        if (userId == null || !StringUtils.hasText(clientId)) {
            return 0;
        }
        String key = userTokenIndexKey(clientId, userId);
        Set<String> accessTokens = redisTemplate.opsForSet().members(key);
        redisTemplate.delete(key);
        if (accessTokens == null || accessTokens.isEmpty()) {
            return 0;
        }
        int revoked = 0;
        for (String accessToken : accessTokens) {
            AccessTokenRecord record = removeAccessToken(accessToken);
            if (record != null) {
                revoked++;
                if (StringUtils.hasText(record.getRefreshToken())) {
                    removeRefreshToken(record.getRefreshToken());
                }
            }
        }
        return revoked;
    }

    private void save(String key, Object record, Duration ttl) {
        redisTemplate.opsForValue().set(key, HanJsonUtil.toJsonString(record), ttl);
    }

    private String userTokenIndexKey(String clientId, Long userId) {
        return OpenCacheConstants.USER_TOKEN_INDEX_KEY + clientId + ":" + userId;
    }
}
