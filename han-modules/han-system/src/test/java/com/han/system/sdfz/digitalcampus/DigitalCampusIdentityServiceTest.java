package com.han.system.sdfz.digitalcampus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.exception.BusinessException;
import com.han.system.converter.SysUserApiConverter;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserSocialPo;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.SysUserSocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalCampusIdentityServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserSocialService socialService;
    @Mock
    private SysUserApiConverter userConverter;
    @Mock
    private DigitalCampusEducationSyncService educationSyncService;

    private DigitalCampusIdentityService service;

    @BeforeEach
    void setUp() {
        service = new DigitalCampusIdentityService(
                userMapper, socialService, userConverter, new ObjectMapper(), educationSyncService);
    }

    @Test
    void firstLoginCreatesUnprivilegedUserAndStoresSanitizedSnapshot() {
        DigitalCampusUserSyncDTO dto = syncDto();
        when(socialService.listByProviderOpenId("digital-campus", "external-user-1")).thenReturn(List.of());
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SysUserPo user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        }).when(userMapper).insert(any(SysUserPo.class));
        when(userConverter.toApiUserVO(any(SysUserPo.class))).thenAnswer(invocation -> {
            SysUserPo user = invocation.getArgument(0);
            UserVO result = new UserVO();
            result.setUserId(user.getId());
            result.setUsername(user.getUsername());
            result.setStatus(user.getStatus());
            return result;
        });

        UserVO result = service.syncCurrentUser(dto);

        ArgumentCaptor<SysUserPo> userCaptor = ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).insert(userCaptor.capture());
        SysUserPo created = userCaptor.getValue();
        assertThat(created.getTenantId()).isEqualTo(1L);
        assertThat(created.getUsername()).startsWith("dc_").hasSize(27);
        assertThat(created.getNickname()).isEqualTo("测试教师");
        assertThat(created.getPhone()).isNull();
        assertThat(created.getPassword()).isNotBlank().doesNotContain("external-user-1");
        assertThat(created.getStatus()).isZero();
        assertThat(result.getUserId()).isEqualTo(100L);

        verify(socialService).bind(eq(100L), eq(1L), eq("digital-campus"), eq("external-user-1"),
                isNull(), eq("测试教师"), isNull());
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(socialService).updateExtra(eq(100L), eq("digital-campus"), snapshotCaptor.capture());
        verify(educationSyncService).sync(dto, 100L);
        assertThat(snapshotCaptor.getValue())
                .contains("\"externalIdentityId\":\"identity-1\"")
                .contains("\"schoolId\":\"school-1\"")
                .doesNotContain("access-token");
    }

    @Test
    void existingBindingRefreshesAllowedFieldsWithoutReactivatingUser() {
        DigitalCampusUserSyncDTO dto = syncDto();
        SysUserSocialPo binding = SysUserSocialPo.builder()
                .userId(100L).tenantId(1L).provider("digital-campus").openId("external-user-1").build();
        SysUserPo existing = new SysUserPo();
        existing.setId(100L);
        existing.setTenantId(1L);
        existing.setUsername("dc_existing");
        existing.setNickname("旧姓名");
        existing.setPhone("13900000000");
        existing.setStatus(1);
        when(socialService.listByProviderOpenId("digital-campus", "external-user-1"))
                .thenReturn(List.of(binding));
        when(userMapper.selectById(100L)).thenReturn(existing);
        when(userConverter.toApiUserVO(existing)).thenReturn(new UserVO());

        service.syncCurrentUser(dto);

        assertThat(existing.getNickname()).isEqualTo("测试教师");
        assertThat(existing.getPhone()).isEqualTo("13900000000");
        assertThat(existing.getStatus()).isEqualTo(1);
        verify(userMapper).updateById(existing);
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    @Test
    void duplicateBindingsAreRejectedInsteadOfGuessing() {
        DigitalCampusUserSyncDTO dto = syncDto();
        SysUserSocialPo first = SysUserSocialPo.builder().userId(1L).tenantId(1L).build();
        SysUserSocialPo second = SysUserSocialPo.builder().userId(2L).tenantId(1L).build();
        when(socialService.listByProviderOpenId("digital-campus", "external-user-1"))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.syncCurrentUser(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数字校园身份映射冲突，请联系管理员");
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    @Test
    void externalProfileExposesOnlySanitizedStoredSnapshot() {
        SysUserSocialPo binding = SysUserSocialPo.builder()
                .userId(100L)
                .provider("digital-campus")
                .openId("external-user-1")
                .extra("{\"externalIdentityId\":\"identity-1\",\"schoolId\":\"school-1\"}")
                .build();
        when(socialService.getByUserAndProvider(100L, "digital-campus")).thenReturn(binding);

        java.util.Map<String, Object> result = service.getExternalProfile(100L);

        assertThat(result)
                .containsEntry("provider", "digital-campus")
                .containsEntry("externalUserId", "external-user-1")
                .containsEntry("externalIdentityId", "identity-1")
                .doesNotContainKey("accessToken");
    }

    private DigitalCampusUserSyncDTO syncDto() {
        return DigitalCampusUserSyncDTO.builder()
                .tenantId(1L)
                .externalUserId("external-user-1")
                .externalIdentityId("identity-1")
                .userName("测试教师")
                .phone("138****0000")
                .identityName("教师")
                .roleType("2")
                .schoolId("school-1")
                .schoolName("测试学校")
                .branchId("class-1")
                .branchName("一班")
                .areaCode("50010000")
                .duties(List.of())
                .classes(List.of())
                .build();
    }
}
