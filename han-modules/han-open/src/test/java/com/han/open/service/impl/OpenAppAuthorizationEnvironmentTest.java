package com.han.open.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenAppCredentialMapper;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenAuthorizationRequestMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.mapper.OpenVendorMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权环境（SANDBOX/PROD）闭环测试：environment 从申请请求透传到授权审批与权限校验。
 */
class OpenAppAuthorizationEnvironmentTest {

    private OpenAuthorizationRequestMapper authorizationRequestMapper;
    private OpenAppCredentialMapper appCredentialMapper;
    private OpenAppMapper appMapper;
    private OpenVendorUserMapper vendorUserMapper;
    private OpenVendorMapper vendorMapper;
    private OpenApiResourceMapper resourceMapper;
    private OpenAppResourceGrantMapper baseMapper;
    private OpenAppAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        authorizationRequestMapper = mock(OpenAuthorizationRequestMapper.class);
        appCredentialMapper = mock(OpenAppCredentialMapper.class);
        appMapper = mock(OpenAppMapper.class);
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        vendorMapper = mock(OpenVendorMapper.class);
        resourceMapper = mock(OpenApiResourceMapper.class);
        baseMapper = mock(OpenAppResourceGrantMapper.class);
        service = new OpenAppAuthorizationServiceImpl(authorizationRequestMapper, appCredentialMapper,
                new ObjectMapper(), appMapper, vendorUserMapper, resourceMapper, vendorMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(appMapper.selectOne(any())).thenReturn(ownedApp());
        when(vendorUserMapper.selectOne(any())).thenReturn(vendorMembership());
        when(vendorMapper.selectOne(any())).thenReturn(activeVendor());
        when(resourceMapper.selectOne(any())).thenReturn(publishedResource());
        when(authorizationRequestMapper.update(any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void reviewApprovalPersistsGrantWithRequestEnvironment() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setAppId(123L);
        pending.setStatus(0);
        pending.setEnvironment("SANDBOX");
        pending.setReason("申请理由");
        pending.setRequestData("[{\"resourceId\":456,\"scopes\":\"edu.teacher.read\",\"quota\":0,\"expireDays\":0}]");
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);

        service.reviewGrantApply(1L, 1, "通过");

        ArgumentCaptor<OpenAppResourceGrantPo> captor = ArgumentCaptor.forClass(OpenAppResourceGrantPo.class);
        verify(baseMapper).insert(captor.capture());
        OpenAppResourceGrantPo grant = captor.getValue();
        assertThat(grant.getEnvironment()).isEqualTo("SANDBOX");
        assertThat(grant.getStatus()).isEqualTo(1);
        assertThat(grant.getAppId()).isEqualTo(123L);
        assertThat(grant.getResourceId()).isEqualTo(456L);
        assertThat(grant.getReviewerId()).isEqualTo(42L);
    }

    @Test
    void reviewApprovalUpdatesExistingPendingGrantInsteadOfInsertingDuplicate() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        OpenAuthorizationRequestPo pending = pendingRequest();
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pending);
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setId(88L);
        grant.setStatus(0);
        when(baseMapper.selectOne(any())).thenReturn(grant);

        service.reviewGrantApply(1L, 1, "通过");

        ArgumentCaptor<OpenAppResourceGrantPo> captor = ArgumentCaptor.forClass(OpenAppResourceGrantPo.class);
        verify(baseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(88L);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        verify(baseMapper, times(0)).insert(any(OpenAppResourceGrantPo.class));
    }

    @Test
    void reviewGrantApplyRejectsConcurrentReviewClaim() {
        SecurityContextHolder.setLoginUser(adminLoginUser());
        when(authorizationRequestMapper.selectById(1L)).thenReturn(pendingRequest());
        when(authorizationRequestMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.reviewGrantApply(1L, 1, "通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申请已被其他管理员审核，请刷新后重试");
        verify(baseMapper, times(0)).insert(any(OpenAppResourceGrantPo.class));
        verify(baseMapper, times(0)).updateById(any(OpenAppResourceGrantPo.class));
    }

    @Test
    void submitGrantApplyPersistsEnvironment() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());

        service.submitGrantApply(grantApplyVO("SANDBOX"));

        ArgumentCaptor<OpenAuthorizationRequestPo> captor = ArgumentCaptor.forClass(OpenAuthorizationRequestPo.class);
        verify(authorizationRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getEnvironment()).isEqualTo("SANDBOX");
    }

    @Test
    void submitGrantApplyCreatesTenantScopedPendingGrantWithCatalogScope() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());

        service.submitGrantApply(grantApplyVO("SANDBOX"));

        ArgumentCaptor<OpenAppResourceGrantPo> captor = ArgumentCaptor.forClass(OpenAppResourceGrantPo.class);
        verify(baseMapper).insert(captor.capture());
        OpenAppResourceGrantPo grant = captor.getValue();
        assertThat(grant.getTenantId()).isEqualTo(99L);
        assertThat(grant.getStatus()).isEqualTo(0);
        assertThat(grant.getEnvironment()).isEqualTo("SANDBOX");
        assertThat(grant.getScopes()).isEqualTo("edu.teacher.read");
    }

    @Test
    void submitGrantApplyReusesRevokedGrantRow() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppResourceGrantPo revoked = new OpenAppResourceGrantPo();
        revoked.setId(88L);
        revoked.setStatus(4);
        revoked.setTenantId(99L);
        when(baseMapper.selectOne(any())).thenReturn(revoked);

        service.submitGrantApply(grantApplyVO("PROD"));

        ArgumentCaptor<OpenAppResourceGrantPo> captor = ArgumentCaptor.forClass(OpenAppResourceGrantPo.class);
        verify(baseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(88L);
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
        assertThat(captor.getValue().getEnvironment()).isEqualTo("PROD");
    }

    @Test
    void sandboxAndProdApplicationsPersistIndependently() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());

        service.submitGrantApply(grantApplyVO("SANDBOX"));
        service.submitGrantApply(grantApplyVO("PROD"));

        ArgumentCaptor<OpenAuthorizationRequestPo> requestCaptor = ArgumentCaptor.forClass(OpenAuthorizationRequestPo.class);
        verify(authorizationRequestMapper, times(2)).insert(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(OpenAuthorizationRequestPo::getEnvironment)
                .containsExactly("SANDBOX", "PROD");
    }

    @Test
    void submitGrantApplyRejectsPendingGrantAsDuplicate() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppResourceGrantPo pending = new OpenAppResourceGrantPo();
        pending.setStatus(0);
        when(baseMapper.selectOne(any())).thenReturn(pending);

        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO("SANDBOX")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有待审核的授权申请");
    }

    @Test
    void hasPermissionFiltersByEnvironment() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setEnvironment("SANDBOX");
        grant.setStatus(1);
        grant.setScopes("edu.teacher.read,edu.device.read");
        when(baseMapper.selectOne(any())).thenReturn(grant);

        assertThat(service.hasPermission(123L, 456L, "SANDBOX", "edu.teacher.read")).isTrue();

        when(baseMapper.selectOne(any())).thenReturn(null);
        assertThat(service.hasPermission(123L, 456L, "PROD", "edu.teacher.read")).isFalse();

    }

    @Test
    void stoppedVendorCannotUseExistingGrant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenApiResourcePo resource = publishedResource();
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setEnvironment("SANDBOX");
        grant.setStatus(1);
        grant.setScopes("edu.teacher.read");
        when(baseMapper.selectOne(any())).thenReturn(grant);
        OpenVendorPo stopped = activeVendor();
        stopped.setStatus(6);
        when(vendorMapper.selectOne(any())).thenReturn(stopped);

        assertThat(service.hasPermission(123L, resource.getId(), "SANDBOX", "edu.teacher.read")).isFalse();
    }

    @Test
    void stoppedVendorFailsTheSharedRuntimeGate() {
        OpenVendorPo stopped = activeVendor();
        stopped.setStatus(6);
        when(vendorMapper.selectOne(any())).thenReturn(stopped);

        assertThatThrownBy(() -> service.requireActiveVendor(789L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商不存在或已停用");
    }

    @Test
    void hasPermissionThrowsWhenTenantContextMissing() {
        assertThatThrownBy(() -> service.hasPermission(123L, 456L, "SANDBOX", "edu.teacher.read"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前租户信息失败");
    }

    @Test
    void sameScopeCannotReuseGrantFromAnotherResourceCode() {
        OpenApiResourcePo teacherResource = publishedResource();
        teacherResource.setResourceCode("directory.teachers.read");
        OpenApiResourcePo otherResource = publishedResource();
        otherResource.setId(789L);
        otherResource.setResourceCode("directory.students.read");
        otherResource.setScopeCode("edu.teacher.read");
        when(resourceMapper.selectList(any())).thenReturn(List.of(teacherResource), List.of(otherResource));

        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setResourceId(456L);
        grant.setEnvironment("SANDBOX");
        grant.setStatus(1);
        grant.setScopes("edu.teacher.read");
        when(baseMapper.selectOne(any())).thenReturn(grant, null);

        assertThat(service.resolveAuthorizedDataScope(
                99L, 123L, "SANDBOX", "edu.teacher.read", "directory.teachers.read"))
                .isEqualTo("");
        assertThat(service.resolveAuthorizedDataScope(
                99L, 123L, "SANDBOX", "edu.teacher.read", "directory.students.read"))
                .isNull();
    }

    @Test
    void submitGrantApplyRejectsClientScopeOutsideResourceCatalog() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        GrantApplyVO apply = grantApplyVO("SANDBOX");
        apply.getResources().get(0).setScopes("admin.root");

        assertThatThrownBy(() -> service.submitGrantApply(apply))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申请Scope必须匹配资源目录Scope");
    }

    @Test
    void submitGrantApplyRejectsInvalidEnvironment() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        GrantApplyVO apply = grantApplyVO("DEV");

        assertThatThrownBy(() -> service.submitGrantApply(apply))
                .isInstanceOf(BusinessException.class)
                .hasMessage("环境类型仅支持SANDBOX或PROD");
    }

    @Test
    void submitGrantApplyRejectsUserOutsideVendor() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(vendorUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO("SANDBOX")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是该厂商成员，无权申请应用授权");
    }

    @Test
    void reviewGrantApplyRejectsUnsupportedStatusBeforeMutation() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());

        assertThatThrownBy(() -> service.reviewGrantApply(1L, 0, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("审核状态仅支持通过或驳回");
        verify(authorizationRequestMapper, times(0)).update(any(), any());
    }

    @Test
    void submitGrantApplyRejectsUnpublishedResource() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        when(resourceMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.submitGrantApply(grantApplyVO("SANDBOX")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资源不存在、未发布或不允许申请");
    }

    private static OpenAppPo ownedApp() {
        OpenAppPo app = new OpenAppPo();
        app.setId(123L);
        app.setTenantId(99L);
        app.setVendorId(789L);
        app.setStatus(0);
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

    private static OpenVendorPo activeVendor() {
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(789L);
        vendor.setTenantId(99L);
        vendor.setStatus(4);
        vendor.setDelFlag(0);
        return vendor;
    }

    private static OpenApiResourcePo publishedResource() {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(456L);
        resource.setResourceCode("directory.teachers.read");
        resource.setStatus(0);
        resource.setScopeCode("edu.teacher.read");
        resource.setPublishStatus(2);
        resource.setAllowApply(1);
        return resource;
    }

    private static OpenAuthorizationRequestPo pendingRequest() {
        OpenAuthorizationRequestPo pending = new OpenAuthorizationRequestPo();
        pending.setId(1L);
        pending.setTenantId(99L);
        pending.setAppId(123L);
        pending.setStatus(0);
        pending.setEnvironment("SANDBOX");
        pending.setReason("申请理由");
        pending.setApplicantId(42L);
        pending.setRequestData("[{\"resourceId\":456,\"scopes\":\"edu.teacher.read\",\"quota\":0,\"expireDays\":0}]");
        return pending;
    }

    private static GrantApplyVO grantApplyVO(String environment) {
        GrantApplyVO vo = new GrantApplyVO();
        vo.setAppId(123L);
        vo.setEnvironment(environment);
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
}
