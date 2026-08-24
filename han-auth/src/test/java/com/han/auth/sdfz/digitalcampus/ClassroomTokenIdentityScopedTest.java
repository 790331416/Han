package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
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

/**
 * identityScoped 登录态的课堂 Token 换发与身份级 Active Key 写入。
 *
 * <p>身份隔离账号的登录态在签发时就绑定了 {@code LoginUser.identityId}，
 * 本地换发课堂凭证只能落到该身份上，不能拿 A 会话去套 B 身份的凭证；
 * 同一账号不同身份的凭证必须按身份粒度各持一张 Active Key。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassroomTokenIdentityScopedTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private DigitalCampusLoginService loginService;
    @Mock
    private SystemServiceClient systemServiceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ClassroomTokenService service() {
        return new ClassroomTokenService(
                loginService, systemServiceClient, redisTemplate, true, SECRET, 900, "2");
    }

    @Test
    void identityScopedSessionRejectsAnotherIdentityId() {
        LoginUser scoped = LoginUser.builder().userId(100L).tenantId(1L).username("teacher01")
                .identityScoped(true).identityId(11L).build();

        assertThatThrownBy(() -> service().exchangeLocal(scoped, "12"))
                .as("identityScoped 登录态不能用 A 会话传 B 身份去换 B 的课堂凭证")
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前会话身份不匹配，请重新登录");
        verifyNoInteractions(systemServiceClient);
    }

    @Test
    void identityScopedSessionDefaultsToItsOwnIdentity() {
        LoginUser scoped = LoginUser.builder().userId(100L).tenantId(1L).username("teacher01")
                .identityScoped(true).identityId(11L).build();
        when(systemServiceClient.getClassroomIdentity(100L, "11"))
                .thenReturn(R.ok(ClassroomIdentityVO.builder()
                        .userId("100").identityId("11").userName("Teacher One").roleType("2")
                        .schoolId("7").status(0).roles(List.of("2", "TEACHER")).build()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service().exchangeLocal(scoped, null);

        assertThat(ClassroomTokenCodec.verify(result.accessToken(), SECRET,
                Instant.now().getEpochSecond()).claims())
                .containsEntry("identityId", "11");
        verify(systemServiceClient).getClassroomIdentity(100L, "11");
    }

    @Test
    void localExchangeWritesTheIdentityScopedActiveKey() {
        LoginUser scoped = LoginUser.builder().userId(100L).tenantId(1L).username("teacher01")
                .identityScoped(true).identityId(11L).build();
        when(systemServiceClient.getClassroomIdentity(100L, "11"))
                .thenReturn(R.ok(ClassroomIdentityVO.builder()
                        .userId("100").identityId("11").userName("Teacher One").roleType("2")
                        .schoolId("7").status(0).roles(List.of("2", "TEACHER")).build()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ClassroomTokenVO result = service().exchangeLocal(scoped, null);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(keys.capture(), values.capture(), any(Duration.class));
        List<String> keyList = keys.getAllValues();
        List<String> valueList = values.getAllValues();

        String identityKey = ClassroomTokenCodec.activeIdentityKey("100", "11");
        assertThat(keyList)
                .as("本地换发必须写身份级 Active Key")
                .contains(identityKey);
        assertThat(valueList.get(keyList.indexOf(identityKey)))
                .as("身份级 Active Key 的值就是该身份的凭证原文")
                .isEqualTo(result.accessToken());
        assertThat(keyList)
                .as("账号级 Active Key 保留作旧版兼容索引")
                .contains(ClassroomTokenCodec.activeKey("100"));
    }
}
