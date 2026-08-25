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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话撤销（han-auth 内部接口）与登出行为测试。
 *
 * <p>撤销以会话索引 Set（{@code auth:sessions:user:{userId}} / {@code auth:sessions:identity:{userId}:{identityId}}）
 * 为唯一依据，不再只遍历 login_user 读最后一枚 token；身份粒度只撤该身份，账号粒度撤全部客户端与课堂凭证。
 */
class AuthSessionRevokeTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            redisTemplate,
            mock(SystemServiceClient.class),
            mock(TenantServiceClient.class),
            new SecurityProperties(),
            mock(TotpService.class),
            mock(CaptchaSettingService.class)
    );

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void revokeIdentityDeletesAllH5SessionsOfThatIdentity() {
        Long userId = 9L;
        String identitySetKey = CacheConstants.SESSION_IDENTITY_KEY + userId + ":100";
        when(setOperations.members(identitySetKey)).thenReturn(Set.of("token-h5-1", "token-h5-2"));

        authService.revokeSession(userId, 100L);

        // 该身份下两个 H5 会话 token 全部删除
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "token-h5-1");
        verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + "token-h5-1");
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "token-h5-2");
        verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + "token-h5-2");
        // 从 user 会话 Set 移除
        verify(setOperations).remove(CacheConstants.SESSION_USER_KEY + userId, "token-h5-1");
        verify(setOperations).remove(CacheConstants.SESSION_USER_KEY + userId, "token-h5-2");
        // 删 identity Set 与 user identities Set 中的 identityId
        verify(redisTemplate).delete(identitySetKey);
        verify(setOperations).remove(CacheConstants.IDENTITIES_USER_KEY + userId, "100");
        // 身份课堂 Active Key 撤销
        verify(redisTemplate).delete(ClassroomTokenCodec.activeIdentityKey(String.valueOf(userId), "100"));
    }

    @Test
    void revokeIdentityKeepsOtherIdentitySessions() {
        Long userId = 9L;
        String identityA = CacheConstants.SESSION_IDENTITY_KEY + userId + ":100";
        String identityB = CacheConstants.SESSION_IDENTITY_KEY + userId + ":101";
        when(setOperations.members(identityA)).thenReturn(Set.of("token-a"));
        when(setOperations.members(identityB)).thenReturn(Set.of("token-b"));

        authService.revokeSession(userId, 100L);

        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "token-a");
        verify(setOperations).remove(CacheConstants.SESSION_USER_KEY + userId, "token-a");
        verify(redisTemplate).delete(identityA);
        // 其他身份会话与课堂凭证不动
        verify(redisTemplate, never()).delete(CacheConstants.TOKEN_KEY + "token-b");
        verify(redisTemplate, never()).delete(identityB);
        verify(redisTemplate, never()).delete(ClassroomTokenCodec.activeIdentityKey(String.valueOf(userId), "101"));
    }

    @Test
    void revokeAccountDeletesAllClientSessions() {
        Long userId = 9L;
        String userSetKey = CacheConstants.SESSION_USER_KEY + userId;
        String identitiesKey = CacheConstants.IDENTITIES_USER_KEY + userId;
        when(setOperations.members(userSetKey)).thenReturn(Set.of("token-pc", "token-h5", "token-app"));
        when(setOperations.members(identitiesKey)).thenReturn(Set.of("100", "101"));

        authService.revokeSession(userId, null);

        // 全部客户端 token 删除
        for (String token : List.of("token-pc", "token-h5", "token-app")) {
            verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + token);
            verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + token);
        }
        // 所有 login_user 设备索引删除（不再作为唯一依据，但一并清理）
        for (ClientType clientType : ClientType.values()) {
            verify(redisTemplate).delete(CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode());
        }
        // 逐个删除身份会话 Set 与身份课堂 Active Key
        verify(redisTemplate).delete(CacheConstants.SESSION_IDENTITY_KEY + userId + ":100");
        verify(redisTemplate).delete(ClassroomTokenCodec.activeIdentityKey(String.valueOf(userId), "100"));
        verify(redisTemplate).delete(CacheConstants.SESSION_IDENTITY_KEY + userId + ":101");
        verify(redisTemplate).delete(ClassroomTokenCodec.activeIdentityKey(String.valueOf(userId), "101"));
        // 账号级课堂兼容 Key 与 user Set / identities Set
        verify(redisTemplate).delete(ClassroomTokenCodec.ACTIVE_KEY_PREFIX + userId);
        verify(redisTemplate).delete(userSetKey);
        verify(redisTemplate).delete(identitiesKey);
    }

    @Test
    void legacyLoginUserTokenIsDeletedDuringAccountRevoke() {
        Long userId = 9L;
        when(setOperations.members(CacheConstants.SESSION_USER_KEY + userId)).thenReturn(Set.of());
        when(setOperations.members(CacheConstants.IDENTITIES_USER_KEY + userId)).thenReturn(Set.of());
        String legacyKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + ClientType.PC.getCode();
        // 部署前签发、未进新会话 Set 的旧 access token 只存在于 login_user 旧索引里。
        when(valueOperations.get(legacyKey)).thenReturn("legacy-token");

        authService.revokeSession(userId, null);

        // 账号级撤销通过遍历 ClientType 的 login_user 旧索引把旧 access token 一并删除。
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "legacy-token");
        verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + "legacy-token");
        verify(redisTemplate).delete(legacyKey);
    }

    @Test
    void logoutOnlyDeletesCurrentToken() {
        String token = "token-current";
        LoginUser loginUser = LoginUser.builder().userId(9L).tenantId(1L).username("admin")
                .clientType(ClientType.H5).identityScoped(false).build();
        when(valueOperations.get(CacheConstants.TOKEN_KEY + token)).thenReturn(XuJsonUtil.toJsonString(loginUser));
        // login_user 索引当前指向更新的一枚 token（同端重登），注销旧 token 不得删除该索引
        String userKey = CacheConstants.LOGIN_USER_KEY + 9L + ":" + ClientType.H5.getCode();
        when(valueOperations.get(userKey)).thenReturn("token-newer");

        authService.logout(token);

        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + token);
        verify(redisTemplate).delete(CacheConstants.ONLINE_KEY + token);
        verify(setOperations).remove(CacheConstants.SESSION_USER_KEY + 9L, token);
        // login_user 索引值不等于当前 token，不删
        verify(redisTemplate, never()).delete(userKey);
        // 非身份会话注销时撤账号级课堂兼容凭证
        verify(redisTemplate).delete(ClassroomTokenCodec.ACTIVE_KEY_PREFIX + 9L);
    }

    @Test
    void logoutIdentityADoesNotRevokeIdentityBClassroomToken() {
        String token = "token-identity-a";
        LoginUser loginUser = LoginUser.builder().userId(9L).tenantId(1L).username("admin")
                .clientType(ClientType.H5).identityScoped(true).identityId(100L).build();
        when(valueOperations.get(CacheConstants.TOKEN_KEY + token)).thenReturn(XuJsonUtil.toJsonString(loginUser));

        authService.logout(token);

        // 只撤当前身份 A 的课堂凭证，不误撤身份 B
        verify(redisTemplate).delete(ClassroomTokenCodec.activeIdentityKey("9", "100"));
        verify(redisTemplate, never()).delete(ClassroomTokenCodec.activeIdentityKey("9", "101"));
        // 身份会话不撤账号级课堂兼容凭证
        verify(redisTemplate, never()).delete(ClassroomTokenCodec.ACTIVE_KEY_PREFIX + 9L);
        // 从 identity Set 移除当前 token
        verify(setOperations).remove(CacheConstants.SESSION_IDENTITY_KEY + 9L + ":100", token);
    }

    @Test
    void logoutDeletesLoginUserIndexOnlyWhenItPointsToCurrentToken() {
        String token = "token-current";
        LoginUser loginUser = LoginUser.builder().userId(9L).tenantId(1L).username("admin")
                .clientType(ClientType.H5).identityScoped(false).build();
        when(valueOperations.get(CacheConstants.TOKEN_KEY + token)).thenReturn(XuJsonUtil.toJsonString(loginUser));
        String userKey = CacheConstants.LOGIN_USER_KEY + 9L + ":" + ClientType.H5.getCode();
        when(valueOperations.get(userKey)).thenReturn(token);

        authService.logout(token);

        verify(redisTemplate, times(1)).delete(userKey);
    }

    @Test
    void revokeSessionRejectsNullUserId() {
        assertThatThrownBy(() -> authService.revokeSession(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户ID不能为空");
    }
}
