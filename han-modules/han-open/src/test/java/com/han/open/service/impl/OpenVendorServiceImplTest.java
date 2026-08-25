package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorProfileUpdateVO;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorApplicationMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenVendorServiceImplTest {

    private OpenVendorUserMapper vendorUserMapper;
    private OpenVendorApplicationMapper vendorApplicationMapper;
    private OpenAppMapper appMapper;
    private OpenVendorMapper baseMapper;
    private OpenVendorServiceImpl service;

    @BeforeEach
    void setUp() {
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        vendorApplicationMapper = mock(OpenVendorApplicationMapper.class);
        appMapper = mock(OpenAppMapper.class);
        baseMapper = mock(OpenVendorMapper.class);
        service = new OpenVendorServiceImpl(vendorUserMapper, vendorApplicationMapper, appMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void submitApplicationPersistsRealTenantAndUserIdsInsteadOfHardcodedOne() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(baseMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            OpenVendorPo vendor = invocation.getArgument(0);
            vendor.setId(100L);
            return 1;
        }).when(baseMapper).insert(any(OpenVendorPo.class));

        service.submitApplication(vendorApplicationVO());

        ArgumentCaptor<OpenVendorPo> vendorCaptor = ArgumentCaptor.forClass(OpenVendorPo.class);
        verify(baseMapper).insert(vendorCaptor.capture());
        OpenVendorPo vendor = vendorCaptor.getValue();
        assertThat(vendor.getTenantId()).isEqualTo(99L);
        assertThat(vendor.getCreateBy()).isEqualTo(42L);
        assertThat(vendor.getStatus()).isEqualTo(2);

        ArgumentCaptor<OpenVendorUserPo> userCaptor = ArgumentCaptor.forClass(OpenVendorUserPo.class);
        verify(vendorUserMapper).insert(userCaptor.capture());
        OpenVendorUserPo vendorUser = userCaptor.getValue();
        assertThat(vendorUser.getVendorId()).isEqualTo(100L);
        assertThat(vendorUser.getUserId()).isEqualTo(42L);
        assertThat(vendorUser.getRole()).isEqualTo("OWNER");

        ArgumentCaptor<OpenVendorApplicationPo> applicationCaptor = ArgumentCaptor.forClass(OpenVendorApplicationPo.class);
        verify(vendorApplicationMapper).insert(applicationCaptor.capture());
        OpenVendorApplicationPo application = applicationCaptor.getValue();
        assertThat(application.getVendorId()).isEqualTo(100L);
        assertThat(application.getApplicantUserId()).isEqualTo(42L);
        assertThat(application.getCreateBy()).isEqualTo(42L);
    }

    @Test
    void createPortalApplicationAllowsMissingEnterpriseDetails() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            OpenVendorPo vendor = invocation.getArgument(0);
            vendor.setId(100L);
            return 1;
        }).when(baseMapper).insert(any(OpenVendorPo.class));
        when(vendorUserMapper.insert(any(OpenVendorUserPo.class))).thenReturn(1);
        OpenVendorApplicationCreateDTO dto = new OpenVendorApplicationCreateDTO();
        dto.setAccountUserId(42L);
        dto.setName("个人开发者-vendor_user");

        assertThat(service.createPortalApplication(dto)).isNotBlank();

        ArgumentCaptor<OpenVendorPo> vendorCaptor = ArgumentCaptor.forClass(OpenVendorPo.class);
        verify(baseMapper).insert(vendorCaptor.capture());
        assertThat(vendorCaptor.getValue().getQualificationNo()).isNull();
        assertThat(vendorCaptor.getValue().getContactName()).isNull();
        assertThat(vendorCaptor.getValue().getContactPhone()).isNull();
    }

    @Test
    void submitApplicationThrowsWhenUserContextMissing() {
        assertThatThrownBy(() -> service.submitApplication(vendorApplicationVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void submitApplicationThrowsWhenTenantContextMissing() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).build());

        assertThatThrownBy(() -> service.submitApplication(vendorApplicationVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前租户信息失败");
    }

    @Test
    void submitApplicationRejectsDuplicateQualificationInSameTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(baseMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> service.submitApplication(vendorApplicationVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("统一社会信用代码已存在");
    }

    @Test
    void bindUserRejectsInvalidRoleBeforeWriting() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        when(baseMapper.selectById(100L)).thenReturn(vendor);

        assertThatThrownBy(() -> service.bindUser(100L, 43L, "OWNERX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商用户角色不合法");
    }

    @Test
    void bindUserRequiresOwnerOrAdministrator() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        when(baseMapper.selectById(100L)).thenReturn(vendor);
        when(vendorUserMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.bindUser(100L, 43L, "DEVELOPER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅厂商所有者或管理员可操作");
    }

    @Test
    void reviewApplicationRejectsIllegalReviewStatus() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(99L).build());

        assertThatThrownBy(() -> service.reviewApplication(1L, 4, "bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商审核结果不合法");
    }

    @Test
    void ownerCanUpdateOnlyVendorProfileFields() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        vendor.setQualificationNo("KEEP-ME");
        when(baseMapper.selectById(100L)).thenReturn(vendor);
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.updateById(any(OpenVendorPo.class))).thenReturn(1);
        VendorProfileUpdateVO profile = new VendorProfileUpdateVO();
        profile.setName("新名称");
        profile.setContactName("李四");
        profile.setContactPhone("13900000000");

        assertThat(service.updateProfile(100L, profile)).isTrue();

        ArgumentCaptor<OpenVendorPo> captor = ArgumentCaptor.forClass(OpenVendorPo.class);
        verify(baseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名称");
        assertThat(captor.getValue().getQualificationNo()).isEqualTo("KEEP-ME");
    }

    @Test
    void administratorCannotDeleteVendorWithApplications() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(99L)
                .roleKeys(Set.of("admin")).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        when(baseMapper.selectById(100L)).thenReturn(vendor);
        when(appMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.removeVendor(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该厂商仍有关联应用，请先删除应用");
    }

    @Test
    void administratorDeletesVendorWithoutApplications() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(99L)
                .roleKeys(Set.of("admin")).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        when(baseMapper.selectById(100L)).thenReturn(vendor);
        when(appMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.deleteById(100L)).thenReturn(1);

        assertThat(service.removeVendor(100L)).isTrue();

        verify(vendorUserMapper).delete(any());
        verify(vendorApplicationMapper).delete(any());
        verify(baseMapper).deleteById(100L);
    }

    private static VendorApplicationVO vendorApplicationVO() {
        VendorApplicationVO vo = new VendorApplicationVO();
        vo.setName("测试厂商");
        vo.setQualificationNo("USCC123456789");
        vo.setContactName("张三");
        vo.setContactPhone("13800000000");
        vo.setApplyReason("申请入驻理由");
        return vo;
    }
}
