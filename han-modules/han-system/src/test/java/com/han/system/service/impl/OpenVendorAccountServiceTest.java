package com.han.system.service.impl;

import com.han.api.system.domain.OpenVendorAccountCreateDTO;
import com.han.api.tenant.TenantServiceClient;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.core.util.PasswordUtil;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenVendorAccountServiceTest {

    private SysUserMapper userMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRoleMapper roleMapper;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        SysUserConverter converter = mock(SysUserConverter.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        TenantServiceClient tenantServiceClient = mock(TenantServiceClient.class);
        service = new SysUserServiceImpl(userMapper, converter, userRoleMapper, userPostMapper,
                roleMapper, tenantServiceClient);
    }

    @Test
    void rejectsWeakPasswordBeforeDatabaseAccess() {
        OpenVendorAccountCreateDTO dto = request();
        dto.setPassword("weak");

        assertThatThrownBy(() -> service.createOpenVendorAccount(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码长度不能少于8位");
        verify(userMapper, never()).selectCount(any());
    }

    @Test
    void rejectsUsernameDuplicatedAcrossTenants() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createOpenVendorAccount(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("用户名");
        verify(roleMapper, never()).selectOne(any());
    }

    @Test
    void retriesSameDisabledOpenVendorAccountIdempotently() {
        SysUserPo existing = existingAccount("Strong@123", "13800000000");
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        when(userMapper.selectRoleIdsByUserId(99L)).thenReturn(java.util.Set.of(202608230001L));
        when(roleMapper.selectById(202608230001L)).thenReturn(openVendorRole());

        assertThat(service.createOpenVendorAccount(request())).isEqualTo(99L);
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    @Test
    void rejectsRetryWhenPasswordOrPhoneDiffers() {
        SysUserPo existing = existingAccount("Other@123", "13800000000");
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        when(userMapper.selectRoleIdsByUserId(99L)).thenReturn(java.util.Set.of(202608230001L));
        when(roleMapper.selectById(202608230001L)).thenReturn(openVendorRole());

        assertThatThrownBy(() -> service.createOpenVendorAccount(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("用户名");

        existing.setPassword(PasswordUtil.encode("Strong@123"));
        existing.setPhone("13900000000");
        assertThatThrownBy(() -> service.createOpenVendorAccount(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("用户名");
    }

    @Test
    void createsDisabledAccountWithOnlyOpenVendorRole() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        SysRolePo role = new SysRolePo();
        role.setId(202608230001L);
        role.setTenantId(1L);
        role.setRoleKey("openVendor");
        role.setStatus(0);
        role.setDelFlag(0);
        when(roleMapper.selectOne(any())).thenReturn(role);
        doAnswer(invocation -> {
            SysUserPo user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        }).when(userMapper).insert(any(SysUserPo.class));

        Long userId = service.createOpenVendorAccount(request());

        assertThat(userId).isEqualTo(99L);
        var captor = org.mockito.ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getEmail()).isEqualTo("vendor@example.com");
        assertThat(captor.getValue().getPwdUpdateTime()).isNotNull();
        assertThat(PasswordUtil.matches("Strong@123", captor.getValue().getPassword())).isTrue();
        verify(userRoleMapper).insert(any(SysUserRolePo.class));
    }

    @Test
    void activatesOnlyDisabledOpenVendorAccount() {
        SysUserPo user = new SysUserPo();
        user.setId(99L);
        user.setTenantId(1L);
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.selectRoleIdsByUserId(99L)).thenReturn(java.util.Set.of(202608230001L));
        SysRolePo role = new SysRolePo();
        role.setId(202608230001L);
        role.setTenantId(1L);
        role.setRoleKey("openVendor");
        role.setDelFlag(0);
        when(roleMapper.selectById(202608230001L)).thenReturn(role);

        service.activateOpenVendorAccount(99L);

        var captor = org.mockito.ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    void activatingAlreadyActiveOpenVendorAccountIsIdempotent() {
        SysUserPo user = new SysUserPo();
        user.setId(99L);
        user.setTenantId(1L);
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.selectRoleIdsByUserId(99L)).thenReturn(java.util.Set.of(202608230001L));
        SysRolePo role = new SysRolePo();
        role.setId(202608230001L);
        role.setTenantId(1L);
        role.setRoleKey("openVendor");
        role.setDelFlag(0);
        when(roleMapper.selectById(202608230001L)).thenReturn(role);

        service.activateOpenVendorAccount(99L);

        verify(userMapper, never()).updateById(any(SysUserPo.class));
    }

    @Test
    void compensationRenamesAndClearsPhoneBeforeLogicalDelete() {
        SysUserPo user = new SysUserPo();
        user.setId(99L);
        user.setTenantId(1L);
        user.setUsername("vendor_user");
        user.setPhone("13900000000");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.selectRoleIdsByUserId(99L)).thenReturn(java.util.Set.of(202608230001L));
        SysRolePo role = new SysRolePo();
        role.setId(202608230001L);
        role.setTenantId(1L);
        role.setRoleKey("openVendor");
        role.setDelFlag(0);
        when(roleMapper.selectById(202608230001L)).thenReturn(role);
        when(userMapper.updateById(any(SysUserPo.class))).thenReturn(1);

        service.compensateOpenVendorAccount(99L);

        var captor = org.mockito.ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("vendor_compensated_99");
        assertThat(captor.getValue().getPhone()).isEmpty();
        assertThat(captor.getValue().getEmail()).isEmpty();
        verify(userMapper).deleteById(99L);

        // 补偿后原用户名/手机号不再占用，可再次申请。
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(role);
        doAnswer(invocation -> {
            SysUserPo recreated = invocation.getArgument(0);
            recreated.setId(100L);
            return 1;
        }).when(userMapper).insert(any(SysUserPo.class));
        service.createOpenVendorAccount(request());
        verify(userMapper).insert(any(SysUserPo.class));
    }

    private static OpenVendorAccountCreateDTO request() {
        OpenVendorAccountCreateDTO dto = new OpenVendorAccountCreateDTO();
        dto.setTenantId(1L);
        dto.setUsername("vendor_user");
        dto.setNickname("厂商用户");
        dto.setPassword("Strong@123");
        dto.setPhone("13800000000");
        dto.setEmail("vendor@example.com");
        return dto;
    }

    private static SysUserPo existingAccount(String password, String phone) {
        SysUserPo user = new SysUserPo();
        user.setId(99L);
        user.setTenantId(1L);
        user.setUsername("vendor_user");
        user.setPassword(PasswordUtil.encode(password));
        user.setPhone(phone);
        user.setEmail("vendor@example.com");
        user.setStatus(1);
        user.setDelFlag(0);
        return user;
    }

    private static SysRolePo openVendorRole() {
        SysRolePo role = new SysRolePo();
        role.setId(202608230001L);
        role.setTenantId(1L);
        role.setRoleKey("openVendor");
        role.setStatus(0);
        role.setDelFlag(0);
        return role;
    }
}
