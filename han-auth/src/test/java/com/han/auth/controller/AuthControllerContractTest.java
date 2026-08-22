package com.han.auth.controller;

import com.han.auth.domain.VendorPublicRegisterDTO;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 厂商公开入口的路由与网关豁免契约。 */
class AuthControllerContractTest {

    @Test
    void vendorPublicEndpointsAreExplicitlyExemptAndUseExpectedHttpMethods() throws Exception {
        assertPublicGet("vendorPublicKey");
        assertPublicGet("vendorStatus", String.class, String.class);
        assertPublicPost("vendorRegister", VendorPublicRegisterDTO.class);

        Method register = AuthController.class.getDeclaredMethod("vendorRegister", VendorPublicRegisterDTO.class);
        assertThat(register.getAnnotation(RepeatSubmit.class)).isNotNull();
    }

    private static void assertPublicGet(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AuthController.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(GetMapping.class)).as(methodName).isNotNull();
        assertThat(method.getAnnotation(PermissionExempt.class)).as(methodName).isNotNull();
    }

    private static void assertPublicPost(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AuthController.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PostMapping.class)).as(methodName).isNotNull();
        assertThat(method.getAnnotation(PermissionExempt.class)).as(methodName).isNotNull();
    }
}
