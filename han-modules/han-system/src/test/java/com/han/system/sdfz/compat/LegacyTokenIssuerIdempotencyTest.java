package com.han.system.sdfz.compat;

import com.han.common.core.util.ClassroomTokenCodec;
import com.han.system.sdfz.education.domain.EduPersonPo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 正式兼容凭证换发的幂等性。
 *
 * <p>凭证同时是旧侧 AES 密钥的来源（密钥取自 token 末 48 位）。每次换发都新签一张，
 * 会话中途密钥就会变，此刻正在飞的请求用旧密钥加密、新密钥解密，旧网关抛
 * {@code BadPaddingException}，对外是 500「filter-----请求不合法」——偶发、难定位。
 *
 * <p>这批用例钉两个方向：<b>有效期内重复换发拿到同一张</b>、<b>会话失效后拿到新的一张</b>。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyTokenIssuerIdempotencyTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 用内存 map 当 Redis：键的生灭是这批用例的判定依据，不能用 mock 糊过去。 */
    private final Map<String, String> store = new HashMap<>();
    private final Set<String> keys = new HashSet<>();

    private LegacyTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        LegacyCompatProperties properties = new LegacyCompatProperties();
        properties.setEnabled(true);
        properties.setTenantId(1L);
        properties.setTokenSecret(SECRET);
        properties.setTokenTtlSeconds(3600L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            keys.add(inv.getArgument(0));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> keys.contains(inv.getArgument(0)));

        issuer = new LegacyTokenIssuer(properties, redisTemplate);
    }

    private static EduPersonPo teacher() {
        EduPersonPo person = new EduPersonPo();
        person.setId(900001L);
        person.setUserId(900002L);
        person.setSchoolId(900003L);
        person.setPersonName("张老师");
        person.setPersonType("TEACHER");
        return person;
    }

    @Test
    void repeatedIssueWithinTheSessionReturnsTheSameToken() {
        LegacyTokenIssuer.IssuedToken first = issuer.issueSession(teacher());
        LegacyTokenIssuer.IssuedToken second = issuer.issueSession(teacher());

        assertThat(second.token())
                .as("有效期内重复换发必须复用同一张凭证，否则旧侧 AES 密钥会中途改变")
                .isEqualTo(first.token());
    }

    @Test
    void reusedTokenReportsRemainingTtlNotTheOriginalTtl() {
        LegacyTokenIssuer.IssuedToken first = issuer.issueSession(teacher());
        LegacyTokenIssuer.IssuedToken second = issuer.issueSession(teacher());

        assertThat(second.expiresIn())
                .as("复用时要回报剩余有效期，回报原始 TTL 会让调用方以为还能用一小时")
                .isLessThanOrEqualTo(first.expiresIn())
                .isGreaterThan(0);
    }

    @Test
    void issuesAFreshTokenAfterTheSessionIsRevoked() {
        LegacyTokenIssuer.IssuedToken first = issuer.issueSession(teacher());
        String tokenId = ClassroomTokenCodec.verify(first.token(), SECRET,
                java.time.Instant.now().getEpochSecond()).tokenId();

        // 主动失效这次会话（等同 Han 侧登出 / 运维踢下线）
        keys.remove(ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId);

        LegacyTokenIssuer.IssuedToken second = issuer.issueSession(teacher());

        assertThat(second.token())
                .as("会话已被撤销就必须重新签发，不能把作废的凭证再发一遍")
                .isNotEqualTo(first.token());
    }

    @Test
    void differentPeopleNeverShareAToken() {
        EduPersonPo other = teacher();
        other.setId(900011L);
        other.setUserId(900012L);

        assertThat(issuer.issueSession(other).token())
                .as("复用粒度是人，不能串号")
                .isNotEqualTo(issuer.issueSession(teacher()).token());
    }

    /** 中间态凭证只活几分钟、拿到就换正式的，没有复用价值，每次必须新签。 */
    @Test
    void interimTokensAreNotReused() {
        assertThat(issuer.issueInterim(teacher()).token())
                .isNotEqualTo(issuer.issueInterim(teacher()).token());
    }
}
