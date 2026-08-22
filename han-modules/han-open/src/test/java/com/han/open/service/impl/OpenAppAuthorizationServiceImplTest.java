package com.han.open.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenAppCredentialPo;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.mapper.OpenAppCredentialMapper;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenAuthorizationRequestMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.service.OpenAppAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAppAuthorizationServiceImplTest {

    private OpenAuthorizationRequestMapper authorizationRequestMapper;
    private OpenAppCredentialMapper appCredentialMapper;
    private OpenAppMapper appMapper;
    private OpenVendorUserMapper vendorUserMapper;
    private OpenApiResourceMapper resourceMapper;
    private OpenAppResourceGrantMapper baseMapper;
    private OpenAppAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        authorizationRequestMapper = mock(OpenAuthorizationRequestMapper.class);
        appCredentialMapper = mock(OpenAppCredentialMapper.class);
        appMapper = mock(OpenAppMapper.class);
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        resourceMapper = mock(OpenApiResourceMapper.class);
        baseMapper = mock(OpenAppResourceGrantMapper.class);
        service = new OpenAppAuthorizationServiceImpl(authorizationRequestMapper, appCredentialMapper,
                new ObjectMapper(), appMapper, vendorUserMapper, resourceMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(appMapper.selectOne(any())).thenReturn(ownedApp());
        when(vendorUserMapper.selectOne(any())).thenReturn(vendorMembership());
        when(resourceMapper.selectOne(any())).thenReturn(publishedResource());
        when(authorizationRequestMapper.update(any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void submitGrantApplyPersistsApplicantAndCreatorFromRealUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());

        service.submitGrantApply(grantApplyVO());

        ArgumentCaptor<OpenAuthorizationRequestPo> captor = ArgumentCaptor.forClass(OpenAuthorizationRequestPo.class);
        verify(authorizationRequestMapper).insert(captor.capture());
        OpenAuthorizationRequestPo request = captor.getValue();
        assertThat(request.getApplicantId()).isEqualTo(42L);
        assertThat(request.getCreateBy()).isEqualTo(42L);
        assertThat(request.getRequestType()).isEqualTo(0);
        assertThat(request.getStatus()).isEqualTo(0);
    }

    @Test
    void reviewGrantApplyPersistsReviewerAndUpdaterFromRealUser() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setStatus(0);
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        service.reviewGrantApply(1L, 2, "驳回原因");

        ArgumentCaptor<OpenAuthorizationRequestPo> captor = ArgumentCaptor.forClass(OpenAuthorizationRequestPo.class);
        ArgumentCaptor<OpenAuthorizationRequestPo> updateCaptor = ArgumentCaptor.forClass(OpenAuthorizationRequestPo.class);
        verify(authorizationRequestMapper).update(updateCaptor.capture(), any());
        OpenAuthorizationRequestPo updated = updateCaptor.getValue();
        assertThat(updated.getReviewerId()).isEqualTo(42L);
        assertThat(updated.getUpdateBy()).isEqualTo(42L);
        assertThat(updated.getStatus()).isEqualTo(2);
        assertThat(updated.getReviewReason()).isEqualTo("驳回原因");
    }

    @Test
    void revokeGrantPersistsReviewerAndUpdaterFromRealUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setId(1L);
        grant.setTenantId(99L);
        grant.setAppId(123L);
        grant.setStatus(1);
        when(baseMapper.selectById(1L)).thenReturn(grant);

        service.revokeGrant(1L, "撤销原因");

        ArgumentCaptor<OpenAppResourceGrantPo> captor = ArgumentCaptor.forClass(OpenAppResourceGrantPo.class);
        verify(baseMapper).updateById(captor.capture());
        OpenAppResourceGrantPo updated = captor.getValue();
        assertThat(updated.getReviewerId()).isEqualTo(42L);
        assertThat(updated.getUpdateBy()).isEqualTo(42L);
        assertThat(updated.getStatus()).isEqualTo(4);
        assertThat(updated.getReviewReason()).isEqualTo("撤销原因");
    }

    @Test
    void generateCredentialPersistsCreatorAppIdAndEnvironmentFromRealUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(appCredentialMapper.selectList(any())).thenReturn(List.of());

        AppCredentialVO vo = service.generateCredential(123L, "SANDBOX");

        ArgumentCaptor<OpenAppCredentialPo> captor = ArgumentCaptor.forClass(OpenAppCredentialPo.class);
        verify(appCredentialMapper).insert(captor.capture());
        OpenAppCredentialPo credential = captor.getValue();
        assertThat(credential.getCreateBy()).isEqualTo(42L);
        assertThat(credential.getTenantId()).isEqualTo(99L);
        assertThat(credential.getAppId()).isEqualTo(123L);
        assertThat(credential.getEnvironment()).isEqualTo("SANDBOX");
        assertThat(credential.getStatus()).isEqualTo(0);
        assertThat(credential.getClientId()).startsWith("APP_");
        assertThat(credential.getClientSecretHash()).isNotBlank();

        assertThat(vo.getAppId()).isEqualTo(123L);
        assertThat(vo.getEnvironment()).isEqualTo("SANDBOX");
        assertThat(vo.getClientSecret()).isNotBlank();
    }

    @Test
    void submitGrantApplyThrowsWhenUserContextMissing() {
        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void reviewGrantApplyThrowsWhenUserContextMissing() {
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setStatus(0);
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        assertThatThrownBy(() -> service.reviewGrantApply(1L, 2, "驳回原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void revokeGrantThrowsWhenUserContextMissing() {
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setId(1L);
        grant.setStatus(1);
        when(baseMapper.selectById(1L)).thenReturn(grant);

        assertThatThrownBy(() -> service.revokeGrant(1L, "撤销原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void generateCredentialThrowsWhenUserContextMissing() {
        assertThatThrownBy(() -> service.generateCredential(123L, "SANDBOX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void listAppGrantsThrowsWhenTenantContextMissing() {
        assertThatThrownBy(() -> service.listAppGrants(123L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前租户信息失败");
    }

    @Test
    void listAppGrantsScopesQueryToCurrentTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        service.listAppGrants(123L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<OpenAppResourceGrantPo>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(baseMapper).selectList(captor.capture());
        var normal = captor.getValue().getExpression().getNormal();
        normal.get(2).getSqlSegment();
        normal.get(6).getSqlSegment();
        normal.get(10).getSqlSegment();
        normal.get(14).getSqlSegment();
        assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue())
                .getParamNameValuePairs().values()).contains(99L);
    }

    @Test
    void viewerCannotSubmitGrantApply() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorUserPo viewer = vendorMembership();
        viewer.setRole("VIEWER");
        when(vendorUserMapper.selectOne(any())).thenReturn(viewer);

        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前厂商用户仅可查看应用");
        verify(baseMapper, org.mockito.Mockito.times(0)).insert(any(OpenAppResourceGrantPo.class));
    }

    @Test
    void submitGrantApplyRejectsAppFromAnotherTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppPo otherTenantApp = ownedApp();
        otherTenantApp.setTenantId(100L);
        when(appMapper.selectOne(any())).thenReturn(otherTenantApp);

        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用不存在或无权申请授权");
    }

    @Test
    void viewerCannotGenerateCredential() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorUserPo viewer = vendorMembership();
        viewer.setRole("VIEWER");
        when(vendorUserMapper.selectOne(any())).thenReturn(viewer);

        assertThatThrownBy(() -> service.generateCredential(123L, "SANDBOX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前厂商用户仅可查看应用");
        verify(appCredentialMapper, org.mockito.Mockito.times(0)).insert(any(OpenAppCredentialPo.class));
    }

    @Test
    void nonAdminCannotReviewGrantApply() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setTenantId(99L);
        pending.setStatus(0);
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        assertThatThrownBy(() -> service.reviewGrantApply(1L, 1, "通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅管理员可审核授权申请");
        verify(authorizationRequestMapper, org.mockito.Mockito.times(0)).update(any(), any());
    }

    @Test
    void adminCannotReviewRequestFromAnotherTenant() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setTenantId(100L);
        pending.setStatus(0);
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        assertThatThrownBy(() -> service.reviewGrantApply(1L, 1, "通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权审核其他租户的授权申请");
        verify(authorizationRequestMapper, org.mockito.Mockito.times(0)).update(any(), any());
    }

    @Test
    void adminCanReviewRequestFromAnotherVendor() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setTenantId(99L);
        pending.setAppId(999L);
        pending.setStatus(0);
        pending.setEnvironment("SANDBOX");
        pending.setRequestData("[{\"resourceId\":456,\"scopes\":\"edu.teacher.read\",\"quota\":0,\"expireDays\":0}]");
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        service.reviewGrantApply(1L, 1, "通过");

        verify(authorizationRequestMapper).update(any(), any());
        verify(baseMapper).insert(any(OpenAppResourceGrantPo.class));
        verify(vendorUserMapper, org.mockito.Mockito.times(0)).selectOne(any());
    }

    @Test
    void adminCanListCredentialsAcrossVendors() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        when(appCredentialMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listCredentials(null)).isEmpty();

        verify(vendorUserMapper, org.mockito.Mockito.times(0)).selectList(any());
        verify(appMapper, org.mockito.Mockito.times(0)).selectList(any());
    }

    @Test
    void viewerListsOnlyAppsFromOwnVendor() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorUserPo viewer = vendorMembership();
        viewer.setRole("VIEWER");
        when(vendorUserMapper.selectList(any())).thenReturn(List.of(viewer));
        when(appMapper.selectList(any())).thenReturn(List.of(ownedApp()));
        when(baseMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listAppGrants(123L)).isEmpty();

        verify(vendorUserMapper).selectList(any());
        verify(appMapper).selectList(any());
        verify(baseMapper).selectList(any());
    }

    @Test
    void viewerListQueriesAreScopedToOwnVendorApps() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorUserPo viewer = vendorMembership();
        viewer.setRole("VIEWER");
        when(vendorUserMapper.selectList(any())).thenReturn(List.of(viewer));
        when(appMapper.selectList(any())).thenReturn(List.of(ownedApp()));
        when(authorizationRequestMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));
        when(appCredentialMapper.selectList(any())).thenReturn(List.of());

        service.listRequestPage(null, null, null, 1, 10);
        service.listCredentials(null);

        verify(vendorUserMapper, org.mockito.Mockito.times(2)).selectList(any());
        verify(appMapper, org.mockito.Mockito.times(2)).selectList(any());
    }

    @Test
    void rotateCredentialRejectsCredentialFromAnotherTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppCredentialPo credential = new OpenAppCredentialPo();
        credential.setId(10L);
        credential.setTenantId(100L);
        credential.setAppId(123L);
        credential.setStatus(0);
        when(appCredentialMapper.selectById(10L)).thenReturn(credential);

        assertThatThrownBy(() -> service.rotateCredential(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作其他租户应用凭证");
        verify(appCredentialMapper, org.mockito.Mockito.times(0)).updateById(any(OpenAppCredentialPo.class));
    }

    @Test
    void viewerCannotRevokeGrant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorUserPo viewer = vendorMembership();
        viewer.setRole("VIEWER");
        when(vendorUserMapper.selectOne(any())).thenReturn(viewer);
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setId(1L);
        grant.setTenantId(99L);
        grant.setAppId(123L);
        grant.setStatus(1);
        when(baseMapper.selectById(1L)).thenReturn(grant);

        assertThatThrownBy(() -> service.revokeGrant(1L, "撤销原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前厂商用户仅可查看应用");
        verify(baseMapper, org.mockito.Mockito.times(0)).updateById(any(OpenAppResourceGrantPo.class));
    }

    @Test
    void rotateCredentialRequiresOwnedAppAndPersistsReplacement() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppCredentialPo oldCredential = new OpenAppCredentialPo();
        oldCredential.setId(10L);
        oldCredential.setAppId(123L);
        oldCredential.setEnvironment("SANDBOX");
        oldCredential.setStatus(0);
        when(appCredentialMapper.selectById(10L)).thenReturn(oldCredential);

        AppCredentialVO replacement = service.rotateCredential(10L);

        assertThat(oldCredential.getStatus()).isEqualTo(2);
        assertThat(replacement.getAppId()).isEqualTo(123L);
        assertThat(replacement.getEnvironment()).isEqualTo("SANDBOX");
        assertThat(replacement.getClientSecret()).isNotBlank();
        verify(appCredentialMapper).updateById(oldCredential);
        verify(appCredentialMapper).insert(any(OpenAppCredentialPo.class));
    }

    @Test
    void validateCredentialContextReturnsEnvironmentWithoutExposingSecret() {
        OpenAppCredentialPo credential = new OpenAppCredentialPo();
        credential.setAppId(123L);
        credential.setClientId("sandbox-client");
        credential.setEnvironment("sandbox");
        credential.setStatus(0);
        credential.setClientSecretHash(new BCryptPasswordEncoder().encode("secret"));
        when(appCredentialMapper.selectOne(any())).thenReturn(credential);

        OpenAppAuthorizationService.CredentialContext context =
                service.validateCredentialContext("sandbox-client", "secret");

        assertThat(context.appId()).isEqualTo(123L);
        assertThat(context.clientId()).isEqualTo("sandbox-client");
        assertThat(context.environment()).isEqualTo("SANDBOX");
    }

    @Test
    void generateCredentialRejectsEnvironmentBeforeLifecycleGate() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppPo app = ownedApp();
        app.setLifecycleStatus(1);
        when(appMapper.selectOne(any())).thenReturn(app);

        assertThatThrownBy(() -> service.generateCredential(123L, "SANDBOX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用尚未开通沙箱环境");

        app.setLifecycleStatus(4);
        assertThatThrownBy(() -> service.generateCredential(123L, "PROD"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用尚未开通生产环境");
    }

    private static GrantApplyVO grantApplyVO() {
        GrantApplyVO vo = new GrantApplyVO();
        vo.setAppId(123L);
        vo.setEnvironment("SANDBOX");
        GrantApplyVO.ResourceApplyItem item = new GrantApplyVO.ResourceApplyItem();
        item.setResourceId(456L);
        item.setScopes("edu.teacher.read");
        item.setQuota(0L);
        item.setExpireDays(0);
        vo.setResources(List.of(item));
        vo.setApplyReason("申请理由");
        return vo;
    }

    private static LoginUser adminLoginUser() {
        return LoginUser.builder().userId(42L).tenantId(99L).roleKeys(Set.of("admin")).build();
    }

    private static OpenAppPo ownedApp() {
        OpenAppPo app = new OpenAppPo();
        app.setId(123L);
        app.setTenantId(99L);
        app.setVendorId(789L);
        app.setStatus(0);
        app.setLifecycleStatus(2);
        return app;
    }

    private static OpenVendorUserPo vendorMembership() {
        OpenVendorUserPo membership = new OpenVendorUserPo();
        membership.setTenantId(99L);
        membership.setVendorId(789L);
        membership.setUserId(42L);
        membership.setRole("OWNER");
        membership.setStatus(0);
        return membership;
    }

    private static OpenApiResourcePo publishedResource() {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(456L);
        resource.setStatus(0);
        resource.setScopeCode("edu.teacher.read");
        resource.setPublishStatus(2);
        resource.setAllowApply(1);
        return resource;
    }
}
