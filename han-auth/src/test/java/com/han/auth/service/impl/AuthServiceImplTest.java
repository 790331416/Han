package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.api.tenant.domain.TenantVO;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

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
    void getMyTenantsMarksCurrentTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(1L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .build());
        when(systemServiceClient.getUserTenants("admin")).thenReturn(R.ok(List.of(
                Map.of("tenantId", 1L, "status", 0),
                Map.of("tenantId", 2L, "status", 0)
        )));
        TenantVO first = tenant(1L, "main", 0);
        TenantVO second = tenant(2L, "tenant2", 0);
        when(tenantServiceClient.listAllValidTenants()).thenReturn(R.ok(List.of(first, second)));

        List<TenantSimpleVo> tenants = authService.getMyTenants();

        assertThat(tenants).hasSize(2);
        assertThat(tenants.get(0).isCurrent()).isTrue();
        assertThat(tenants.get(1).isCurrent()).isFalse();
    }

    @Test
    void switchTenantIssuesNewTokenForTargetTenantUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(1L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemServiceClient.getUserTenants("admin")).thenReturn(R.ok(List.of(
                Map.of("tenantId", 2L, "status", 0)
        )));
        when(systemServiceClient.listClassroomIdentities(2L)).thenReturn(R.ok(List.of()));
        when(tenantServiceClient.checkTenantValid(2L)).thenReturn(R.ok(true));
        when(systemServiceClient.getUserByUsername("admin", 2L)).thenReturn(R.ok(user(2L, 2L)));
        when(systemServiceClient.getPermissionsByUserId(2L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(2L)).thenReturn(R.ok(Set.of(10L)));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        LoginVO result = authService.switchTenant(2L, "Bearer old-token");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.userInfo().userId()).isEqualTo(2L);
        assertThat(result.userInfo().username()).isEqualTo("admin");
    }

    @Test
    void switchTenantSkipsTenantServiceWhenTargetIsCurrentTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(1L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemServiceClient.getUserTenants("admin")).thenReturn(R.ok(List.of(
                Map.of("tenantId", 1L, "status", 0)
        )));
        when(systemServiceClient.listClassroomIdentities(1L)).thenReturn(R.ok(List.of()));
        when(systemServiceClient.getUserByUsername("admin", 1L)).thenReturn(R.ok(user(1L, 1L)));
        when(systemServiceClient.getPermissionsByUserId(1L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(1L)).thenReturn(R.ok(Set.of(10L)));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        LoginVO result = authService.switchTenant(1L, "Bearer old-token");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.userInfo().userId()).isEqualTo(1L);
        verify(tenantServiceClient, never()).checkTenantValid(anyLong());
    }

    @Test
    void switchTenantReportsBusinessErrorWhenTenantServiceUnavailable() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(1L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .build());
        when(systemServiceClient.getUserTenants("admin")).thenReturn(R.ok(List.of(
                Map.of("tenantId", 2L, "status", 0)
        )));
        when(tenantServiceClient.checkTenantValid(2L)).thenThrow(new IllegalStateException("No instances available"));

        assertThatThrownBy(() -> authService.switchTenant(2L, "Bearer old-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户服务不可用");
    }

    @Test
    void requiresCaptchaForH5WhenCaptchaIsEnabled() {
        CaptchaSettingService captchaSettingService = mock(CaptchaSettingService.class);
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(true);
        AuthServiceImpl service = new AuthServiceImpl(
                redisTemplate,
                systemServiceClient,
                tenantServiceClient,
                new SecurityProperties(),
                mock(TotpService.class),
                captchaSettingService
        );
        LoginDTO dto = new LoginDTO();
        dto.setUsername("u_13900000001");
        dto.setPassword("not-used");
        dto.setClientType(ClientType.H5);

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码不能为空");
        verify(systemServiceClient, never()).getUserByUsername(anyString());
    }

    @Test
    void rejectsManagementLoginWithoutManagementRole() {
        UserVO user = user(9L, 1L);
        user.setRoleKeys(Set.of("teacher"));

        assertThatThrownBy(() -> authService.issueLoginForUser(user, ClientType.PC, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置管理端权限");
    }

    @Test
    void allowsManagementLoginWithConfiguredRole() {
        UserVO user = user(9L, 1L);
        user.setRoleKeys(Set.of("common"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        assertThat(authService.issueLoginForUser(user, ClientType.PC, false).accessToken()).isNotBlank();
        verify(valueOperations).set(startsWith(CacheConstants.ONLINE_KEY), anyString(), eq(Duration.ofMinutes(5)));
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

    private TenantVO tenant(Long tenantId, String tenantName, Integer status) {
        TenantVO tenant = new TenantVO();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(tenantName);
        tenant.setStatus(status);
        return tenant;
    }
}
