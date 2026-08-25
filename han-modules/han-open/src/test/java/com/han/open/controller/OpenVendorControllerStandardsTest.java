package com.han.open.controller;

import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorProfileUpdateVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenVendorControllerStandardsTest {

    @Test
    void vendorControllerUsesAdminAuthPermissionsAndSafeApplicationLogging() throws Exception {
        OpenVendorController controller = new OpenVendorController(mock(com.han.open.service.OpenVendorService.class));
        assertThat(controller).isNotNull();
        assertThat(OpenVendorController.class.isAnnotationPresent(AdminAuth.class)).isTrue();

        Method my = OpenVendorController.class.getDeclaredMethod("listMyVendors");
        assertThat(my.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("open:vendor:my");

        Method submit = OpenVendorController.class.getDeclaredMethod("submitApplication", VendorApplicationVO.class);
        assertThat(submit.isAnnotationPresent(RepeatSubmit.class)).isTrue();
        OperLog submitLog = submit.getAnnotation(OperLog.class);
        assertThat(submitLog).isNotNull();
        assertThat(submitLog.saveParams()).isFalse();
    }

    @Test
    void reviewAndStatusPreferPostAndKeepPutCompatibility() throws Exception {
        assertThat(requestMethods("reviewApplication", Long.class, Integer.class, String.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
        assertThat(requestMethods("updateStatus", Long.class, Integer.class, String.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
        assertThat(OpenVendorController.class.getDeclaredMethod("bindUser", Long.class, Long.class, String.class)
                .isAnnotationPresent(RepeatSubmit.class)).isTrue();
        assertThat(requestMethods("updateProfile", Long.class, VendorProfileUpdateVO.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
        assertThat(OpenVendorController.class.getDeclaredMethod("removeVendor", Long.class)
                .isAnnotationPresent(RepeatSubmit.class)).isTrue();
    }

    private static Set<RequestMethod> requestMethods(String methodName, Class<?>... parameterTypes)
            throws Exception {
        return Set.copyOf(Arrays.asList(OpenVendorController.class.getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(RequestMapping.class).method()));
    }
}
