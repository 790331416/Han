package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.converter.OpenAppConverter;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.vo.OpenAppCredentialVO;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAppServiceImplTest {

    private OpenAppMapper appMapper;
    private OpenAppConverter converter;
    private OpenVendorMapper vendorMapper;
    private OpenVendorUserMapper vendorUserMapper;
    private OpenAppServiceImpl service;

    @BeforeEach
    void setUp() {
        appMapper = mock(OpenAppMapper.class);
        converter = mock(OpenAppConverter.class);
        vendorMapper = mock(OpenVendorMapper.class);
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        service = new OpenAppServiceImpl(appMapper, converter, vendorMapper, vendorUserMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void vendorUserCreatesDraftWithoutProductionSecret() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorPo vendor = approvedVendor();
        OpenAppPo app = app(100L, 42L);
        OpenAppDTO dto = dto(app);
        when(vendorMapper.selectById(100L)).thenReturn(vendor);
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);
        when(appMapper.selectCount(any())).thenReturn(0L);
        when(converter.toPo(dto)).thenReturn(app);
        doAnswer(invocation -> {
            ((OpenAppPo) invocation.getArgument(0)).setId(200L);
            return 1;
        }).when(appMapper).insert(any(OpenAppPo.class));

        OpenAppCredentialVO result = service.createWithCredentials(dto);

        assertThat(result.appId()).isEqualTo(200L);
        assertThat(result.appSecret()).isNull();
        assertThat(app.getLifecycleStatus()).isEqualTo(OpenAppServiceImpl.LIFECYCLE_DRAFT);
        assertThat(app.getTenantId()).isEqualTo(99L);
    }

    @Test
    void administratorKeepsLegacyCredentialCreationUsable() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(99L).build());
        OpenAppPo app = app(null, null);
        OpenAppDTO dto = dto(app);
        when(converter.toPo(dto)).thenReturn(app);
        when(appMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ((OpenAppPo) invocation.getArgument(0)).setId(201L);
            return 1;
        }).when(appMapper).insert(any(OpenAppPo.class));

        OpenAppCredentialVO result = service.createWithCredentials(dto);

        assertThat(result.appSecret()).isNotBlank();
        assertThat(app.getLifecycleStatus()).isEqualTo(OpenAppServiceImpl.LIFECYCLE_PRODUCTION);
    }

    @Test
    void vendorDraftCannotValidateClient() {
        OpenAppPo app = app(100L, 42L);
        app.setLifecycleStatus(OpenAppServiceImpl.LIFECYCLE_DRAFT);
        app.setAppSecret("secret");
        when(appMapper.selectOne(any())).thenReturn(app);

        assertThat(service.validateClient(app.getAppKey(), "secret")).isFalse();
    }

    @Test
    void vendorUserCanSubmitDraftButCannotJumpToProduction() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppPo app = app(100L, 42L);
        app.setId(202L);
        when(appMapper.selectById(202L)).thenReturn(app);
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);

        service.updateLifecycleStatus(202L, OpenAppServiceImpl.LIFECYCLE_PENDING);
        verify(appMapper).updateById(any(OpenAppPo.class));

        app.setLifecycleStatus(OpenAppServiceImpl.LIFECYCLE_DRAFT);
        assertThatThrownBy(() -> service.updateLifecycleStatus(202L,
                OpenAppServiceImpl.LIFECYCLE_PRODUCTION))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用生命周期状态转换不合法");
    }

    @Test
    void vendorUserCanEnterTestingAfterSandboxApproval() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppPo app = app(100L, 42L);
        app.setId(205L);
        app.setLifecycleStatus(OpenAppServiceImpl.LIFECYCLE_SANDBOX);
        when(appMapper.selectById(205L)).thenReturn(app);
        when(vendorUserMapper.selectCount(any())).thenReturn(1L);

        service.updateLifecycleStatus(205L, OpenAppServiceImpl.LIFECYCLE_TESTING);

        ArgumentCaptor<OpenAppPo> captor = ArgumentCaptor.forClass(OpenAppPo.class);
        verify(appMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo(OpenAppServiceImpl.LIFECYCLE_TESTING);
    }

    @Test
    void crossVendorAppIsNotVisibleToAnotherVendorUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(43L).tenantId(99L).build());
        OpenAppPo app = app(100L, 42L);
        app.setId(203L);
        when(appMapper.selectById(203L)).thenReturn(app);
        when(vendorUserMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.selectVoById(203L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作其他厂商应用");
    }

    @Test
    void administratorCanApproveLegalLifecycleTransitionOnly() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(99L).build());
        OpenAppPo app = app(100L, 42L);
        app.setId(204L);
        when(appMapper.selectById(204L)).thenReturn(app);

        service.updateLifecycleStatus(204L, OpenAppServiceImpl.LIFECYCLE_PENDING);
        ArgumentCaptor<OpenAppPo> captor = ArgumentCaptor.forClass(OpenAppPo.class);
        verify(appMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo(OpenAppServiceImpl.LIFECYCLE_PENDING);

        app.setLifecycleStatus(OpenAppServiceImpl.LIFECYCLE_DRAFT);
        assertThatThrownBy(() -> service.updateLifecycleStatus(204L,
                OpenAppServiceImpl.LIFECYCLE_PRODUCTION))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用生命周期状态转换不合法");
    }

    private static OpenAppDTO dto(OpenAppPo app) {
        OpenAppDTO dto = new OpenAppDTO();
        dto.setBase(app);
        return dto;
    }

    private static OpenVendorPo approvedVendor() {
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setTenantId(99L);
        vendor.setStatus(4);
        return vendor;
    }

    private static OpenAppPo app(Long vendorId, Long ownerId) {
        OpenAppPo app = new OpenAppPo();
        app.setVendorId(vendorId);
        app.setTenantId(99L);
        app.setAppName("测试应用" + (ownerId == null ? "管理员" : ownerId));
        app.setAppType("web");
        app.setAccessTokenTtl(3600);
        app.setRefreshTokenTtl(7200);
        app.setLifecycleStatus(ownerId == null ? null : OpenAppServiceImpl.LIFECYCLE_DRAFT);
        app.setEnvironmentPolicy("SANDBOX_FIRST");
        app.setAppKey("app-test-" + (ownerId == null ? "admin" : ownerId));
        return app;
    }
}
