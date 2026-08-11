package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.domain.UserVO;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private DigitalCampusLoginService loginService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void exchangesVerifiedIdentityForSignedRevocableToken() {
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        DigitalCampusProfile.Identity identity = identity();
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ClassroomTokenService service = new ClassroomTokenService(
                loginService, redisTemplate, true, SECRET, 900);

        ClassroomTokenVO result = service.exchange("external-token", "identity-1");

        ClassroomTokenCodec.VerifiedToken verified = ClassroomTokenCodec.verify(
                result.accessToken(), SECRET, Instant.now().getEpochSecond());
        assertThat(verified.claims())
                .containsEntry("userId", "external-user-1")
                .containsEntry("identityId", "identity-1")
                .containsEntry("hanUserId", "100");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) verified.claims().get("roles");
        assertThat(roles).containsExactlyInAnyOrder("2", "9", "teacher");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), eq("external-user-1"), eq(Duration.ofSeconds(900)));
        assertThat(key.getValue()).isEqualTo(ClassroomTokenCodec.SESSION_KEY_PREFIX + verified.tokenId());
    }

    @Test
    void refusesToIssueWhenFeatureIsDisabled() {
        ClassroomTokenService service = new ClassroomTokenService(
                loginService, redisTemplate, false, SECRET, 900);

        assertThatThrownBy(() -> service.exchange("external-token", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void codecRejectsTamperingAndExpiry() {
        String token = ClassroomTokenCodec.issue(
                java.util.Map.of("userId", "1"), SECRET, 1000, 60, "token-1");

        assertThatThrownBy(() -> ClassroomTokenCodec.verify(token + "x", SECRET, 1001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassroomTokenCodec.verify(token, SECRET, 1060))
                .isInstanceOf(IllegalArgumentException.class);
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
