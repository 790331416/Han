package com.han.tenant.service.support;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.domain.LoginUser;
import com.han.tenant.config.HanTenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 按租户吊销已签发的登录会话。
 * <p>
 * 租户停用 / 过期 / 删除原先只改 {@code sys_tenant.status}，而租户有效性只在登录与切租户时校验一次，
 * 网关只校验 token 是否存在于 Redis。结果是已登录用户在租户停用后仍能在 token 有效期内正常访问，
 * 且能靠 refreshToken 无限续期。这里在停用侧补上会话吊销。
 * <p>
 * 键结构复用 {@link CacheConstants}（跨服务共享常量），与 han-system 的在线用户强退
 * （{@code ASysOnlineController}）是同一套做法：扫描 {@code han:token:*}、按 LoginUser 里的租户匹配、
 * 删除访问令牌与用户令牌映射。删掉访问令牌后 {@code /auth/refresh} 也会因取不到旧令牌而失败。
 * <p>
 * 吊销是尽力而为：Redis 不可用时记 ERROR 但不阻断停用动作本身，否则会退化成「租户既没停用、
 * 会话也没吊销」，比只吊销失败更糟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSessionRevoker {

    private final StringRedisTemplate redisTemplate;
    private final HanTenantProperties properties;

    /**
     * 吊销指定租户下的全部登录会话。
     *
     * @return 实际吊销的会话数
     */
    public int revokeByTenant(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }
        if (!properties.getSession().isRevokeOnDisable()) {
            log.warn("租户[{}]会话吊销已被配置关闭，已签发 Token 将保留到自然过期", tenantId);
            return 0;
        }

        try {
            Set<String> revokedTokens = deleteAccessTokens(tenantId);
            deleteRefreshTokens(revokedTokens);
            if (!revokedTokens.isEmpty()) {
                log.info("租户[{}]会话吊销完成，共吊销 {} 个会话", tenantId, revokedTokens.size());
            }
            return revokedTokens.size();
        } catch (Exception e) {
            log.error("租户[{}]会话吊销失败，已签发 Token 仍可用至自然过期，需人工介入", tenantId, e);
            return 0;
        }
    }

    private Set<String> deleteAccessTokens(Long tenantId) {
        Set<String> revokedTokens = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(CacheConstants.TOKEN_KEY + "*")
                .count(properties.getSession().getScanBatchSize())
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String tokenKey = cursor.next();
                String json = redisTemplate.opsForValue().get(tokenKey);
                if (json == null || json.isBlank()) {
                    continue;
                }
                LoginUser loginUser = parseLoginUser(json);
                if (loginUser == null || !tenantId.equals(loginUser.getTenantId())) {
                    continue;
                }
                redisTemplate.delete(tokenKey);
                if (loginUser.getUserId() != null && loginUser.getClientType() != null) {
                    redisTemplate.delete(CacheConstants.LOGIN_USER_KEY
                            + loginUser.getUserId() + ":" + loginUser.getClientType().getCode());
                }
                revokedTokens.add(tokenKey.substring(CacheConstants.TOKEN_KEY.length()));
            }
        }
        return revokedTokens;
    }

    private void deleteRefreshTokens(Set<String> revokedTokens) {
        if (revokedTokens.isEmpty()) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions()
                .match(CacheConstants.REFRESH_TOKEN_KEY + "*")
                .count(properties.getSession().getScanBatchSize())
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String refreshKey = cursor.next();
                String accessToken = redisTemplate.opsForValue().get(refreshKey);
                if (accessToken != null && revokedTokens.contains(accessToken)) {
                    redisTemplate.delete(refreshKey);
                }
            }
        }
    }

    private LoginUser parseLoginUser(String json) {
        try {
            return XuJsonUtil.parseObject(json, LoginUser.class);
        } catch (Exception e) {
            log.warn("解析在线会话失败，已跳过该会话", e);
            return null;
        }
    }
}
