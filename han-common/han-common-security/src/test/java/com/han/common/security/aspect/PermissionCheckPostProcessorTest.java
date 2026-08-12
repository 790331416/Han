package com.han.common.security.aspect;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RequiresPermission;
import com.han.common.security.config.PermissionCheckProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动期权限门禁行为测试。
 *
 * <p>对应工单 S-01（门禁因 {@code @Override} 是 SOURCE 保留而恒不生效）与
 * S-03（{@code @PreAuthorize} 与 {@code @RequiresPermission} 两套体系互不识别）。
 */
class PermissionCheckPostProcessorTest {

    @Test
    @DisplayName("S-01：未标 @Override 的映射方法缺少权限注解时会被判定为违规")
    void shouldDetectViolationOnNonOverrideMappingMethod() {
        assertThatThrownBy(() -> runCheck(failFast(), AdminControllerWithGap.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AdminControllerWithGap")
                .hasMessageContaining("listAll");
    }

    @Test
    @DisplayName("S-01：全部映射方法都有权限注解时通过")
    void shouldPassWhenEveryMappingMethodIsAnnotated() {
        assertThatCode(() -> runCheck(failFast(), CompliantAdminController.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S-01：fail-fast 关闭时只告警，不阻止启动")
    void shouldNotBlockStartupWhenFailFastDisabled() {
        assertThatCode(() -> runCheck(new PermissionCheckProperties(), AdminControllerWithGap.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S-03：@RequiresPermission 被认可为有效权限注解")
    void shouldAcceptRequiresPermissionAsPermissionAnnotation() {
        assertThatCode(() -> runCheck(failFast(), CompliantRequiresPermissionController.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S-03：未标 @AdminAuth 但使用了 @RequiresPermission 的控制器也纳入扫描")
    void shouldScanControllerWithoutAdminAuthButUsingRequiresPermission() {
        assertThatThrownBy(() -> runCheck(failFast(), TenantStyleController.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantStyleController")
                .hasMessageContaining("listAll");
    }

    @Test
    @DisplayName("S-03：关闭 scan-annotated-controllers 后恢复为只扫 @AdminAuth")
    void shouldOnlyScanAdminAuthWhenInferenceDisabled() {
        PermissionCheckProperties properties = failFast();
        properties.setScanAnnotatedControllers(false);

        assertThatCode(() -> runCheck(properties, TenantStyleController.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("无权限注解的普通控制器不会被误判为管理端控制器")
    void shouldIgnorePlainControllerWithoutAnyPermissionAnnotation() {
        assertThatCode(() -> runCheck(failFast(), PublicController.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@PermissionExempt 视为已声明豁免，不计违规")
    void shouldTreatPermissionExemptAsDeclared() {
        assertThatCode(() -> runCheck(failFast(), ExemptAdminController.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("继承自父类且未被重写的映射方法同样纳入检查")
    void shouldCheckInheritedMappingMethods() {
        assertThatThrownBy(() -> runCheck(failFast(), ChildAdminController.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inheritedGap");
    }

    private PermissionCheckProperties failFast() {
        PermissionCheckProperties properties = new PermissionCheckProperties();
        properties.setFailFast(true);
        return properties;
    }

    private void runCheck(PermissionCheckProperties properties, Class<?>... controllers) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            if (controllers.length > 0) {
                context.register(controllers);
            }
            context.refresh();

            PermissionCheckPostProcessor processor = new PermissionCheckPostProcessor(properties);
            processor.onApplicationEvent(new ContextRefreshedEvent(context));
        }
    }

    @Test
    @DisplayName("无任何目标控制器时静默跳过")
    void shouldSkipWhenNoTargetController() {
        assertThatCode(() -> runCheck(failFast())).doesNotThrowAnyException();
        assertThat(new PermissionCheckProperties().isFailFast()).isFalse();
    }

    // ==================== 测试用控制器 ====================

    @AdminAuth
    @RestController
    @RequestMapping("/test/gap")
    static class AdminControllerWithGap {

        @GetMapping("/list")
        @PreAuthorize("@ss.hasAuthority('test:list')")
        public R<Void> list() {
            return R.ok();
        }

        /** 无 @Override、无权限注解：修复前恒被跳过，修复后必须报违规 */
        @GetMapping("/all")
        public R<Void> listAll() {
            return R.ok();
        }
    }

    @AdminAuth
    @RestController
    @RequestMapping("/test/compliant")
    static class CompliantAdminController {

        @GetMapping("/list")
        @PreAuthorize("@ss.hasAuthority('test:list')")
        public R<Void> list() {
            return R.ok();
        }

        @PostMapping("/edit")
        @PreAuthorize("@ss.hasAuthority('test:edit')")
        public R<Void> edit() {
            return R.ok();
        }
    }

    @AdminAuth
    @RestController
    @RequestMapping("/test/mixed")
    static class CompliantRequiresPermissionController {

        @GetMapping("/list")
        @RequiresPermission("test:list")
        public R<Void> list() {
            return R.ok();
        }
    }

    /** 模拟 han-tenant / han-workflow：只有 @RequiresPermission，没有 @AdminAuth */
    @RestController
    @RequestMapping("/test/tenant")
    static class TenantStyleController {

        @GetMapping("/list")
        @RequiresPermission("tenant:list")
        public R<Void> list() {
            return R.ok();
        }

        @GetMapping("/all")
        public R<Void> listAll() {
            return R.ok();
        }
    }

    /** 完全不使用权限注解的公开控制器，不应被推断为管理端控制器 */
    @RestController
    @RequestMapping("/test/public")
    static class PublicController {

        @GetMapping("/ping")
        public R<Void> ping() {
            return R.ok();
        }
    }

    @AdminAuth
    @RestController
    @RequestMapping("/test/exempt")
    static class ExemptAdminController {

        @GetMapping("/open")
        @PermissionExempt("登录前入口，网关白名单放行")
        public R<Void> open() {
            return R.ok();
        }
    }

    static class ParentAdminController {

        @GetMapping("/inherited")
        public R<Void> inheritedGap() {
            return R.ok();
        }
    }

    @AdminAuth
    @RestController
    @RequestMapping("/test/child")
    static class ChildAdminController extends ParentAdminController {

        @GetMapping("/list")
        @PreAuthorize("@ss.hasAuthority('test:list')")
        public R<Void> list() {
            return R.ok();
        }
    }
}
