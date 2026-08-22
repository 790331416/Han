package com.han.open.converter;

import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenAppCredentialPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.AppGrantDetailVO;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenAuthorizationRequestAdminVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPlatformConverterTest {

    @Test
    void resourceConverterMapsOnlyExplicitCatalogFields() {
        OpenApiResourcePo source = new OpenApiResourcePo();
        source.setId(7L);
        source.setResourceCode("directory.teachers.read");
        source.setResourceName("教师目录");
        source.setHttpMethod("GET");
        source.setPath("/open/api/v1/directory/teachers");
        source.setScopeCode("edu.teacher.read");
        source.setStatus(0);
        source.setAllowTest(1);

        OpenApiResourceDetailVO target = OpenApiResourceConverter.toDetailVO(source, java.util.List.of());

        assertThat(target.getId()).isEqualTo(7L);
        assertThat(target.getResourceCode()).isEqualTo("directory.teachers.read");
        assertThat(target.getPath()).isEqualTo("/open/api/v1/directory/teachers");
        assertThat(target.getAllowTest()).isEqualTo(1);
        assertThat(target.getVersions()).isEmpty();
    }

    @Test
    void authorizationConverterMapsSensitiveCredentialWithoutHash() {
        OpenAppCredentialPo source = new OpenAppCredentialPo();
        source.setId(9L);
        source.setAppId(10L);
        source.setEnvironment("PROD");
        source.setClientId("APP_TEST");
        source.setClientSecretHash("$2a$10$should-never-leak");
        source.setStatus(0);
        source.setExpireAt(LocalDateTime.now().plusDays(1));

        AppCredentialVO target = OpenAppAuthorizationConverter.toCredentialVO(source, "one-time-secret");

        assertThat(target.getClientId()).isEqualTo("APP_TEST");
        assertThat(target.getClientSecret()).isEqualTo("one-time-secret");
        assertThat(target.getClass().getDeclaredFields()).extracting("name")
                .doesNotContain("clientSecretHash");
    }

    @Test
    void authorizationConverterMapsRequestAndGrantFields() {
        OpenAuthorizationRequestPo request = new OpenAuthorizationRequestPo();
        request.setId(11L);
        request.setAppId(12L);
        request.setEnvironment("SANDBOX");
        request.setStatus(0);
        request.setReason("需要教师目录");
        OpenAuthorizationRequestAdminVO requestVO = OpenAppAuthorizationConverter.toRequestAdminVO(request);

        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setId(13L);
        grant.setAppId(12L);
        grant.setResourceId(14L);
        grant.setEnvironment("SANDBOX");
        grant.setScopes("edu.teacher.read");
        AppGrantDetailVO grantVO = OpenAppAuthorizationConverter.toGrantDetailVO(grant);

        assertThat(requestVO.getRequestId()).isEqualTo(11L);
        assertThat(requestVO.getReason()).isEqualTo("需要教师目录");
        assertThat(grantVO.getResourceId()).isEqualTo(14L);
        assertThat(grantVO.getScopes()).isEqualTo("edu.teacher.read");
    }
}
