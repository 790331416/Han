package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh 重新计算身份权限的回归测试。
 *
 * <p>刷新必须重查账号 + 当前身份 + 人员/离校/学校/岗位/角色并重建完整 LoginUser，
 * 不复用旧权限；身份失效删旧 access/refresh token 并 401；岗位从 SCHOOL_ADMIN 降为 TEACHER
 * 后不得再刷新出旧管理员权限。
 */
class AuthServiceRefreshIdentityTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SystemServiceClient systemServiceClient = mock(SystemServiceClient.class);
    private final TenantServiceClient tenantServiceClient = mock(TenantServiceClient.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            redisTemplate,
            systemServiceClient,
            tenantServiceClient,
            new SecurityProperties(),
            mock(TotpService.class),
            mock(CaptchaSettingService.class)
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void refreshRebuildsPermissionsForCurrentIdentity() {
        String refreshToken = "refresh-1";
        String oldAccessToken = "old-access";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stubRefreshPair(refreshToken, oldAccessToken, LoginUser.builder()
                .userId(9L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .identityScoped(true)
                .identityId(100L)
                .dutyCode("SCHOOL_ADMIN")
                .permissions(Set.of("stale:perm"))
                .build());

        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "SCHOOL_ADMIN", "张三", true, ""))));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.getRolesByUserId(9L)).thenReturn(R.ok(List.of(role(500L, "common"))));

        LoginVO result = authService.refreshToken(refreshToken);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isTrue();
        assertThat(stored.getIdentityId()).isEqualTo(100L);
        assertThat(stored.getDutyCode()).isEqualTo("SCHOOL_ADMIN");
        assertThat(stored.getRoleIds()).containsExactly(500L);
        assertThat(stored.getRoleKeys()).containsExactly("common");
        // 权限按当前身份重新加载，不复用旧会话里的 stale:perm
        assertThat(stored.getPermissions()).containsExactly("system:user:list");
    }

    @Test
    void refreshDropsAdminPermissionsWhenDutyDowngradedToTeacher() {
        String refreshToken = "refresh-2";
        String oldAccessToken = "old-access-2";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stubRefreshPair(refreshToken, oldAccessToken, LoginUser.builder()
                .userId(9L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .identityScoped(true)
                .identityId(100L)
                .dutyCode("SCHOOL_ADMIN")
                .roleIds(Set.of(500L))
                .roleKeys(Set.of("common"))
                .permissions(Set.of("system:user:list"))
                .build());

        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        // 同一身份岗位已从 SCHOOL_ADMIN 降为 TEACHER
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三", false, "当前岗位未开通管理端"))));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.getRolesByUserId(9L)).thenReturn(R.ok(List.of(role(500L, "common"))));

        LoginVO result = authService.refreshToken(refreshToken);

        assertThat(result.accessToken()).isNotBlank();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.getIdentityId()).isEqualTo(100L);
        assertThat(stored.getDutyCode()).isEqualTo("TEACHER");
        // 教师身份不继承账号管理角色与权限，旧管理员权限不得被刷新出来
        assertThat(stored.getRoleIds()).isEmpty();
        assertThat(stored.getRoleKeys()).isEmpty();
        assertThat(stored.getPermissions()).isEmpty();
    }

    @Test
    void refreshReturns401WhenIdentityInvalidated() {
        String refreshToken = "refresh-3";
        String oldAccessToken = "old-access-3";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stubRefreshPair(refreshToken, oldAccessToken, LoginUser.builder()
                .userId(9L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .identityScoped(true)
                .identityId(100L)
                .build());

        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of()));

        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("当前身份已失效");

        // 身份失效时删除旧 access/refresh token，禁止继续持有旧权限
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + oldAccessToken);
        verify(redisTemplate).delete(CacheConstants.REFRESH_TOKEN_KEY + refreshToken);
    }

    private void stubRefreshPair(String refreshToken, String oldAccessToken, LoginUser oldLoginUser) {
        when(valueOperations.get(CacheConstants.REFRESH_TOKEN_KEY + refreshToken)).thenReturn(oldAccessToken);
        when(valueOperations.get(CacheConstants.TOKEN_KEY + oldAccessToken))
                .thenReturn(XuJsonUtil.toJsonString(oldLoginUser));
    }

    private LoginUser capturedTokenUser() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(startsWith(CacheConstants.TOKEN_KEY), captor.capture(), any(Duration.class));
        return XuJsonUtil.parseObject(captor.getValue(), LoginUser.class);
    }

    private UserVO user(Long userId, Long tenantId) {
        UserVO user = new UserVO();
        user.setUserId(userId);
        user.setTenantId(tenantId);
        user.setUsername("admin");
        user.setNickname("Admin");
        user.setStatus(0);
        user.setRoleKeys(Set.of("common"));
        return user;
    }

    private ClassroomIdentityVO identity(String identityId, String schoolId, String schoolName,
                                         String personType, String dutyCode, String userName,
                                         boolean managementAvailable, String managementUnavailableReason) {
        return ClassroomIdentityVO.builder()
                .identityId(identityId)
                .schoolId(schoolId)
                .schoolName(schoolName)
                .personType(personType)
                .dutyCode(dutyCode)
                .userName(userName)
                .managementAvailable(managementAvailable)
                .managementUnavailableReason(managementUnavailableReason)
                .build();
    }

    private RoleVO role(Long roleId, String roleKey) {
        RoleVO role = new RoleVO();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        role.setStatus(0);
        return role;
    }
}
