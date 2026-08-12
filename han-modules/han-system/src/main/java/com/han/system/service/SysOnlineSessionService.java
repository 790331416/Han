package com.han.system.service;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.util.HanJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 在线会话查询与强制下线。
 *
 * <p>在线用户列表与首页仪表盘此前各自用 {@code KEYS han:token:*} 扫描 Redis。
 * {@code KEYS} 在 Redis 上是 O(N) 的阻塞命令，几万个 key 时会卡住整个单线程实例，
 * 这里统一改成游标式 {@code SCAN} 并批量取值，同时补上租户维度的可见性判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOnlineSessionService {

    /** 单次 SCAN 的建议返回条数 */
    private static final int SCAN_BATCH = 500;

    /** 单次扫描处理的会话上限，防止 key 规模异常时把内存打满 */
    private static final int MAX_SCAN_KEYS = 20000;

    private final StringRedisTemplate redisTemplate;

    /**
     * 列出当前调用方可见的在线会话。
     *
     * @param username 用户名模糊过滤，可为空
     * @param ipAddr   登录IP模糊过滤，可为空
     */
    public List<OnlineSession> listVisibleSessions(String username, String ipAddr) {
        List<OnlineSession> sessions = new ArrayList<>();
        for (String key : scanTokenKeys()) {
            OnlineSession session = readSession(key);
            if (session == null || !isVisible(session)) {
                continue;
            }
            if (username != null && !username.isEmpty() && !session.username().contains(username)) {
                continue;
            }
            if (ipAddr != null && !ipAddr.isEmpty() && !session.ipAddr().contains(ipAddr)) {
                continue;
            }
            sessions.add(session);
        }
        sessions.sort(Comparator.comparingLong(
                (OnlineSession s) -> s.loginTime() == null ? 0L : s.loginTime()).reversed());
        return sessions;
    }

    /**
     * 统计当前调用方可见的在线会话数。
     */
    public int countVisibleSessions() {
        int count = 0;
        for (String key : scanTokenKeys()) {
            OnlineSession session = readSession(key);
            if (session != null && isVisible(session)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 强制下线。
     *
     * <p>此前只删了 access token，用户拿 refresh token 立刻就能换回来，强退形同虚设。
     * 这里连带清理 refresh token 与登录用户索引。refresh 侧只有「refreshToken -&gt; accessToken」
     * 的正向映射、没有反向索引，所以只能扫一遍 refresh key 找出指向本次 access token 的那条；
     * 强退是低频管理动作，可以接受，建索引需要改 han-auth 的签发逻辑。
     *
     * @return 是否确实下线了一个会话
     */
    public boolean forceLogout(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) {
            return false;
        }
        String tokenKey = CacheConstants.TOKEN_KEY + tokenId;
        OnlineSession session = readSession(tokenKey);
        if (session == null) {
            return false;
        }
        if (!isVisible(session)) {
            throw new ForbiddenException("无权下线其它租户的用户");
        }

        redisTemplate.delete(tokenKey);
        if (session.userId() != null && session.clientType() != null && !session.clientType().isEmpty()) {
            redisTemplate.delete(CacheConstants.LOGIN_USER_KEY + session.userId() + ":" + session.clientType());
        }
        deleteRefreshTokensPointingTo(tokenId);
        return true;
    }

    /**
     * 撤销指定用户的全部会话。
     *
     * <p>{@code HeaderAuthenticationFilter} 是从 Redis 里的 LoginUser 快照做权限判定的，
     * 而这份快照只在登录时写入。用户被删除 / 停用 / 改角色 / 改密码之后不撤销，
     * 他手上的 Token 在有效期内继续全权可用，撤销的角色也不会生效。
     *
     * @return 撤销的会话数
     */
    public int revokeByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Set<Long> targets = new HashSet<>(userIds);
        return revokeMatching(session -> targets.contains(session.userId()));
    }

    /**
     * 撤销单个用户的全部会话。
     */
    public int revokeByUserId(Long userId) {
        return userId == null ? 0 : revokeByUserIds(List.of(userId));
    }

    /**
     * 撤销会话时 Redis 不可用不应该把业务操作一起回滚：
     * 停用用户、收回角色这些动作本身必须落库成功，会话最迟也会随 Token 过期失效。
     * 因此这里吞掉异常并记 ERROR 日志，由运维从日志发现。
     */
    private int revokeMatching(Predicate<OnlineSession> matcher) {
        try {
            return doRevokeMatching(matcher);
        } catch (Exception e) {
            log.error("撤销在线会话失败，相关用户的登录态需等 Token 自然过期", e);
            return 0;
        }
    }

    private int doRevokeMatching(Predicate<OnlineSession> matcher) {
        List<OnlineSession> matched = new ArrayList<>();
        for (String key : scanTokenKeys()) {
            OnlineSession session = readSession(key);
            if (session != null && session.userId() != null && matcher.test(session)) {
                matched.add(session);
            }
        }
        if (matched.isEmpty()) {
            return 0;
        }

        Set<String> accessTokens = new HashSet<>();
        List<String> keysToDelete = new ArrayList<>();
        for (OnlineSession session : matched) {
            accessTokens.add(session.tokenId());
            keysToDelete.add(CacheConstants.TOKEN_KEY + session.tokenId());
            if (session.clientType() != null && !session.clientType().isEmpty()) {
                keysToDelete.add(CacheConstants.LOGIN_USER_KEY + session.userId() + ":" + session.clientType());
            }
        }
        keysToDelete.addAll(findRefreshKeysPointingTo(accessTokens));
        redisTemplate.delete(keysToDelete);
        log.info("已撤销 {} 个在线会话", accessTokens.size());
        return accessTokens.size();
    }

    // ==================== 私有方法 ====================

    private List<String> scanTokenKeys() {
        return scanKeys(CacheConstants.TOKEN_KEY + "*");
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && keys.size() < MAX_SCAN_KEYS) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    private void deleteRefreshTokensPointingTo(String accessToken) {
        List<String> stale = findRefreshKeysPointingTo(Set.of(accessToken));
        if (!stale.isEmpty()) {
            redisTemplate.delete(stale);
        }
    }

    private List<String> findRefreshKeysPointingTo(Set<String> accessTokens) {
        List<String> refreshKeys = scanKeys(CacheConstants.REFRESH_TOKEN_KEY + "*");
        if (refreshKeys.isEmpty()) {
            return List.of();
        }
        List<String> values = redisTemplate.opsForValue().multiGet(refreshKeys);
        if (values == null) {
            return List.of();
        }
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < refreshKeys.size() && i < values.size(); i++) {
            if (values.get(i) != null && accessTokens.contains(values.get(i))) {
                stale.add(refreshKeys.get(i));
            }
        }
        return stale;
    }

    private OnlineSession readSession(String tokenKey) {
        String json = redisTemplate.opsForValue().get(tokenKey);
        if (json == null) {
            return null;
        }
        Map<String, Object> user = HanJsonUtil.parseMap(json);
        if (user == null) {
            return null;
        }
        return new OnlineSession(
                tokenKey.substring(CacheConstants.TOKEN_KEY.length()),
                toLong(user.get("userId")),
                toLong(user.get("tenantId")),
                String.valueOf(user.getOrDefault("username", "")),
                String.valueOf(user.getOrDefault("nickname", "")),
                String.valueOf(user.getOrDefault("loginIp", "")),
                String.valueOf(user.getOrDefault("clientType", "")),
                toLong(user.get("loginTime")));
    }

    /**
     * 会话是否对当前调用方可见。
     *
     * <p>超级管理员看全平台；其余管理员只能看到本租户的会话，
     * 此前任一租户的管理员都能看到并强退其它租户的用户。
     */
    private boolean isVisible(OnlineSession session) {
        if (SecurityContextHolder.isAdmin()) {
            return true;
        }
        return Objects.equals(session.tenantId(), SecurityContextHolder.getTenantId());
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Redis 里一条在线会话的快照。
     */
    public record OnlineSession(String tokenId, Long userId, Long tenantId, String username,
                                String nickname, String ipAddr, String clientType, Long loginTime) {
    }
}
