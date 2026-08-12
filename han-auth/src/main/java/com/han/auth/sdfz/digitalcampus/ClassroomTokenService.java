package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomClaims;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.HanJsonUtil;
import com.han.common.security.domain.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 签发三个课堂短时兼容令牌，支持两条互不影响的来源通路。
 *
 * <ul>
 *   <li>{@link #exchange} 校验数字校园外部身份后签发，属于已冻结但保留可用的数字校园通路；</li>
 *   <li>{@link #exchangeLocal} 从 Han 自己的登录态签发，本地账号没有外部 Token，
 *       也不要求 {@code sys_user_social} 存在任何外部身份绑定。</li>
 * </ul>
 */
@Service
public class ClassroomTokenService {

    /** 非教师身份请求凭证时的固定文案，与兼容层保持一致。 */
    public static final String NON_TEACHER_LOGIN_UNSUPPORTED = "本期仅支持教师登录三个课堂，学生登录暂未开放";

    private final DigitalCampusLoginService loginService;
    private final SystemServiceClient systemServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final String secret;
    private final long ttlSeconds;
    private final Set<String> loginRoleTypes;

    public ClassroomTokenService(
            DigitalCampusLoginService loginService,
            SystemServiceClient systemServiceClient,
            StringRedisTemplate redisTemplate,
            @Value("${sdfz.classroom-gateway.enabled:false}") boolean enabled,
            @Value("${sdfz.classroom-gateway.token-secret:}") String secret,
            @Value("${sdfz.classroom-gateway.token-ttl-seconds:900}") long ttlSeconds,
            @Value("${sdfz.classroom-gateway.login-role-types:2}") String loginRoleTypes) {
        this.loginService = loginService;
        this.systemServiceClient = systemServiceClient;
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.loginRoleTypes = Arrays.stream(loginRoleTypes.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 用 Han 本地登录态换发兼容凭证。
     *
     * <p>本地教师就是 {@code sys_user} + {@code edu_person} 两条记录，claims 直接由这两条记录组装，
     * 不走任何外部身份查找。本期只对教师签发，其它身份返回
     * {@link #NON_TEACHER_LOGIN_UNSUPPORTED}——这只限制登录，不影响该身份在兼容目录里的可见性。
     */
    public ClassroomTokenVO exchangeLocal(LoginUser loginUser) {
        requireConfigured();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException("当前没有可用的 Han 登录态");
        }

        R<ClassroomIdentityVO> result = systemServiceClient.getClassroomIdentity(loginUser.getUserId());
        if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
            throw new BusinessException("当前账号未开通三个课堂身份");
        }
        ClassroomIdentityVO identity = result.getData();
        if (identity.getStatus() != null && identity.getStatus() != 0) {
            throw new BusinessException("当前账号的三个课堂身份已停用");
        }
        if (!loginRoleTypes.contains(identity.getRoleType())) {
            throw new BusinessException(NON_TEACHER_LOGIN_UNSUPPORTED);
        }

        String userId = identity.getUserId() != null
                ? identity.getUserId() : String.valueOf(loginUser.getUserId());
        Map<String, Object> claims = ClassroomClaims.build(
                userId,
                identity.getUserName(),
                identity.getRoleType(),
                identity.getRoles(),
                identity.getIdentityId(),
                identity.getSchoolId(),
                String.valueOf(loginUser.getUserId()));
        return sign(claims, userId);
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
        return sign(claims(hanUser, identity), identity.userId());
    }

    private ClassroomTokenVO sign(Map<String, Object> claims, String sessionUserId) {
        long issuedAt = Instant.now().getEpochSecond();
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token;
        try {
            token = ClassroomTokenCodec.issue(claims, secret, issuedAt, ttlSeconds, tokenId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Classroom token configuration is invalid");
        }
        redisTemplate.opsForValue().set(
                ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId,
                String.valueOf(sessionUserId),
                Duration.ofSeconds(ttlSeconds));
        return new ClassroomTokenVO(token, ttlSeconds);
    }

    private Map<String, Object> claims(UserVO hanUser, DigitalCampusProfile.Identity identity) {
        Set<String> roles = new LinkedHashSet<>();
        addRole(roles, identity.roleType());
        identity.duties().forEach(item -> addRole(roles, item.roleType()));
        identity.classes().forEach(item -> addRole(roles, item.classRoleId()));

        return ClassroomClaims.build(
                identity.userId(), identity.userName(), identity.roleType(), roles,
                identity.identityId(), identity.schoolId(), String.valueOf(hanUser.getUserId()));
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
