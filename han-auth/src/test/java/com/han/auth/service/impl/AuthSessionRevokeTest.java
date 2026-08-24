package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.tenant.TenantServiceClient;
import com.han.auth.config.SecurityProperties;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话撤销（han-auth 内部接口）行为测试。
 *
 * <p>identityId 为空撤销该账号全部客户端会话与课堂凭证；指定时只撤销该教育身份对应的会话与课堂 token。
 */
class AuthSessionRevokeTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            redisTemplate,
            mock(SystemServiceClient.class),
            mock(TenantServiceClient.class),
            new SecurityProperties(),
            mock(TotpService.class),
            mock(CaptchaSettingService.class)
    );

    @Test
    void revokeAllSessionsDeletesTokensForEveryClientType() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Long userId = 9L;
        for (ClientType clientType : ClientType.values()) {
            String userKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode();
            when(valueOperations.get(userKey)).thenReturn("token-" + clientType.getCode());
        }

        authService.revokeSession(userId, null);

        for (ClientType clientType : ClientType.values()) {
            String userKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode();
            String accessToken = "token-" + clientType.getCode();
            verify(redisTemplate).delete(userKey);
            verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + accessToken);
            verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + accessToken);
        }
        // 账号级课堂凭证一并撤销
        verify(redisTemplate).delete(ClassroomTokenCodec.ACTIVE_KEY_PREFIX + userId);
    }

    @Test
    void revokeByIdentityOnlyDeletesMatchingIdentitySession() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Long userId = 9L;
        String pcKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + ClientType.PC.getCode();
        String h5Key = CacheConstants.LOGIN_USER_KEY + userId + ":" + ClientType.H5.getCode();
        when(valueOperations.get(pcKey)).thenReturn("token-pc");
        when(valueOperations.get(h5Key)).thenReturn("token-h5");
        when(valueOperations.get(CacheConstants.TOKEN_KEY + "token-pc")).thenReturn(
                XuJsonUtil.toJsonString(LoginUser.builder().userId(userId).identityScoped(true).identityId(100L).build()));
        when(valueOperations.get(CacheConstants.TOKEN_KEY + "token-h5")).thenReturn(
                XuJsonUtil.toJsonString(LoginUser.builder().userId(userId).identityScoped(true).identityId(101L).build()));

        authService.revokeSession(userId, 100L);

        verify(redisTemplate).delete(pcKey);
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "token-pc");
        verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + "token-pc");
        // 其他身份会话不动
        verify(redisTemplate, never()).delete(h5Key);
        verify(redisTemplate, never()).delete(CacheConstants.TOKEN_KEY + "token-h5");
        // 身份粒度课堂凭证撤销
        verify(redisTemplate).delete(ClassroomTokenCodec.activeIdentityKey(String.valueOf(userId), "100"));
    }

    @Test
    void revokeSessionRejectsNullUserId() {
        assertThatThrownBy(() -> authService.revokeSession(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户ID不能为空");
    }
}
