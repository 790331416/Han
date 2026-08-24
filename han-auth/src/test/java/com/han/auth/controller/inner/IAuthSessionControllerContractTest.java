package com.han.auth.controller.inner;

import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.security.annotation.InnerAuth;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 会话撤销内部接口的路由与内部鉴权契约。 */
class IAuthSessionControllerContractTest {

    @Test
    void revokeEndpointIsInnerAuthAndUsesExpectedMapping() throws Exception {
        assertThat(IAuthSessionController.class.getAnnotation(RestController.class)).isNotNull();
        assertThat(IAuthSessionController.class.getAnnotation(InnerAuth.class)).isNotNull();

        RequestMapping mapping = IAuthSessionController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/inner/auth");

        Method method = IAuthSessionController.class.getDeclaredMethod("revokeSession", SessionRevokeRequest.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        assertThat(post).isNotNull();
        assertThat(post.value()).containsExactly("/session/revoke");
    }
}
