package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.domain.UserVO;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.HanJsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 校验数字校园身份并签发三个课堂短时兼容令牌。 */
@Service
public class ClassroomTokenService {

    private final DigitalCampusLoginService loginService;
    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final String secret;
    private final long ttlSeconds;

    public ClassroomTokenService(
            DigitalCampusLoginService loginService,
            StringRedisTemplate redisTemplate,
            @Value("${sdfz.classroom-gateway.enabled:false}") boolean enabled,
            @Value("${sdfz.classroom-gateway.token-secret:}") String secret,
            @Value("${sdfz.classroom-gateway.token-ttl-seconds:900}") long ttlSeconds) {
        this.loginService = loginService;
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public ClassroomTokenVO exchange(String externalToken, String identityId) {
        requireConfigured();
        String selector = identityId != null && !identityId.isBlank()
                ? identityId : inferIdentityId(externalToken);
        DigitalCampusLoginService.SynchronizedIdentity synchronizedIdentity =
                loginService.synchronize(externalToken, selector);
        UserVO hanUser = synchronizedIdentity.user();
        if (hanUser.getStatus() != null && hanUser.getStatus() != 0) {
            throw new BusinessException("Han user is disabled");
        }

        DigitalCampusProfile.Identity identity = synchronizedIdentity.identity();
        long issuedAt = Instant.now().getEpochSecond();
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> claims = claims(hanUser, identity);
        String token;
        try {
            token = ClassroomTokenCodec.issue(claims, secret, issuedAt, ttlSeconds, tokenId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Classroom token configuration is invalid");
        }
        redisTemplate.opsForValue().set(
                ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId,
                String.valueOf(identity.userId()),
                Duration.ofSeconds(ttlSeconds));
        return new ClassroomTokenVO(token, ttlSeconds);
    }

    private Map<String, Object> claims(UserVO hanUser, DigitalCampusProfile.Identity identity) {
        Set<String> roles = new LinkedHashSet<>();
        addRole(roles, identity.roleType());
        identity.duties().forEach(item -> addRole(roles, item.roleType()));
        identity.classes().forEach(item -> addRole(roles, item.classRoleId()));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", identity.userId());
        claims.put("username", identity.userName());
        claims.put("userType", identity.roleType());
        claims.put("roles", roles);
        claims.put("status", 0);
        claims.put("identityId", identity.identityId());
        claims.put("schoolId", identity.schoolId());
        claims.put("hanUserId", String.valueOf(hanUser.getUserId()));
        return claims;
    }

    private String inferIdentityId(String token) {
        if (token == null || token.length() > 8192) return null;
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Object value = HanJsonUtil.parseMap(json).get("identityId");
            return value instanceof String text && !text.isBlank() ? text : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requireConfigured() {
        if (!enabled) {
            throw new BusinessException("Classroom gateway token exchange is disabled");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || ttlSeconds < 60 || ttlSeconds > 3600) {
            throw new BusinessException("Classroom gateway token configuration is invalid");
        }
    }

    private static void addRole(Set<String> roles, String role) {
        if (role != null && !role.isBlank()) roles.add(role.trim());
    }
}
