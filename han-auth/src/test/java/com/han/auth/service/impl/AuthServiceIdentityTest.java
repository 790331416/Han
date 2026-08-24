package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.IdentitySelectDTO;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「一账号、多学校身份、按身份隔离」后端身份模型测试。
 *
 * <p>覆盖：0 身份原流程、1 身份自动绑定、≥2 身份返回 requireIdentity + 一次性票据且不签发 Token、
 * 票据一次性消费、身份切换换发 Token 并作废旧 Token。
 */
class AuthServiceIdentityTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final SystemServiceClient systemServiceClient = mock(SystemServiceClient.class);
    private final TenantServiceClient tenantServiceClient = mock(TenantServiceClient.class);
    private final CaptchaSettingService captchaSettingService = mock(CaptchaSettingService.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            redisTemplate,
            systemServiceClient,
            tenantServiceClient,
            new SecurityProperties(),
            mock(TotpService.class),
            captchaSettingService
    );

    private static final String RAW_PASSWORD = "Passw0rd!";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void loginWithoutIdentitiesFallsBackToOriginalFlow() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of()));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        LoginVO result = authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.requireIdentity()).isFalse();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isFalse();
        assertThat(stored.getIdentityId()).isNull();
    }

    @Test
    void loginWithSingleIdentityAutoBindsIdentity() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        user.setRoleKeys(Set.of("common"));
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"))));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        // 普通教师身份在 H5 课堂仍可用；PC 管理端则会被门禁拦截（见下方用例）。
        LoginVO result = authService.login(loginDto("admin", RAW_PASSWORD, ClientType.H5));

        assertThat(result.accessToken()).isNotBlank();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isTrue();
        assertThat(stored.getIdentityId()).isEqualTo(100L);
        assertThat(stored.getSchoolId()).isEqualTo(200L);
        assertThat(stored.getSchoolName()).isEqualTo("示范小学");
        assertThat(stored.getPersonType()).isEqualTo("TEACHER");
        assertThat(stored.getDutyCode()).isEqualTo("TEACHER");
        assertThat(stored.getDutyName()).isEqualTo("普通教师");
        assertThat(stored.getIdentityDisplayName()).isEqualTo("张三");
        // 非管理员身份：管理端角色与权限置空
        assertThat(stored.getRoleKeys()).isEmpty();
        assertThat(stored.getPermissions()).isEmpty();
        assertThat(stored.getRoleIds()).isEmpty();
    }

    @Test
    void loginWithMultipleIdentitiesReturnsTicketWithoutToken() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"),
                identity("101", "201", "实验小学", "TEACHER", "SCHOOL_ADMIN", "李四"))));

        LoginVO result = authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC));

        assertThat(result.requireIdentity()).isTrue();
        assertThat(result.identityTicket()).isNotBlank();
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.identities()).hasSize(2);
        assertThat(result.identities().get(0).getIdentityId()).isEqualTo(100L);
        assertThat(result.identities().get(0).getIdentityDisplayName()).isEqualTo("张三");
        assertThat(result.identities().get(1).getIdentityDisplayName()).isEqualTo("李四");
        assertThat(result.identities().get(1).getDutyName()).isEqualTo("管理员");

        // 未签发任何正式 Token，但写入了身份票据
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
        verify(valueOperations).set(
                startsWith(CacheConstants.CACHE_PREFIX + "identity_ticket:"),
                anyString(),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void identityTicketIsSingleUse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String ticket = "ticket-1";
        String ticketKey = CacheConstants.CACHE_PREFIX + "identity_ticket:" + ticket;
        String ticketJson = XuJsonUtil.toJsonString(Map.of(
                "userId", 9L,
                "tenantId", 1L,
                "clientType", "h5",
                "forceChangePassword", false));
        when(valueOperations.getAndDelete(ticketKey)).thenReturn(ticketJson, (String) null);

        UserVO user = user(9L, 1L);
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"))));
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        IdentitySelectDTO dto = new IdentitySelectDTO();
        dto.setIdentityTicket(ticket);
        dto.setIdentityId(100L);

        LoginVO first = authService.selectIdentity(dto);
        assertThat(first.accessToken()).isNotBlank();

        assertThatThrownBy(() -> authService.selectIdentity(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("身份票据已过期或已使用");
        verify(valueOperations, times(2)).getAndDelete(ticketKey);
    }

    @Test
    void switchIdentityIssuesNewTokenAndRevokesOldToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(9L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .identityScoped(true)
                .identityId(100L)
                .build());

        UserVO user = user(9L, 1L);
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"),
                identityWithManagement("101", "201", "实验小学", "TEACHER", "SCHOOL_ADMIN", "李四",
                        true, ""))));
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());
        when(valueOperations.get(CacheConstants.TOKEN_KEY + "old-token")).thenReturn(
                XuJsonUtil.toJsonString(LoginUser.builder()
                        .userId(9L)
                        .tenantId(1L)
                        .username("admin")
                        .clientType(ClientType.PC)
                        .identityScoped(true)
                        .identityId(100L)
                        .build()));

        LoginVO result = authService.switchIdentity(101L, "Bearer old-token");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        verify(redisTemplate).delete(CacheConstants.TOKEN_KEY + "old-token");

        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isTrue();
        assertThat(stored.getIdentityId()).isEqualTo(101L);
        assertThat(stored.getSchoolName()).isEqualTo("实验小学");
        assertThat(stored.getDutyName()).isEqualTo("管理员");
        assertThat(stored.getIdentityDisplayName()).isEqualTo("李四");
        // SCHOOL_ADMIN 身份保留管理端角色与权限
        assertThat(stored.getRoleKeys()).containsExactly("common");
        assertThat(stored.getPermissions()).containsExactly("system:user:list");
    }

    @Test
    void loginBlocksWhenIdentityServiceThrowsException() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("身份服务暂时不可用，请稍后重试");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void loginBlocksWhenIdentityServiceReturnsFailure() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.fail("身份查询失败"));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("身份服务暂时不可用，请稍后重试");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void educationAccountWithoutValidIdentityIsBlocked() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        user.setEducationAccount(true);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of()));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.H5)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号没有有效教育身份，请联系管理员");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void educationSchoolAdminWithCommonRoleAndNoValidIdentityIsBlocked() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        user.setRoleKeys(Set.of("common"));
        user.setEducationAccount(true);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of()));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.H5)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号没有有效教育身份，请联系管理员");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void systemAccountWithoutEducationBindingKeepsOriginalLogin() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        user.setEducationAccount(false);
        user.setEducationBound(false);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of()));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        LoginVO result = authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.requireIdentity()).isFalse();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isFalse();
        assertThat(stored.getIdentityId()).isNull();
    }

    @Test
    void pcLoginForPlainTeacherIsRejected() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        user.setRoleKeys(Set.of("teacher"));
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"))));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该身份没有管理端权限");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void pcLoginForSchoolAdminWithoutManagementRoleIsRejectedWithReason() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identityWithManagement("100", "200", "示范小学", "TEACHER", "SCHOOL_ADMIN", "张三",
                        false, "账号未配置管理端角色"))));

        assertThatThrownBy(() -> authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号未配置管理端角色");
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    @Test
    void pcLoginForSchoolAdminWithManagementRoleSucceeds() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserByUsername("admin")).thenReturn(R.ok(user));
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identityWithManagement("100", "200", "示范小学", "TEACHER", "SCHOOL_ADMIN", "张三",
                        true, ""))));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of("system:user:list")));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.getRolesByUserId(9L)).thenReturn(R.ok(List.of(role(500L, "common"))));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        LoginVO result = authService.login(loginDto("admin", RAW_PASSWORD, ClientType.PC));

        assertThat(result.accessToken()).isNotBlank();
        LoginUser stored = capturedTokenUser();
        assertThat(stored.isIdentityScoped()).isTrue();
        assertThat(stored.getIdentityId()).isEqualTo(100L);
        assertThat(stored.getDutyCode()).isEqualTo("SCHOOL_ADMIN");
        assertThat(stored.getRoleIds()).containsExactly(500L);
        assertThat(stored.getRoleKeys()).containsExactly("common");
        assertThat(stored.getPermissions()).containsExactly("system:user:list");
    }

    @Test
    void identityTicketRejectsTenantMismatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String ticket = "ticket-tenant";
        String ticketKey = CacheConstants.CACHE_PREFIX + "identity_ticket:" + ticket;
        String ticketJson = XuJsonUtil.toJsonString(Map.of(
                "userId", 9L,
                "tenantId", 99L,
                "clientType", "pc",
                "forceChangePassword", false));
        when(valueOperations.getAndDelete(ticketKey)).thenReturn(ticketJson);

        UserVO user = user(9L, 1L);
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));

        IdentitySelectDTO dto = new IdentitySelectDTO();
        dto.setIdentityTicket(ticket);
        dto.setIdentityId(100L);

        assertThatThrownBy(() -> authService.selectIdentity(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("身份票据与当前账号租户不一致");
    }

    @Test
    void identityTicketRestoresForceChangePassword() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String ticket = "ticket-pwd";
        String ticketKey = CacheConstants.CACHE_PREFIX + "identity_ticket:" + ticket;
        String ticketJson = XuJsonUtil.toJsonString(Map.of(
                "userId", 9L,
                "tenantId", 1L,
                "clientType", "h5",
                "forceChangePassword", true));
        when(valueOperations.getAndDelete(ticketKey)).thenReturn(ticketJson);

        UserVO user = user(9L, 1L);
        when(systemServiceClient.listClassroomIdentities(9L)).thenReturn(R.ok(List.of(
                identity("100", "200", "示范小学", "TEACHER", "TEACHER", "张三"))));
        when(systemServiceClient.getUserById(9L)).thenReturn(R.ok(user));
        when(systemServiceClient.getPermissionsByUserId(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.getDataScopeDeptIds(9L)).thenReturn(R.ok(Set.of()));
        when(systemServiceClient.recordLoginLog(any())).thenReturn(R.ok());

        IdentitySelectDTO dto = new IdentitySelectDTO();
        dto.setIdentityTicket(ticket);
        dto.setIdentityId(100L);

        LoginVO result = authService.selectIdentity(dto);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.forceChangePassword()).isTrue();
    }

    @Test
    void switchTenantRechecksTargetIdentityInsteadOfRestoringAccountPermissions() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(9L)
                .tenantId(1L)
                .username("admin")
                .clientType(ClientType.PC)
                .build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemServiceClient.getUserTenants("admin")).thenReturn(R.ok(List.of(
                Map.of("tenantId", 2L, "status", 0)
        )));
        when(tenantServiceClient.checkTenantValid(2L)).thenReturn(R.ok(true));
        UserVO target = user(12L, 2L);
        when(systemServiceClient.getUserByUsername("admin", 2L)).thenReturn(R.ok(target));
        when(systemServiceClient.listClassroomIdentities(12L)).thenReturn(R.ok(List.of(
                identity("200", "300", "实验中学", "TEACHER", "TEACHER", "王五"),
                identity("201", "301", "第二中学", "TEACHER", "TEACHER", "赵六"))));

        LoginVO result = authService.switchTenant(2L, "Bearer old-token");

        assertThat(result.requireIdentity()).isTrue();
        assertThat(result.identityTicket()).isNotBlank();
        assertThat(result.accessToken()).isNull();
        verify(valueOperations, never()).set(startsWith(CacheConstants.TOKEN_KEY), anyString(), any(Duration.class));
    }

    /** 捕获写入 Redis 的 accessToken 对应 LoginUser（TOKEN_KEY 前缀唯一命中）。 */
    private LoginUser capturedTokenUser() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(startsWith(CacheConstants.TOKEN_KEY), captor.capture(), any(Duration.class));
        return XuJsonUtil.parseObject(captor.getValue(), LoginUser.class);
    }

    private LoginDTO loginDto(String username, String password, ClientType clientType) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setClientType(clientType);
        return dto;
    }

    private UserVO user(Long userId, Long tenantId) {
        UserVO user = new UserVO();
        user.setUserId(userId);
        user.setTenantId(tenantId);
        user.setUsername("admin");
        user.setNickname("Admin");
        user.setStatus(0);
        user.setRoleKeys(Set.of("common"));
        user.setPassword(PasswordUtil.encode(RAW_PASSWORD));
        return user;
    }

    private ClassroomIdentityVO identity(String identityId, String schoolId, String schoolName,
                                         String personType, String dutyCode, String userName) {
        return ClassroomIdentityVO.builder()
                .identityId(identityId)
                .schoolId(schoolId)
                .schoolName(schoolName)
                .personType(personType)
                .dutyCode(dutyCode)
                .userName(userName)
                .build();
    }

    private ClassroomIdentityVO identityWithManagement(String identityId, String schoolId, String schoolName,
                                                       String personType, String dutyCode, String userName,
                                                       boolean managementAvailable,
                                                       String managementUnavailableReason) {
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
