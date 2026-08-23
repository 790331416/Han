package com.han.open.service.impl;

import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorApplicationMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class OpenVendorPortalServiceTest {

    private OpenVendorMapper vendorMapper;
    private OpenVendorApplicationMapper applicationMapper;
    private OpenVendorUserMapper vendorUserMapper;
    private SystemServiceClient systemServiceClient;
    private OpenVendorServiceImpl service;

    @BeforeEach
    void setUp() {
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        applicationMapper = mock(OpenVendorApplicationMapper.class);
        OpenAppMapper appMapper = mock(OpenAppMapper.class);
        vendorMapper = mock(OpenVendorMapper.class);
        systemServiceClient = mock(SystemServiceClient.class);
        service = new OpenVendorServiceImpl(vendorUserMapper, applicationMapper, appMapper);
        ReflectionTestUtils.setField(service, "baseMapper", vendorMapper);
        ReflectionTestUtils.setField(service, "systemServiceClient", systemServiceClient);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsPortalApplicationWithRandom32CharacterNumberAndOwner() {
        when(vendorMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            OpenVendorPo vendor = invocation.getArgument(0);
            vendor.setId(100L);
            return 1;
        }).when(vendorMapper).insert(any(OpenVendorPo.class));

        String applicationNo = service.createPortalApplication(request());

        assertThat(applicationNo).hasSize(32);
        var vendorUserCaptor = org.mockito.ArgumentCaptor.forClass(OpenVendorUserPo.class);
        verify(vendorUserMapper).insert(vendorUserCaptor.capture());
        assertThat(vendorUserCaptor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(vendorUserCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(vendorUserCaptor.getValue().getRole()).isEqualTo("OWNER");
        var applicationCaptor = org.mockito.ArgumentCaptor.forClass(OpenVendorApplicationPo.class);
        verify(applicationMapper).insert(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getApplyData()).isEqualTo("PUBLIC_PORTAL");
    }

    @Test
    void rejectsDuplicateEnterpriseBeforeWriting() {
        when(vendorMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createPortalApplication(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商名称已存在");
    }

    @Test
    void retriesSameOwnerPublicApplicationIdempotently() {
        when(vendorMapper.selectCount(any())).thenReturn(1L);
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setName("测试厂商");
        vendor.setQualificationNo("USCC123456789");
        when(vendorMapper.selectList(any())).thenReturn(java.util.List.of(vendor));
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setApplicationNo("existing-public-application");
        application.setApplyData("PUBLIC_PORTAL");
        when(applicationMapper.selectOne(any())).thenReturn(application);

        assertThat(service.createPortalApplication(request())).isEqualTo("existing-public-application");
        verify(vendorMapper, never()).insert(any(OpenVendorPo.class));
    }

    @Test
    void retriesAfterConcurrentVendorInsertConflict() {
        when(vendorMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            throw new DuplicateKeyException("duplicate vendor");
        }).when(vendorMapper).insert(any(OpenVendorPo.class));
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setName("测试厂商");
        vendor.setQualificationNo("USCC123456789");
        when(vendorMapper.selectList(any())).thenReturn(java.util.List.of(vendor));
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setApplicationNo("concurrent-public-application");
        application.setApplyData("PUBLIC_PORTAL");
        when(applicationMapper.selectOne(any())).thenReturn(application);

        assertThat(service.createPortalApplication(request())).isEqualTo("concurrent-public-application");
    }

    @Test
    void approvesPublicApplicationAndActivatesAccount() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setId(7L);
        application.setVendorId(100L);
        application.setApplicantUserId(42L);
        application.setApplyData("PUBLIC_PORTAL");
        application.setStatus(1);
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setStatus(2);
        when(applicationMapper.selectById(7L)).thenReturn(application);
        when(vendorMapper.selectById(100L)).thenReturn(vendor);
        when(systemServiceClient.activateOpenVendorAccount(42L)).thenReturn(R.ok());

        service.reviewApplication(7L, 2, null);

        verify(systemServiceClient).activateOpenVendorAccount(42L);
    }

    @Test
    void rejectsPublicApplicationWithoutActivatingAccount() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setId(7L);
        application.setVendorId(100L);
        application.setApplicantUserId(42L);
        application.setApplyData("PUBLIC_PORTAL");
        application.setStatus(1);
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setStatus(2);
        when(applicationMapper.selectById(7L)).thenReturn(application);
        when(vendorMapper.selectById(100L)).thenReturn(vendor);

        service.reviewApplication(7L, 3, "材料不完整");

        verify(systemServiceClient, never()).activateOpenVendorAccount(42L);
    }

    @Test
    void phoneOnlyStatusDoesNotExposeLegacyApplication() {
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setApplicationNo("legacy");
        application.setApplyData(null);
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setDelFlag(0);
        vendor.setContactPhone("13800000000");
        when(vendorMapper.selectOne(any())).thenReturn(vendor);
        when(applicationMapper.selectOne(any())).thenReturn(application);

        assertThatThrownBy(() -> service.queryPublicApplication("13800000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申请不存在或校验信息不匹配");
    }

    @Test
    void publicStatusLookupScopesVendorToPlatformTenantAndNotDeletedRows() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), OpenVendorPo.class);
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setApplyData("PUBLIC_PORTAL");
        application.setStatus(1);
        application.setVendorId(100L);
        application.setCreateTime(java.time.LocalDateTime.now());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(1L);
        vendor.setDelFlag(0);
        vendor.setContactPhone("13800000000");
        vendor.setName("公开厂商");
        when(vendorMapper.selectOne(any())).thenReturn(vendor);
        when(applicationMapper.selectOne(any())).thenReturn(application);

        assertThat(service.queryPublicApplication("13800000000").getVendorName())
                .isEqualTo("公开厂商");

        org.mockito.ArgumentCaptor<Wrapper<OpenVendorPo>> captor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(vendorMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("contactPhone", "tenantId", "delFlag");
    }

    private static OpenVendorApplicationCreateDTO request() {
        OpenVendorApplicationCreateDTO dto = new OpenVendorApplicationCreateDTO();
        dto.setAccountUserId(42L);
        dto.setName("测试厂商");
        dto.setQualificationNo("USCC123456789");
        dto.setContactName("张三");
        dto.setContactPhone("13800000000");
        return dto;
    }
}
