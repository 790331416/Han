package com.han.system.sdfz.compat;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomAesCodec;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.PasswordUtil;
import com.han.system.domain.po.SysUserPo;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyCredentialServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ANONYMOUS_KEY = "1234123412ABCDEF";
    private static final String ANONYMOUS_IV = "ABCDEF1234123412";
    private static final String RAW_PASSWORD = "Sdfz@Compat1";

    @Mock
    private LegacyDirectoryService directoryService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LegacyCompatProperties properties;
    private LegacyTokenIssuer tokenIssuer;
    private LegacyCredentialService service;

    @BeforeEach
    void setUp() {
        properties = new LegacyCompatProperties();
        properties.setEnabled(true);
        properties.setTenantId(1L);
        properties.setTokenSecret(SECRET);
        properties.setAnonymousKey(ANONYMOUS_KEY);
        properties.setAnonymousIv(ANONYMOUS_IV);
        properties.setCaptchaEnabled(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        tokenIssuer = new LegacyTokenIssuer(properties, redisTemplate);
        service = new LegacyCredentialService(properties, directoryService, tokenIssuer, redisTemplate,
                new LegacyCipher(properties));
    }

    // ------------------------------------------------------------ C2 登录

    @Test
    void issuesAnInterimTokenForATeacherWithMatchingPassword() {
        SysUserPo user = user(100L, 0);
        when(directoryService.userByLoginName("teacher01")).thenReturn(user);
        when(directoryService.personByUserId(100L)).thenReturn(person(11L, 100L, LegacyDirectoryService.TEACHER, 0));

        Map<String, Object> result = asMap(service.login(loginRequest("teacher01", RAW_PASSWORD)).value());

        String token = (String) result.get("token");
        assertThat(ClassroomAesCodec.canDeriveKey(token)).isTrue();
        assertThat(ClassroomTokenCodec.verify(token, SECRET, java.time.Instant.now().getEpochSecond())
                .claims())
                .containsEntry("userType", "USER")
                .containsEntry("roleType", "2")
                .containsEntry("hanUserId", "100");
    }

    @Test
    void acceptsTheLegacyUiDoubleEncryptedCredentials() {
        SysUserPo user = user(100L, 0);
        when(directoryService.userByLoginName("teacher01")).thenReturn(user);
        when(directoryService.personByUserId(100L)).thenReturn(person(11L, 100L, LegacyDirectoryService.TEACHER, 0));
        LegacyCipher cipher = new LegacyCipher(properties);
        LegacyRequest request = new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, null, LegacyPaths.UI_LOGIN,
                Map.of("phone", cipher.encrypt("teacher01", null), "password", cipher.encrypt(RAW_PASSWORD, null)));

        assertThat(asMap(service.login(request).value())).containsKey("token");
    }

    @Test
    void issuesCredentialsForStudentsButLeavesBusinessEndpointRestrictionsToTheGateway() {
        when(directoryService.userByLoginName("student01")).thenReturn(user(200L, 0));
        when(directoryService.personByUserId(200L))
                .thenReturn(person(21L, 200L, LegacyDirectoryService.STUDENT, 0));

        Map<String, Object> result = asMap(service.login(loginRequest("student01", RAW_PASSWORD)).value());

        assertThat(result.get("token")).isInstanceOf(String.class);
    }

    @Test
    void rejectsAWrongPasswordWithTheSameMessageAsAnUnknownAccount() {
        when(directoryService.userByLoginName("teacher01")).thenReturn(user(100L, 0));
        when(directoryService.userByLoginName("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.login(loginRequest("teacher01", "WrongPass1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> service.login(loginRequest("ghost", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void rejectsDisabledHanAccountsAndDisabledEducationIdentities() {
        when(directoryService.userByLoginName("disabled")).thenReturn(user(100L, 1));
        when(directoryService.personByUserId(100L)).thenReturn(person(11L, 100L, LegacyDirectoryService.TEACHER, 0));

        assertThatThrownBy(() -> service.login(loginRequest("disabled", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已停用，请联系管理员");

        when(directoryService.userByLoginName("suspended")).thenReturn(user(101L, 0));
        when(directoryService.personByUserId(101L)).thenReturn(person(12L, 101L, LegacyDirectoryService.TEACHER, 1));

        assertThatThrownBy(() -> service.login(loginRequest("suspended", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号的三个课堂身份已停用");

        when(directoryService.userByLoginName("missingStatus")).thenReturn(user(102L, 0));
        when(directoryService.personByUserId(102L))
                .thenReturn(person(13L, 102L, LegacyDirectoryService.TEACHER, null));

        assertThatThrownBy(() -> service.login(loginRequest("missingStatus", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号的三个课堂身份已停用");
    }

    @Test
    void rejectsAccountsWithoutAnEducationIdentity() {
        when(directoryService.userByLoginName("plain")).thenReturn(user(100L, 0));
        when(directoryService.personByUserId(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.login(loginRequest("plain", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号未开通三个课堂身份");
    }

    @Test
    void locksTheAccountAfterTooManyFailures() {
        when(directoryService.userByLoginName("teacher01")).thenReturn(user(100L, 0));
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("sdfz:compat:login-fail:")))
                .thenReturn("5");

        assertThatThrownBy(() -> service.login(loginRequest("teacher01", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录失败次数过多，请稍后再试");
    }

    @Test
    void validatesTheCaptchaOnceAndRejectsAnExpiredOne() {
        properties.setCaptchaEnabled(true);
        when(directoryService.userByLoginName("teacher01")).thenReturn(user(100L, 0));
        when(directoryService.personByUserId(100L)).thenReturn(person(11L, 100L, LegacyDirectoryService.TEACHER, 0));
        when(valueOperations.get("sdfz:compat:captcha:1700000000000")).thenReturn(null);

        assertThatThrownBy(() -> service.login(
                loginRequest("teacher01", RAW_PASSWORD, "abcd", "1700000000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码已过期");

        when(valueOperations.get("sdfz:compat:captcha:1700000000000")).thenReturn("ABCD");
        assertThat(asMap(service.login(
                loginRequest("teacher01", RAW_PASSWORD, "abcd", "1700000000000")).value()))
                .containsKey("token");
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).delete("sdfz:compat:captcha:1700000000000");
    }

    @Test
    void rejectsCaptchaKeysThatCouldEscapeTheRedisNamespace() {
        assertThatThrownBy(() -> service.captcha("../../etc"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void captchaUsesTheZeroCodeTheLegacyFrontendChecksFor() {
        LegacyPayload payload = service.captcha("1700000000000");

        assertThat(payload.uiCode()).isEqualTo(LegacyPayload.UI_CAPTCHA_OK);
        assertThat((String) payload.value()).startsWith("data:image");
    }

    // ------------------------------------------------------------ C3 当前用户

    @Test
    void buildsRolesAndIssuesTheSessionTokenTheFrontendWillAdopt() {
        EduPersonPo teacher = person(11L, 100L, LegacyDirectoryService.TEACHER, 0);
        String interim = tokenIssuer.issueInterim(teacher).token();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(teacher));
        when(directoryService.roleOf(teacher)).thenReturn(Map.of("roleType", 2, "userId", "100"));
        when(directoryService.externalUserId(teacher)).thenReturn("100");

        Map<String, Object> result = asMap(service.currentUser(currentUserRequest(interim)).value());

        assertThat((List<?>) result.get("roles")).hasSize(1);
        String accessToken = (String) result.get("accessToken");
        assertThat(accessToken).isNotEqualTo(interim);
        assertThat(ClassroomAesCodec.canDeriveKey(accessToken)).isTrue();
    }

    @Test
    void buildsAStudentSessionWhenTheAccountOnlyHasStudentIdentities() {
        EduPersonPo teacher = person(11L, 100L, LegacyDirectoryService.TEACHER, 0);
        EduPersonPo student = person(21L, 100L, LegacyDirectoryService.STUDENT, 0);
        String interim = tokenIssuer.issueInterim(teacher).token();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(student));
        when(directoryService.roleOf(student)).thenReturn(Map.of("roleType", 4, "userId", "100"));
        when(directoryService.externalUserId(student)).thenReturn("100");

        Map<String, Object> result = asMap(service.currentUser(currentUserRequest(interim)).value());

        assertThat((List<?>) result.get("roles")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) result.get("roles")).get(0)).get("roleType")).isEqualTo(4);
    }

    @Test
    void refusesWhenTheSessionWasRevoked() {
        String interim = tokenIssuer.issueInterim(person(11L, 100L, LegacyDirectoryService.TEACHER, 0)).token();
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.currentUser(currentUserRequest(interim)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void refusesWhenNoTokenWasPresented() {
        assertThatThrownBy(() -> service.currentUser(currentUserRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    // ------------------------------------------------------------ 夹具

    private static LegacyRequest loginRequest(String loginName, String password) {
        return loginRequest(loginName, password, null, null);
    }

    private static LegacyRequest loginRequest(String loginName, String password,
                                              String captcha, String checkKey) {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("phone", loginName);
        params.put("password", password);
        if (captcha != null) {
            params.put("captcha", captcha);
            params.put("checkKey", checkKey);
        }
        return new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, null, LegacyPaths.UI_LOGIN, params);
    }

    private static LegacyRequest currentUserRequest(String token) {
        return new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, token,
                LegacyPaths.UI_GET_ONE_BY_ID, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static SysUserPo user(Long id, Integer status) {
        SysUserPo user = new SysUserPo();
        user.setId(id);
        user.setUsername("teacher01");
        user.setNickname("张老师");
        user.setPassword(PasswordUtil.encode(RAW_PASSWORD));
        user.setStatus(status);
        user.setDelFlag(0);
        return user;
    }

    private static EduPersonPo person(Long id, Long userId, String personType, Integer status) {
        EduPersonPo person = new EduPersonPo();
        person.setId(id);
        person.setUserId(userId);
        person.setSchoolId(7L);
        person.setPersonName("张老师");
        person.setPersonType(personType);
        person.setStatus(status);
        person.setDelFlag(0);
        return person;
    }
}
