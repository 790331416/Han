package com.han.system.sdfz.compat;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomAesCodec;
import com.han.common.core.util.ClassroomClaims;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.system.sdfz.education.domain.EduPersonPo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 为本地账号签发三课堂兼容凭证。
 *
 * <p>不经过数字校园换票：本地教师就是 {@code sys_user} + {@code edu_person} 两条记录，
 * claims 直接由这两条记录组装，不查任何外部身份绑定。
 *
 * <p>本期只对教师签发。学生在兼容目录里照常可见（名册、课程参与、历史统计），
 * 但走到这里会被 {@link #STUDENT_LOGIN_UNSUPPORTED} 明确拒绝，不产生任何凭证。
 *
 * <p>签发出的凭证同时是旧侧的 AES 密钥来源，因此会话中途不得更换——正式凭证只在
 * {@code getOneById} 这一步签发一次。
 */
@Component
@RequiredArgsConstructor
public class LegacyTokenIssuer {

    /** 非教师身份请求凭证时的固定文案，便于旧侧与联调日志识别这是范围限制而非故障。 */
    public static final String STUDENT_LOGIN_UNSUPPORTED = "本期仅支持教师登录三个课堂，学生登录暂未开放";

    private final LegacyCompatProperties properties;
    private final StringRedisTemplate redisTemplate;

    /** 该身份本期是否允许换取凭证，供调用方在签发前做前置判断。 */
    public boolean canIssueFor(EduPersonPo person) {
        return person != null && properties.canIssueToken(person.getPersonType());
    }

    /**
     * @param token     兼容凭证原文
     * @param expiresIn 有效期（秒）
     */
    public record IssuedToken(String token, long expiresIn) {
    }

    public IssuedToken issueInterim(EduPersonPo person) {
        return issue(person, properties.getInterimTokenTtlSeconds());
    }

    public IssuedToken issueSession(EduPersonPo person) {
        return issue(person, properties.getTokenTtlSeconds());
    }

    private IssuedToken issue(EduPersonPo person, long ttlSeconds) {
        String secret = requireSecret();
        if (!canIssueFor(person)) {
            throw new BusinessException(STUDENT_LOGIN_UNSUPPORTED);
        }
        String roleType = properties.roleTypeOf(person.getPersonType());
        String userId = person.getUserId() != null
                ? String.valueOf(person.getUserId()) : String.valueOf(person.getId());
        Map<String, Object> claims = new LinkedHashMap<>(ClassroomClaims.build(
                userId,
                person.getPersonName(),
                roleType,
                List.of(roleType, person.getPersonType() == null ? "" : person.getPersonType()),
                String.valueOf(person.getId()),
                person.getSchoolId() == null ? "" : String.valueOf(person.getSchoolId()),
                userId));
        claims.put("tenantId", properties.getTenantId());

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        long issuedAt = Instant.now().getEpochSecond();
        String token;
        try {
            token = ClassroomTokenCodec.issue(claims, secret, issuedAt, ttlSeconds, tokenId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("兼容凭证配置无效");
        }
        if (!ClassroomAesCodec.canDeriveKey(token)) {
            throw new BusinessException("兼容凭证不满足旧侧 AES 密钥派生约束");
        }
        redisTemplate.opsForValue().set(
                ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId, userId, Duration.ofSeconds(ttlSeconds));
        return new IssuedToken(token, ttlSeconds);
    }

    /** 校验签名与有效期，并确认会话未被主动失效。 */
    public ClassroomTokenCodec.VerifiedToken verify(String token) {
        String secret = requireSecret();
        ClassroomTokenCodec.VerifiedToken verified;
        try {
            verified = ClassroomTokenCodec.verify(token, secret, Instant.now().getEpochSecond());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        Boolean active = redisTemplate.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + verified.tokenId());
        if (!Boolean.TRUE.equals(active)) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        return verified;
    }

    private String requireSecret() {
        String secret = properties.getTokenSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new BusinessException("兼容层缺少配置项 sdfz.compat.token-secret");
        }
        return secret;
    }
}
