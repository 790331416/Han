package com.han.open.controller;

import com.han.common.security.annotation.PermissionExempt;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiIntegrationExportControllerTest {

    @Test
    void everyExportIsExplicitlyPublicAndReadOnly() throws Exception {
        for (String name : new String[]{"openApi", "postman", "environment", "zip"}) {
            Method method = OpenApiIntegrationExportController.class.getDeclaredMethod(
                    name, String.class, jakarta.servlet.http.HttpServletRequest.class);
            assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
            assertThat(method.getAnnotation(PermissionExempt.class)).isNotNull();
        }
    }
}
