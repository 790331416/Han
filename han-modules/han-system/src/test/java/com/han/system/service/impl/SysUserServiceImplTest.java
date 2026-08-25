package com.han.system.service.impl;

import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.controller.inner.ISysUserController;
import com.han.system.converter.SysUserApiConverter;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysRoleDeptMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.EducationAccountIdentityService;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.service.ISysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统用户状态/角色修改触发会话撤销，以及服务侧 UserVO 教育账号标识组装。
 */
class SysUserServiceImplTest {

    private SysUserMapper userMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysUserPostMapper userPostMapper;
    private SysRoleMapper roleMapper;
    private SysUserConverter converter;
    private TenantServiceClient tenantServiceClient;
    private EducationAccountIdentityService identityService;
    private AuthServiceClient authServiceClient;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        converter = mock(SysUserConverter.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        userPostMapper = mock(SysUserPostMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        tenantServiceClient = mock(TenantServiceClient.class);
        identityService = mock(EducationAccountIdentityService.class);
        authServiceClient = mock(AuthServiceClient.class);
        service = new SysUserServiceImpl(userMapper, converter, userRoleMapper, userPostMapper,
                roleMapper, tenantServiceClient, identityService);
        // 字段注入的会话撤销客户端 + MyBatis-Plus ServiceImpl 的 baseMapper（单测无 Spring 容器）。
        ReflectionTestUtils.setField(service, "authServiceClient", authServiceClient);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        // 会话撤销默认成功：个别用例再按需覆盖为 R.fail / 抛网络异常。
        when(authServiceClient.revokeSession(any())).thenReturn(R.ok());
        // 以超级管理员身份调用，使 DataOwnerUtil.checkRolePermission 放行。
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    /** 角色集合发生变化时，更新角色后撤销该账号全部会话（identityId 为空）。 */
    @Test
    void changingUserRolesRevokesAllSessions() {
        SysUserPo existUser = user(9L);
        when(userMapper.selectById(9L)).thenReturn(existUser);
        when(userMapper.selectRoleIdsByUserId(9L)).thenReturn(Set.of(100L));

        SysUserDto dto = new SysUserDto();
        dto.setUserId(9L);
        dto.setRoleIds(Set.of(200L));

        service.update(dto);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9L);
        assertThat(request.getIdentityId()).isNull();
    }

    /** 账号 0→1 停用时更新状态后撤销全部会话。 */
    @Test
    void disablingUserRevokesAllSessions() {
        SysUserPo existing = user(9L);
        existing.setStatus(0);
        when(userMapper.selectById(9L)).thenReturn(existing);

        service.updateUserStatus(9L, 1);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9L);
        assertThat(request.getIdentityId()).isNull();
    }

    /** 删除用户前撤销全部会话（失败则删除事务回滚，不删用户）。 */
    @Test
    void deletingUserRevokesAllSessions() {
        service.deleteById(9L);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9L);
        assertThat(request.getIdentityId()).isNull();
        verify(userMapper).deleteById(9L);
    }

    /** 重置密码后撤销现有会话，强制用新密码重新登录。 */
    @Test
    void resetPasswordRevokesExistingSessions() {
        when(userMapper.updateById(any(SysUserPo.class))).thenReturn(1);

        service.resetPwd(9L, "NewPwd@123");

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9L);
        assertThat(request.getIdentityId()).isNull();
    }

    /** 角色集合未变化（仅顺序不同/null 与空等价）时不撤销会话。 */
    @Test
    void unchangedRolesDoNotRevokeSessions() {
        SysUserPo existUser = user(9L);
        when(userMapper.selectById(9L)).thenReturn(existUser);
        when(userMapper.selectRoleIdsByUserId(9L)).thenReturn(Set.of(100L, 200L));

        SysUserDto dto = new SysUserDto();
        dto.setUserId(9L);
        dto.setRoleIds(Set.of(200L, 100L));

        service.update(dto);

        verify(authServiceClient, never()).revokeSession(any(SessionRevokeRequest.class));
    }

    /** 恢复启用（1→0）不撤销会话。 */
    @Test
    void enablingDisabledUserDoesNotRevokeSessions() {
        SysUserPo existing = user(9L);
        existing.setStatus(1);
        when(userMapper.selectById(9L)).thenReturn(existing);

        service.updateUserStatus(9L, 0);

        verify(authServiceClient, never()).revokeSession(any(SessionRevokeRequest.class));
    }

    /** 会话撤销失败不静默忽略：抛业务异常使角色更新事务回滚。 */
    @Test
    void revokeFailureOnRoleChangeThrowsBusinessException() {
        SysUserPo existUser = user(9L);
        when(userMapper.selectById(9L)).thenReturn(existUser);
        when(userMapper.selectRoleIdsByUserId(9L)).thenReturn(Set.of(100L));
        when(authServiceClient.revokeSession(any(SessionRevokeRequest.class)))
                .thenThrow(new RuntimeException("down"));

        SysUserDto dto = new SysUserDto();
        dto.setUserId(9L);
        dto.setRoleIds(Set.of(200L));

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("会话撤销失败，请稍后重试");
    }

    /** 会话撤销返回 R.fail（非成功 code）同样抛业务异常使角色更新事务回滚。 */
    @Test
    void revokeBusinessFailureOnRoleChangeThrowsBusinessException() {
        SysUserPo existUser = user(9L);
        when(userMapper.selectById(9L)).thenReturn(existUser);
        when(userMapper.selectRoleIdsByUserId(9L)).thenReturn(Set.of(100L));
        when(authServiceClient.revokeSession(any(SessionRevokeRequest.class)))
                .thenReturn(R.fail(500, "auth down"));

        SysUserDto dto = new SysUserDto();
        dto.setUserId(9L);
        dto.setRoleIds(Set.of(200L));

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("会话撤销失败，请稍后重试");
    }

    /**
     * 服务侧 UserVO 组装角度：即使账号拥有 common 管理角色，只要 edu_person 存在未删除绑定，
     * UserVO 仍应标记 educationBound=true，从而被 auth 侧识别为教育账号（无有效身份时禁止登录）。
     */
    @Test
    void educationSchoolAdminWithCommonRoleAndNoValidIdentityIsBlocked() {
        SysUserPo po = user(9L);
        po.setRemark(null); // 非「教育人员」开头，educationAccount=false，仅靠绑定识别

        ISysUserService sysUserService = mock(ISysUserService.class);
        SysRoleMapper cRoleMapper = mock(SysRoleMapper.class);
        SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        SysUserConverter cConverter = mock(SysUserConverter.class);
        SysUserApiConverter apiConverter = mock(SysUserApiConverter.class);
        EduPersonMapper eduPersonMapper = mock(EduPersonMapper.class);

        when(userMapper.selectById(9L)).thenReturn(po);
        UserVO converted = new UserVO();
        converted.setUserId(9L);
        when(apiConverter.toApiUserVO(po)).thenReturn(converted);
        when(sysUserService.selectPermissionsByUserId(9L)).thenReturn(Set.of("system:user:list"));
        when(sysUserService.selectRoleKeysByUserId(9L)).thenReturn(Set.of("common"));
        when(eduPersonMapper.selectCount(any())).thenReturn(1L);

        ISysUserController controller = new ISysUserController(sysUserService, userMapper, cRoleMapper,
                roleDeptMapper, deptMapper, cConverter, apiConverter, eduPersonMapper);

        R<UserVO> result = controller.getUserById(9L);

        UserVO vo = result.getData();
        assertThat(vo).isNotNull();
        assertThat(vo.getRoleKeys()).containsExactly("common");
        assertThat(vo.isEducationAccount()).isFalse();
        assertThat(vo.isEducationBound()).isTrue();
        assertThat(vo.isEducationAccount() || vo.isEducationBound())
                .as("common 角色 + 已绑定教育人员时应被识别为教育账号")
                .isTrue();
    }

    // ---------------------------------------------------------------- 工具

    private SessionRevokeRequest captureRevoke() {
        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient).revokeSession(captor.capture());
        return captor.getValue();
    }

    private static SysUserPo user(Long id) {
        SysUserPo po = new SysUserPo();
        po.setId(id);
        po.setTenantId(1L);
        po.setUsername("user_" + id);
        po.setStatus(0);
        return po;
    }
}
