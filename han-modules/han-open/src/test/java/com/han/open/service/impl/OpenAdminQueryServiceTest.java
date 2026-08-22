package com.han.open.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.po.OpenAppCredentialPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.vo.OpenAppCredentialAdminVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenAppCredentialMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenAuthorizationRequestMapper;
import com.han.open.mapper.OpenVendorApplicationMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAdminQueryServiceTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void vendorApplicationPageExposesApplicationIdAndPagination() {
        OpenVendorApplicationMapper mapper = mock(OpenVendorApplicationMapper.class);
        Page<OpenVendorApplicationPo> page = new Page<>(2, 20);
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setId(101L);
        application.setVendorId(7L);
        application.setStatus(1);
        page.setRecords(List.of(application));
        page.setTotal(21);
        when(mapper.selectPage(any(), any())).thenReturn(page);

        OpenVendorServiceImpl service = new OpenVendorServiceImpl(
                mock(OpenVendorUserMapper.class), mapper, mock(OpenAppMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", mock(OpenVendorMapper.class));

        var result = service.listApplicationPage(7L, 1, 2, 20);

        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(21);
        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(101L);
            assertThat(row.getApplicationId()).isEqualTo(101L);
        });
    }

    @Test
    void authorizationRequestPageCopiesRequestId() {
        OpenAuthorizationRequestMapper mapper = mock(OpenAuthorizationRequestMapper.class);
        Page<OpenAuthorizationRequestPo> page = new Page<>(1, 10);
        OpenAuthorizationRequestPo request = new OpenAuthorizationRequestPo();
        request.setId(202L);
        request.setAppId(303L);
        request.setEnvironment("PROD");
        request.setStatus(0);
        page.setRecords(List.of(request));
        page.setTotal(1);
        when(mapper.selectPage(any(), any())).thenReturn(page);

        OpenAppAuthorizationServiceImpl service = new OpenAppAuthorizationServiceImpl(
                mapper, mock(OpenAppCredentialMapper.class), new ObjectMapper(), mock(OpenAppMapper.class),
                mock(OpenVendorUserMapper.class), mock(OpenApiResourceMapper.class), mock(OpenVendorMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", mock(OpenAppResourceGrantMapper.class));

        var result = service.listRequestPage(303L, 0, "prod", 1, 10);

        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(202L);
            assertThat(row.getRequestId()).isEqualTo(202L);
            assertThat(row.getEnvironment()).isEqualTo("PROD");
        });
    }

    @Test
    void credentialQueryReturnsOnlyNonSecretFields() {
        OpenAppCredentialMapper mapper = mock(OpenAppCredentialMapper.class);
        OpenAppCredentialPo credential = new OpenAppCredentialPo();
        credential.setId(404L);
        credential.setAppId(303L);
        credential.setTenantId(99L);
        credential.setEnvironment("SANDBOX");
        credential.setClientId("APP_SANDBOX");
        credential.setClientSecretHash("$2a$secret-hash");
        credential.setStatus(0);
        when(mapper.selectList(any())).thenReturn(List.of(credential));

        OpenAppAuthorizationServiceImpl service = new OpenAppAuthorizationServiceImpl(
                mock(OpenAuthorizationRequestMapper.class), mapper, new ObjectMapper(), mock(OpenAppMapper.class),
                mock(OpenVendorUserMapper.class), mock(OpenApiResourceMapper.class), mock(OpenVendorMapper.class));

        List<OpenAppCredentialAdminVO> rows = service.listCredentials(303L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(404L);
            assertThat(row.getCredentialId()).isEqualTo(404L);
            assertThat(row.getClientId()).isEqualTo("APP_SANDBOX");
        });
        assertThat(OpenAppCredentialAdminVO.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("clientSecret")
                        || field.getName().equals("clientSecretHash"));
    }
}
