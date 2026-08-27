package com.han.open.service.impl;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiTestRunPo;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiTestRunMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.service.OpenAppAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiTestRunServiceImplTest {

    private OpenApiTestRunMapper runMapper;
    private OpenAppMapper appMapper;
    private OpenVendorUserMapper vendorUserMapper;
    private OpenApiResourceMapper resourceMapper;
    private OpenAppAuthorizationService authorizationService;
    private OpenApiTestRunServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        runMapper = mock(OpenApiTestRunMapper.class);
        appMapper = mock(OpenAppMapper.class);
        vendorUserMapper = mock(OpenVendorUserMapper.class);
        resourceMapper = mock(OpenApiResourceMapper.class);
        authorizationService = mock(OpenAppAuthorizationService.class);
        service = new OpenApiTestRunServiceImpl(appMapper, vendorUserMapper, resourceMapper, authorizationService);
        ReflectionTestUtils.setField(service, "baseMapper", runMapper);

        OpenAppPo app = new OpenAppPo();
        app.setId(10L);
        app.setTenantId(99L);
        app.setVendorId(7L);
        app.setStatus(0);
        when(appMapper.selectOne(any())).thenReturn(app);

        OpenVendorUserPo membership = new OpenVendorUserPo();
        membership.setTenantId(99L);
        membership.setVendorId(7L);
        membership.setUserId(42L);
        membership.setRole("DEVELOPER");
        membership.setStatus(0);
        when(vendorUserMapper.selectOne(any())).thenReturn(membership);

        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(20L);
        resource.setHttpMethod("get");
        resource.setPath("/open/api/v1/directory/teachers");
        resource.setScopeCode("edu.teacher.read");
        resource.setStatus(0);
        resource.setPublishStatus(2);
        resource.setAllowTest(1);
        when(resourceMapper.selectOne(any())).thenReturn(resource);
        when(authorizationService.hasPermission(10L, 20L, "SANDBOX", "edu.teacher.read")).thenReturn(true);
        when(runMapper.insert(any(OpenApiTestRunPo.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void addRebuildsMethodAndPathAndStoresOnlyRedactedSummary() {
        OpenApiTestRunDTO request = request();
        request.setTraceId("trace-42");

        var result = service.add(request);

        assertThat(result.getRequestMethod()).isEqualTo("GET");
        assertThat(result.getRequestPath()).isEqualTo("/open/api/v1/directory/teachers");
        assertThat(result.getResponseSize()).isEqualTo(128L);
        assertThat(result.getTraceId()).isEqualTo("trace-42");
        verify(runMapper).insert(any(OpenApiTestRunPo.class));
        assertThat(OpenApiTestRunPo.class.getDeclaredFields())
                .noneMatch(field -> List.of("requestBody", "responseBody", "headers", "clientSecret", "accessToken")
                        .contains(field.getName()));
    }

    @Test
    void clientCannotMarkServerErrorAsSuccess() {
        OpenApiTestRunDTO request = request();
        request.setStatusCode(500);
        request.setResult("SUCCESS");

        service.add(request);

        var captor = forClass(OpenApiTestRunPo.class);
        verify(runMapper).insert(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAIL");
    }

    @Test
    void httpSuccessWithBusinessFailureIsRecordedAsFailure() {
        OpenApiTestRunDTO request = request();
        request.setBusinessSuccess(false);

        service.add(request);

        var captor = forClass(OpenApiTestRunPo.class);
        verify(runMapper).insert(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAIL");
    }

    @Test
    void viewerCannotSubmitRun() {
        when(vendorUserMapper.selectOne(any())).thenReturn(member("VIEWER"));

        assertThatThrownBy(() -> service.add(request()))
                .hasMessage("当前厂商用户仅可查看调测记录");
    }

    @Test
    void disabledUnpublishedOrNotTestableResourceIsRejected() {
        when(resourceMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.add(request()))
                .hasMessage("资源不存在、未发布或不允许调测");
    }

    @Test
    void missingOrExpiredGrantIsRejected() {
        when(authorizationService.hasPermission(10L, 20L, "SANDBOX", "edu.teacher.read")).thenReturn(false);

        assertThatThrownBy(() -> service.add(request()))
                .hasMessage("应用未获得该环境的有效接口授权");
    }

    @Test
    void stoppedVendorCannotSubmitOnlineDebugRun() {
        doThrow(new com.han.common.core.exception.BusinessException("厂商不存在或已停用"))
                .when(authorizationService).requireActiveVendor(7L, 99L);

        assertThatThrownBy(() -> service.add(request()))
                .isInstanceOf(com.han.common.core.exception.BusinessException.class)
                .hasMessage("厂商不存在或已停用");
    }

    @Test
    void crossVendorApplicationIsRejected() {
        when(vendorUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.add(request()))
                .hasMessage("当前用户无权访问该厂商应用");
    }

    @Test
    void listUsesCurrentTenantAndVendorApplicationScope() {
        when(vendorUserMapper.selectList(any())).thenReturn(List.of(member("DEVELOPER")));
        when(appMapper.selectList(any())).thenReturn(List.of(app(10L, 7L)));
        OpenApiTestRunPo record = new OpenApiTestRunPo();
        record.setAppId(10L);
        record.setTenantId(99L);
        record.setRedactedSummary("status=200,bytes=3");
        when(runMapper.selectList(any())).thenReturn(List.of(record));

        var result = service.list((Long) null);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getAppId()).isEqualTo(10L);
            assertThat(row.getResponseSize()).isEqualTo(3L);
        });
        verify(runMapper).selectList(any());
    }

    private static OpenApiTestRunDTO request() {
        OpenApiTestRunDTO request = new OpenApiTestRunDTO();
        request.setAppId(10L);
        request.setResourceId(20L);
        request.setEnvironment("sandbox");
        request.setStatusCode(200);
        request.setDurationMs(18);
        request.setResponseSize(128L);
        request.setResult("success");
        return request;
    }

    private static OpenVendorUserPo member(String role) {
        OpenVendorUserPo member = new OpenVendorUserPo();
        member.setTenantId(99L);
        member.setVendorId(7L);
        member.setUserId(42L);
        member.setRole(role);
        member.setStatus(0);
        return member;
    }

    private static OpenAppPo app(Long id, Long vendorId) {
        OpenAppPo app = new OpenAppPo();
        app.setId(id);
        app.setTenantId(99L);
        app.setVendorId(vendorId);
        app.setStatus(0);
        return app;
    }
}
