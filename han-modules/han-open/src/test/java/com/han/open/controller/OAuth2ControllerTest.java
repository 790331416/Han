package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.open.service.IOAuth2Service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2ControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void discoveryReturnsConcreteMetadata() {
        OAuth2Controller controller = new OAuth2Controller(mock(IOAuth2Service.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open/oauth2/.well-known/openid-configuration");
        request.setScheme("http");
        request.setServerName("example.test");
        request.setServerPort(19090);

        R<Object> response = controller.discovery(request);

        Map<String, Object> metadata = (Map<String, Object>) response.getData();
        assertThat(metadata.get("issuer")).isEqualTo("http://example.test:19090/open/oauth2");
        assertThat(metadata.get("authorization_endpoint")).isEqualTo("http://example.test:19090/open/oauth2/authorize");
        assertThat(metadata.get("token_endpoint")).isEqualTo("http://example.test:19090/open/oauth2/token");
    }
}
