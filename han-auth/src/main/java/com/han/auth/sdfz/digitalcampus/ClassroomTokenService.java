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
import java.util.LinkedHashMap;
import java.util.List;
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

    /** 身份不在当前部署允许名单时的固定文案。 */
    public static final String NON_TEACHER_LOGIN_UNSUPPORTED = "当前身份暂未开放三个课堂登录";

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
            @Value("${sdfz.classroom-gateway.login-role-types:2,4}") String loginRoleTypes) {
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
     * <p>本地教师或学生均由 {@code sys_user} + {@code edu_person} 组装 claims，
     * 不走任何外部身份查找。允许的 roleType 由部署配置控制，校端业务权限仍须服务端另行校验。
     */
    public ClassroomTokenVO exchangeLocal(LoginUser loginUser) {
        return exchangeLocal(loginUser, null);
    }

    /** 当前 Han 登录账号选择教育身份后换取 Classroom Token。 */
    public ClassroomTokenVO exchangeLocal(LoginUser loginUser, String identityId) {
        requireConfigured();
        if (loginUser == null || loginUser.getUserId() == null
                || loginUser.getTenantId() == null || loginUser.getTenantId() <= 0) {
            throw new BusinessException("当前没有可用的 Han 登录态");
        }

        String selected = identityId == null || identityId.isBlank() ? null : identityId.trim();
        // identityScoped 登录态在签发时就已经绑定了身份：只能用该身份换发课堂 Token，
        // 不能用 A 会话传 B 的 identityId 去套出 B 身份的凭证。
        if (loginUser.isIdentityScoped() && loginUser.getIdentityId() != null) {
            String scoped = String.valueOf(loginUser.getIdentityId());
            if (selected != null && !scoped.equals(selected)) {
                throw new BusinessException("当前会话身份不匹配，请重新登录");
            }
            selected = scoped;
        }
        if (selected == null) {
            // 多身份账号必须显式选择 identityId；列表不可用时由 han-system 的 resolve 兜底判断。
            requireSingleLoginIdentity(loginUser.getUserId());
        }
        R<ClassroomIdentityVO> result = selected == null
                ? systemServiceClient.getClassroomIdentity(loginUser.getUserId())
                : systemServiceClient.getClassroomIdentity(loginUser.getUserId(), selected);
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
        Map<String, Object> claims = new LinkedHashMap<>(ClassroomClaims.build(
                userId,
                identity.getUserName(),
                identity.getRoleType(),
                identity.getRoles(),
                identity.getIdentityId(),
                identity.getSchoolId(),
                String.valueOf(loginUser.getUserId())));
        claims.put("tenantId", loginUser.getTenantId());
        claims.put("personType", identity.getPersonType() == null ? "" : identity.getPersonType());
        claims.put("classIds", identity.getClassIds() == null ? List.of() : identity.getClassIds());
        return sign(claims, loginUser.getUserId(), parseIdentityId(identity.getIdentityId()));
    }

    /** 未显式选择身份时，多身份账号必须报业务错误，不允许默认取第一条。 */
    private void requireSingleLoginIdentity(Long userId) {
        R<List<ClassroomIdentityVO>> listResult = systemServiceClient.listClassroomIdentities(userId);
        if (listResult == null || listResult.getCode() != Constants.SUCCESS || listResult.getData() == null) {
            return;
        }
        long loginAllowed = listResult.getData().stream()
                .filter(ClassroomIdentityVO::isLoginAllowed)
                .count();
        if (loginAllowed > 1) {
            throw new BusinessException("当前账号存在多个教育身份，请先选择身份");
        }
    }

    /** 返回当前账号可展示的教育身份；是否可签发由 {@code loginAllowed} 标识。 */
    public List<ClassroomIdentityVO> listLocalIdentities(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException("当前没有可用的 Han 登录态");
        }
        R<List<ClassroomIdentityVO>> result = systemServiceClient.listClassroomIdentities(loginUser.getUserId());
        if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
            throw new BusinessException("当前账号未开通三个课堂身份");
        }
        return result.getData();
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
        Map<String, Object> claims = claims(hanUser, identity,
                synchronizedIdentity.localIdentityId(), synchronizedIdentity.localSchoolId());
        return sign(claims, hanUser.getUserId(), synchronizedIdentity.localIdentityId());
    }

    private ClassroomTokenVO sign(Map<String, Object> claims, Long hanUserId, Long localIdentityId) {
        long issuedAt = Instant.now().getEpochSecond();
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token;
        try {
            token = ClassroomTokenCodec.issue(claims, secret, issuedAt, ttlSeconds, tokenId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Classroom token configuration is invalid");
        }
        // Session Key 的 value 写 Han userId，不混用外部/内部 ID。
        redisTemplate.opsForValue().set(
                ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId,
                String.valueOf(hanUserId),
                Duration.ofSeconds(ttlSeconds));
        // 身份级 Active Key：active:{hanUserId}:{localIdentityId}。同一账号的多个学校身份
        // 各持一张正式凭证，换发复用（幂等）与撤销都按「身份」粒度判定，与 han-system 的
        // LegacyTokenIssuer 同一键规则，避免身份 A 的凭证被复用到身份 B、claims 里的
        // identityId 与实际身份错位。
        if (localIdentityId != null) {
            redisTemplate.opsForValue().set(
                    ClassroomTokenCodec.activeIdentityKey(
                            String.valueOf(hanUserId), String.valueOf(localIdentityId)),
                    token,
                    Duration.ofSeconds(ttlSeconds));
        }
        // 账号级 Active Key 仅作旧版兼容索引：登出撤销链（AuthServiceImpl）按 hanUserId 粒度读它
        // 定位当前凭证；不写这里登出/切换身份后撤销不到课堂凭证。隔离依据是上面的身份级 Key。
        redisTemplate.opsForValue().set(ClassroomTokenCodec.activeKey(String.valueOf(hanUserId)), token,
                Duration.ofSeconds(ttlSeconds));
        return new ClassroomTokenVO(token, ttlSeconds);
    }

    private Map<String, Object> claims(UserVO hanUser, DigitalCampusProfile.Identity identity,
                                       Long localIdentityId, String localSchoolId) {
        Set<String> roles = new LinkedHashSet<>();
        addRole(roles, identity.roleType());
        identity.duties().forEach(item -> addRole(roles, item.roleType()));
        identity.classes().forEach(item -> addRole(roles, item.classRoleId()));

        Map<String, Object> claims = new LinkedHashMap<>(ClassroomClaims.build(
                identity.userId(), identity.userName(), identity.roleType(), roles,
                text(localIdentityId), localSchoolId, String.valueOf(hanUser.getUserId())));
        // 统一数字校园课堂凭证 ID 域：hanUserId 是 Han sys_user.id，identityId 是本地 edu_person.id，
        // 外部标识单独用 externalUserId / externalIdentityId 承载，不再复用同一组 claim 造成歧义。
        claims.put("externalUserId", identity.userId());
        claims.put("externalIdentityId", identity.identityId());
        return claims;
    }

    private static String text(Long value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static Long parseIdentityId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
