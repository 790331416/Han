package com.han.open.controller;

import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.InnerAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.controller.inner.IOpenVendorController;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.dto.OpenAppStatusUpdateRequest;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;
import com.han.open.domain.vo.VendorApplicationVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPlatformControllerStandardsTest {

    @Test
    void adminWriteEndpointsUseRepeatSubmitAndSafeOperationLogs() throws Exception {
        assertThat(OpenApiResourceController.class.isAnnotationPresent(AdminAuth.class)).isTrue();
        assertThat(OpenAppAuthorizationController.class.isAnnotationPresent(AdminAuth.class)).isTrue();
        assertThat(OpenAppController.class.isAnnotationPresent(AdminAuth.class)).isTrue();
        assertThat(OpenApiTestRunController.class.isAnnotationPresent(AdminAuth.class)).isTrue();

        assertWriteLogged(OpenApiResourceController.class, "add", true, OpenApiResourcePo.class);
        assertWriteLogged(OpenAppController.class, "add", false, com.han.open.domain.dto.OpenAppDTO.class);
        assertWriteLogged(OpenAppController.class, "resetSecret", false, Long.class);
        assertWriteLogged(OpenAppAuthorizationController.class, "generateCredential", false, Long.class, String.class);
        assertWriteLogged(OpenAppAuthorizationController.class, "rotateCredential", false, Long.class);

        OperLog resetLog = OpenAppController.class.getDeclaredMethod("resetSecret", Long.class)
                .getAnnotation(OperLog.class);
        assertThat(resetLog.saveParams()).isFalse();
        assertThat(resetLog.saveResult()).isFalse();
    }

    @Test
    void allOpenPlatformBusinessWritesHavePostPermissionAndReplayProtection() throws Exception {
        List<Endpoint> resourceWrites = List.of(
                endpoint(OpenApiResourceController.class, "add", true, true, OpenApiResourcePo.class),
                endpoint(OpenApiResourceController.class, "edit", true, true, OpenApiResourcePo.class),
                endpoint(OpenApiResourceController.class, "remove", true, true, Long.class),
                endpoint(OpenApiResourceController.class, "changeStatus", true, true, OpenApiResourcePo.class),
                endpoint(OpenApiResourceController.class, "offline", true, true, Long.class),
                endpoint(OpenApiResourceController.class, "createDraftVersion", true, true,
                        Long.class, OpenApiResourceVersionVO.class),
                endpoint(OpenApiResourceController.class, "updateDraftVersion", true, true,
                        OpenApiResourceVersionVO.class),
                endpoint(OpenApiResourceController.class, "publishVersion", true, true, Long.class),
                endpoint(OpenApiResourceController.class, "deprecateVersion", true, true, Long.class)
        );
        List<Endpoint> appWrites = List.of(
                endpoint(OpenAppController.class, "add", true, true, OpenAppDTO.class),
                endpoint(OpenAppController.class, "edit", true, true, Map.class),
                endpoint(OpenAppController.class, "remove", true, true, Long.class),
                endpoint(OpenAppController.class, "resetSecret", true, true, Long.class),
                endpoint(OpenAppController.class, "changeStatus", true, true,
                        OpenAppStatusUpdateRequest.class),
                endpoint(OpenAppController.class, "changeLifecycleStatus", true, true,
                        Long.class, Integer.class)
        );
        List<Endpoint> authorizationWrites = List.of(
                endpoint(OpenAppAuthorizationController.class, "submitGrantApply", true, true,
                        GrantApplyVO.class),
                endpoint(OpenAppAuthorizationController.class, "reviewGrantApply", true, true,
                        Long.class, Integer.class, String.class),
                endpoint(OpenAppAuthorizationController.class, "revokeGrant", true, true,
                        Long.class, String.class),
                endpoint(OpenAppAuthorizationController.class, "generateCredential", true, true,
                        Long.class, String.class),
                endpoint(OpenAppAuthorizationController.class, "rotateCredential", true, true, Long.class)
        );
        List<Endpoint> debugWrites = List.of(
                endpoint(OpenApiTestRunController.class, "add", true, false, OpenApiTestRunDTO.class)
        );
        List<Endpoint> vendorWrites = List.of(
                endpoint(OpenVendorController.class, "submitApplication", true, true, VendorApplicationVO.class),
                endpoint(OpenVendorController.class, "reviewApplication", true, true,
                        Long.class, Integer.class, String.class),
                endpoint(OpenVendorController.class, "bindUser", true, true,
                        Long.class, Long.class, String.class),
                endpoint(OpenVendorController.class, "updateStatus", true, true,
                        Long.class, Integer.class, String.class)
        );
        List<Endpoint> adminWrites = List.of(
                endpoint(OpenApiAdminController.class, "refreshResourceCache", false, false)
        );

        Stream.of(resourceWrites, appWrites, authorizationWrites, debugWrites, vendorWrites, adminWrites)
                .flatMap(List::stream)
                .forEach(OpenPlatformControllerStandardsTest::assertEndpoint);
    }

    @Test
    void publicAndInternalBoundariesAreExplicit() throws Exception {
        List<Endpoint> publicEndpoints = List.of(
                endpoint(OAuth2Controller.class, "authorize", false, false,
                        String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class),
                endpoint(OAuth2Controller.class, "authorizeConfirm", false, false,
                        com.han.open.domain.dto.OAuth2AuthorizeDTO.class, Boolean.class),
                endpoint(OAuth2Controller.class, "token", false, false,
                        String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, String.class, String.class),
                endpoint(OAuth2Controller.class, "tokenJson", false, false,
                        com.han.open.domain.dto.OAuth2TokenDTO.class),
                endpoint(OAuth2Controller.class, "revoke", false, false,
                        String.class, String.class, String.class, String.class),
                endpoint(OAuth2Controller.class, "introspect", false, false,
                        String.class, String.class, String.class),
                endpoint(OAuth2Controller.class, "userInfo", false, false, String.class),
                endpoint(OAuth2Controller.class, "discovery", false, false,
                        jakarta.servlet.http.HttpServletRequest.class),
                endpoint(SsoController.class, "ssoLogin", false, false,
                        String.class, String.class, String.class),
                endpoint(SsoController.class, "ssoLogout", false, false, String.class, String.class),
                endpoint(SsoController.class, "validateTicket", false, false,
                        String.class, String.class, String.class),
                endpoint(SsoController.class, "checkLogin", false, false)
        );
        publicEndpoints.forEach(OpenPlatformControllerStandardsTest::assertPermissionExempt);

        assertThat(IOpenVendorController.class.isAnnotationPresent(InnerAuth.class)).isTrue();
        assertThat(IOpenVendorController.class.getDeclaredMethod("createPortalApplication",
                com.han.api.open.domain.OpenVendorApplicationCreateDTO.class)
                .getAnnotation(PostMapping.class)).isNotNull();
        assertThat(IOpenVendorController.class.getDeclaredMethod("queryPortalApplication",
                String.class).getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    void allProtectedAndBearerReadEndpointsHaveExplicitGetMappings() throws Exception {
        List<Endpoint> protectedReads = List.of(
                endpoint(OpenAppController.class, "list", false, false,
                        com.han.open.domain.query.OpenAppQuery.class),
                endpoint(OpenAppController.class, "getInfo", false, false, Long.class),
                endpoint(OpenApiResourceController.class, "list", false, false, boolean.class),
                endpoint(OpenApiResourceController.class, "getDetail", false, false, Long.class),
                endpoint(OpenVendorController.class, "list", false, false,
                        String.class, Integer.class, Integer.class, Integer.class),
                endpoint(OpenVendorController.class, "applications", false, false,
                        Long.class, Integer.class, Integer.class, Integer.class),
                endpoint(OpenVendorController.class, "getDetail", false, false, Long.class),
                endpoint(OpenVendorController.class, "listMyVendors", false, false),
                endpoint(OpenAppAuthorizationController.class, "requests", false, false,
                        Long.class, Integer.class, String.class, Integer.class, Integer.class),
                endpoint(OpenAppAuthorizationController.class, "credentials", false, false, Long.class),
                endpoint(OpenAppAuthorizationController.class, "listAppGrants", false, false, Long.class),
                endpoint(OpenApiTestRunController.class, "list", false, false, Long.class)
        );
        protectedReads.forEach(endpoint -> assertGetEndpoint(endpoint, true));

        assertGetEndpoint(endpoint(OpenDirectoryController.class, "teachers", false, false,
                String.class, Long.class, Integer.class, java.time.LocalDateTime.class,
                Integer.class, Integer.class), false);
        assertGetEndpoint(endpoint(OpenDirectoryController.class, "students", false, false,
                String.class, Long.class, Integer.class, java.time.LocalDateTime.class,
                Integer.class, Integer.class), false);
        assertGetEndpoint(endpoint(OpenDirectoryController.class, "devices", false, false,
                String.class, Long.class, Integer.class, java.time.LocalDateTime.class,
                Integer.class, Integer.class), false);
    }

    @Test
    void authorizationWritesPreferPostAndKeepPutCompatibility() throws Exception {
        assertThat(requestMethods("reviewGrantApply", Long.class, Integer.class, String.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
        assertThat(requestMethods("revokeGrant", Long.class, String.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
        assertThat(requestMethods("rotateCredential", Long.class))
                .containsExactlyInAnyOrder(RequestMethod.POST, RequestMethod.PUT);
    }

    private static Endpoint endpoint(Class<?> controller, String methodName,
                                     boolean requireRepeatSubmit, boolean requireOperLog,
                                     Class<?>... parameterTypes) {
        return new Endpoint(controller, methodName, requireRepeatSubmit, requireOperLog, parameterTypes);
    }

    private static void assertEndpoint(Endpoint endpoint) {
        try {
            Method method = endpoint.controller().getDeclaredMethod(endpoint.methodName(), endpoint.parameterTypes());
            assertThat(requestMethods(method)).as(endpoint.description()).contains(RequestMethod.POST);
            assertThat(method.getAnnotation(PreAuthorize.class)).as(endpoint.description())
                    .isNotNull();
            if (endpoint.requireRepeatSubmit()) {
                assertThat(method.getAnnotation(RepeatSubmit.class)).as(endpoint.description())
                        .isNotNull();
            }
            if (endpoint.requireOperLog()) {
                assertThat(method.getAnnotation(OperLog.class)).as(endpoint.description())
                        .isNotNull();
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(endpoint.description(), e);
        }
    }

    private static void assertPermissionExempt(Endpoint endpoint) {
        try {
            Method method = endpoint.controller().getDeclaredMethod(endpoint.methodName(), endpoint.parameterTypes());
            assertThat(method.getAnnotation(PermissionExempt.class))
                    .as(endpoint.description()).isNotNull();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(endpoint.description(), e);
        }
    }

    private static void assertGetEndpoint(Endpoint endpoint, boolean requirePermission) {
        try {
            Method method = endpoint.controller().getDeclaredMethod(endpoint.methodName(), endpoint.parameterTypes());
            assertThat(method.getAnnotation(GetMapping.class)).as(endpoint.description()).isNotNull();
            if (requirePermission) {
                assertThat(method.getAnnotation(PreAuthorize.class)).as(endpoint.description()).isNotNull();
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(endpoint.description(), e);
        }
    }

    private static void assertWriteLogged(Class<?> controller, String methodName, boolean saveResult,
                                          Class<?>... parameterTypes) throws Exception {
        Method method = controller.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(RepeatSubmit.class)).isTrue();
        OperLog operLog = method.getAnnotation(OperLog.class);
        assertThat(operLog).isNotNull();
        assertThat(operLog.saveResult()).isEqualTo(saveResult);
    }

    private static Set<RequestMethod> requestMethods(String methodName, Class<?>... parameterTypes)
            throws Exception {
        return requestMethods(OpenAppAuthorizationController.class.getDeclaredMethod(methodName, parameterTypes));
    }

    private static Set<RequestMethod> requestMethods(Method method) {
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            return Set.of(RequestMethod.POST);
        }
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).as("请求映射缺失: %s", method).isNotNull();
        return Set.copyOf(Arrays.asList(requestMapping.method()));
    }

    private record Endpoint(Class<?> controller, String methodName,
                            boolean requireRepeatSubmit, boolean requireOperLog,
                            Class<?>... parameterTypes) {
        String description() {
            return controller.getSimpleName() + "#" + methodName;
        }
    }
}
