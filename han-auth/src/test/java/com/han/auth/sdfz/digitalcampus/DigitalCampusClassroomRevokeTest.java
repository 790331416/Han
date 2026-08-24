package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.auth.config.SecurityProperties;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.TotpService;
import com.han.auth.service.impl.AuthServiceImpl;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.util.ClassroomTokenCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数字校园课堂凭证与撤销链联动：撤销本地教育身份时，按
 * {@code active:{hanUserId}:{localIdentityId}} 定位并作废该数字校园课堂凭证。
 *
 * <p>凭证由 {@link ClassroomTokenService#exchange} 以 Han userId + 本地 edu_person.id 写入，
 * {@link AuthServiceImpl#revokeSession} 的身份级撤销按同一键删除，这里验证两者键规则一致。
 */
class DigitalCampusClassroomRevokeTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void revokingLocalIdentityDeletesDigitalCampusClassroomToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        // 先由 ClassroomTokenService 走数字校园通路签发一张正式凭证，本地身份为 900。
        DigitalCampusLoginService loginService = mock(DigitalCampusLoginService.class);
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity(), 900L, "77"));
        ClassroomTokenService tokenService = new ClassroomTokenService(
                loginService, mock(SystemServiceClient.class), redisTemplate, true, SECRET, 900, "2");
        ClassroomTokenVO issued = tokenService.exchange("external-token", "identity-1");

        String tokenId = ClassroomTokenCodec.verify(issued.accessToken(), SECRET,
                Instant.now().getEpochSecond()).tokenId();
        String identityKey = ClassroomTokenCodec.activeIdentityKey("100", "900");
        // 撤销链按身份级 Active Key 读回凭证原文，再据 jti 删会话键。
        when(valueOperations.get(identityKey)).thenReturn(issued.accessToken());
        when(setOperations.members(CacheConstants.SESSION_IDENTITY_KEY + 100L + ":900"))
                .thenReturn(Set.of());

        AuthServiceImpl authService = new AuthServiceImpl(
                redisTemplate,
                mock(SystemServiceClient.class),
                mock(TenantServiceClient.class),
                new SecurityProperties(),
                mock(TotpService.class),
                mock(CaptchaSettingService.class));
        authService.revokeSession(100L, 900L);

        verify(redisTemplate).delete(identityKey);
        verify(redisTemplate).delete(ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId);
    }

    private static DigitalCampusProfile.Identity identity() {
        return new DigitalCampusProfile.Identity(
                "external-user-1", "Teacher One", "identity-1", "Teacher", "2",
                "school-1", "School One", "class-1", "Class One", "2", "500100",
                List.of(new DigitalCampusProfile.Duty("duty-1", "9", "Teacher", "Teacher")),
                List.of(new DigitalCampusProfile.ClassMembership(
                        "class-1", "Class One", "teacher", "Class One", "school-1", "School One",
                        "3", "500100", "", "", "", "", "", "", "", "")));
    }
}
