package com.han.system.service.impl;

import com.han.api.tenant.TenantServiceClient;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.dto.ProfileDto;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.EducationAccountIdentityService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserProfileIdentitySyncTest {

    @Test
    void profileChangeSynchronizesLinkedEducationIdentity() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        EducationAccountIdentityService identityService = mock(EducationAccountIdentityService.class);
        SysUserPo user = new SysUserPo();
        user.setId(1L);
        user.setNickname("旧姓名");
        user.setPhone("13800000000");
        when(userMapper.selectById(1L)).thenReturn(user);
        SysUserServiceImpl service = new SysUserServiceImpl(userMapper, mock(SysUserConverter.class),
                mock(SysUserRoleMapper.class), mock(SysUserPostMapper.class), mock(SysRoleMapper.class),
                mock(TenantServiceClient.class), identityService);
        ProfileDto form = new ProfileDto();
        form.setNickname("新姓名");
        form.setPhone("13900000000");

        service.updateProfile(1L, form);

        verify(userMapper).updateById(user);
        verify(identityService).syncFromAccount(user);
    }
}
