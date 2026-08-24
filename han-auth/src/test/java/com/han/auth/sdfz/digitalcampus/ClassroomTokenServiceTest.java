package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassroomTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private DigitalCampusLoginService loginService;
    @Mock
    private SystemServiceClient systemServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ClassroomTokenService service(boolean enabled) {
        return new ClassroomTokenService(
                loginService, systemServiceClient, redisTemplate, enabled, SECRET, 900, "2");
    }

    @Test
    void exchangesVerifiedIdentityForSignedRevocableToken() {
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        DigitalCampusProfile.Identity identity = identity();
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity, 900L, "77"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchange("external-token", "identity-1");

        ClassroomTokenCodec.VerifiedToken verified = ClassroomTokenCodec.verify(
                result.accessToken(), SECRET, Instant.now().getEpochSecond());
        assertThat(verified.claims())
                .containsEntry("userId", "external-user-1")
                .containsEntry("identityId", "900")
                .containsEntry("schoolId", "77")
                .containsEntry("externalUserId", "external-user-1")
                .containsEntry("externalIdentityId", "identity-1")
                .containsEntry("hanUserId", "100");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) verified.claims().get("roles");
        assertThat(roles).containsExactlyInAnyOrder("2", "9", "teacher");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(keys.capture(), values.capture(), any(Duration.class));
        int sessionIdx = keys.getAllValues().indexOf(ClassroomTokenCodec.SESSION_KEY_PREFIX + verified.tokenId());
        assertThat(sessionIdx).as("Session Key 必须写入").isGreaterThanOrEqualTo(0);
        // Session Key 的 value 写 Han userId，不混用外部/内部 ID。
        assertThat(values.getAllValues().get(sessionIdx)).isEqualTo("100");
    }

    @Test
    void digitalCampusClaimsContainLocalAndExternalIdentityIds() {
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity(), 900L, "77"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchange("external-token", "identity-1");

        var claims = ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims();
        assertThat(claims)
                .containsEntry("hanUserId", "100")
                .containsEntry("identityId", "900")
                .containsEntry("externalUserId", "external-user-1")
                .containsEntry("externalIdentityId", "identity-1")
                .containsEntry("schoolId", "77");
    }

    @Test
    void digitalCampusClassroomKeyUsesHanUserAndLocalPersonId() {
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity(), 900L, "77"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchange("external-token", "identity-1");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(keys.capture(), values.capture(), any(Duration.class));
        List<String> keyList = keys.getAllValues();
        List<String> valueList = values.getAllValues();

        String identityKey = ClassroomTokenCodec.activeIdentityKey("100", "900");
        assertThat(keyList)
                .as("数字校园课堂凭证的身份级 Active Key 使用 active:{hanUserId}:{localIdentityId}")
                .contains(identityKey);
        assertThat(valueList.get(keyList.indexOf(identityKey)))
                .as("身份级 Active Key 的值就是该身份的凭证原文")
                .isEqualTo(result.accessToken());
        assertThat(keyList)
                .as("账号级 Active Key 保留作旧版兼容索引")
                .contains(ClassroomTokenCodec.activeKey("100"));
    }

    @Test
    void pinsUserTypeToTheValueTheLegacyPortalFilterRequires() {
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        when(loginService.synchronize("external-token", "identity-1"))
                .thenReturn(new DigitalCampusLoginService.SynchronizedIdentity(user, identity(), 900L, "77"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchange("external-token", "identity-1");

        assertThat(ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims())
                .containsEntry("userType", "USER")
                .containsEntry("roleType", "2");
    }

    @Test
    void issuesFromHanLoginSessionWithoutAnyExternalIdentityLookup() {
        when(systemServiceClient.getClassroomIdentity(100L)).thenReturn(R.ok(ClassroomIdentityVO.builder()
                .userId("100").identityId("11").userName("Teacher One").roleType("2")
                .schoolId("7").status(0).roles(List.of("2", "TEACHER")).build()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchangeLocal(
                LoginUser.builder().userId(100L).tenantId(1L).username("teacher01").build());

        var claims = ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims();
        assertThat(claims)
                .containsEntry("userId", "100")
                .containsEntry("identityId", "11")
                .containsEntry("userType", "USER");
        assertThat(((Number) claims.get("tenantId")).longValue()).isEqualTo(1L);
        verifyNoInteractions(loginService);
    }

    @Test
    void issuesTheExplicitlySelectedIdentityOwnedByCurrentHanUser() {
        when(systemServiceClient.getClassroomIdentity(100L, "12")).thenReturn(R.ok(ClassroomIdentityVO.builder()
                .userId("100").identityId("12").userName("Teacher Two").roleType("2")
                .schoolId("8").status(0).roles(List.of("2", "TEACHER")).build()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service(true).exchangeLocal(
                LoginUser.builder().userId(100L).tenantId(1L).username("teacher01").build(), "12");

        assertThat(ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims())
                .containsEntry("identityId", "12")
                .containsEntry("schoolId", "8");
    }

    @Test
    void listsAllCurrentUserEducationIdentitiesBeforeSelection() {
        List<ClassroomIdentityVO> identities = List.of(
                ClassroomIdentityVO.builder().identityId("11").personType("TEACHER").loginAllowed(true).build(),
                ClassroomIdentityVO.builder().identityId("21").personType("STUDENT").loginAllowed(false).build());
        when(systemServiceClient.listClassroomIdentities(100L)).thenReturn(R.ok(identities));

        assertThat(service(true).listLocalIdentities(
                LoginUser.builder().userId(100L).tenantId(1L).username("teacher01").build()))
                .extracting(ClassroomIdentityVO::getIdentityId)
                .containsExactly("11", "21");
    }

    @Test
    void refusesLocalExchangeForNonTeacherIdentitiesThisPhase() {
        when(systemServiceClient.getClassroomIdentity(200L)).thenReturn(R.ok(ClassroomIdentityVO.builder()
                .userId("200").identityId("21").userName("Student One").roleType("4")
                .schoolId("7").status(0).roles(List.of("4", "STUDENT")).build()));

        assertThatThrownBy(() -> service(true).exchangeLocal(
                LoginUser.builder().userId(200L).tenantId(1L).username("student01").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ClassroomTokenService.NON_TEACHER_LOGIN_UNSUPPORTED);
    }

    @Test
    void issuesStudentTokenWhenTheStudentRoleIsEnabledAndCarriesItsClassScope() {
        ClassroomTokenService studentEnabled = new ClassroomTokenService(
                loginService, systemServiceClient, redisTemplate, true, SECRET, 900, "2,4");
        when(systemServiceClient.getClassroomIdentity(200L)).thenReturn(R.ok(ClassroomIdentityVO.builder()
                .userId("200").identityId("21").userName("Student One").roleType("4")
                .schoolId("7").status(0).classIds(List.of("class-1")).roles(List.of("4", "STUDENT")).build()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = studentEnabled.exchangeLocal(
                LoginUser.builder().userId(200L).tenantId(1L).username("student01").build());

        assertThat(ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims())
                .containsEntry("roleType", "4")
                .containsEntry("classIds", List.of("class-1"));
    }

    @Test
    void refusesLocalExchangeWithoutAnEducationIdentityOrLoginSession() {
        when(systemServiceClient.getClassroomIdentity(300L)).thenReturn(R.ok(null));

        assertThatThrownBy(() -> service(true).exchangeLocal(
                LoginUser.builder().userId(300L).tenantId(1L).username("plain").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号未开通三个课堂身份");
        assertThatThrownBy(() -> service(true).exchangeLocal(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service(true).exchangeLocal(
                LoginUser.builder().userId(301L).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前没有可用的 Han 登录态");
    }

    @Test
    void refusesToIssueWhenFeatureIsDisabled() {
        assertThatThrownBy(() -> service(false).exchange("external-token", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service(false).exchangeLocal(
                LoginUser.builder().userId(100L).build()))
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
