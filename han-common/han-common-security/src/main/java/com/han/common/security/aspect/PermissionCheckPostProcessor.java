package com.han.common.security.aspect;

import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.InnerAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RequiresPermission;
import com.han.common.security.annotation.RequiresRole;
import com.han.common.security.config.PermissionCheckProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限校验后置处理器
 *
 * <p>在应用启动完成后扫描管理端控制器，检查请求映射方法是否缺少权限注解。
 *
 * <p><b>扫描范围：</b>
 * <ul>
 *   <li>所有标注了 {@link AdminAuth} 的控制器</li>
 *   <li>以及（默认开启）任何已经使用了权限注解的 {@link RestController}
 *       —— 用于覆盖未标注 {@code @AdminAuth} 但走 {@link RequiresPermission} 体系的管理端控制器</li>
 * </ul>
 *
 * <p><b>检查规则：</b>控制器内每个带 {@link RequestMapping} 系注解（含 {@code @GetMapping}
 * 等组合注解）的方法，必须在方法或类上带以下任一注解：
 * {@link PreAuthorize}、{@link RequiresPermission}、{@link RequiresRole}、
 * {@link InnerAuth}、{@link PermissionExempt}。
 *
 * <p><b>失败处理：</b>由 {@link PermissionCheckProperties#isFailFast()} 决定。
 * 开启时抛异常阻止启动，关闭时只输出违规清单告警。
 *
 * @author han
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PermissionCheckPostProcessor implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckPostProcessor.class);

    private static final String SEPARATOR = "========================================";

    /**
     * 视为「该方法已有访问控制」的注解。
     *
     * <p>{@code @RequiresLogin} 与 {@code @AllowClient} 没有对应切面实现，不计入。
     */
    private static final List<Class<? extends Annotation>> PERMISSION_ANNOTATIONS = List.of(
            PreAuthorize.class,
            RequiresPermission.class,
            RequiresRole.class,
            InnerAuth.class,
            PermissionExempt.class
    );

    private final PermissionCheckProperties properties;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();

        Map<String, Object> adminControllers = context.getBeansWithAnnotation(AdminAuth.class);
        Map<String, Object> targets = new LinkedHashMap<>(adminControllers);
        if (properties.isScanAnnotatedControllers()) {
            collectPermissionAnnotatedControllers(context, targets);
        }

        if (targets.isEmpty()) {
            log.debug("未发现需要权限门禁的控制器，跳过检查");
            return;
        }

        List<String> violations = new ArrayList<>();
        int checkedMethodCount = 0;

        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            String beanName = entry.getKey();
            Class<?> clazz = AopUtils.getTargetClass(entry.getValue());

            for (Method method : mappingMethods(clazz)) {
                checkedMethodCount++;

                if (!hasPermissionAnnotation(clazz, method)) {
                    String violation = String.format(
                            "[权限校验失败] Controller: %s (Bean: %s), Method: %s 缺少权限注解 "
                                    + "(@PreAuthorize / @RequiresPermission / @RequiresRole / @InnerAuth / @PermissionExempt)",
                            clazz.getSimpleName(),
                            beanName,
                            method.getName()
                    );
                    violations.add(violation);
                    continue;
                }

                // 记录豁免的方法（用于审计）
                PermissionExempt exempt = AnnotationUtils.findAnnotation(method, PermissionExempt.class);
                if (exempt != null) {
                    log.warn("[权限豁免] Controller: {}, Method: {}, Reason: {}",
                            clazz.getSimpleName(), method.getName(), exempt.value());
                }
            }
        }

        // 门禁曾因筛选条件恒为 false 而空转，0 个方法本身就是异常信号
        if (checkedMethodCount == 0) {
            log.error("[权限门禁异常] 命中 {} 个控制器却未扫描到任何请求映射方法，门禁很可能未按预期生效，请检查扫描逻辑",
                    targets.size());
        }

        if (violations.isEmpty()) {
            log.info("✅ 权限校验通过！共检查 {} 个控制器（@AdminAuth {} 个，按权限注解识别 {} 个），{} 个映射方法",
                    targets.size(), adminControllers.size(), targets.size() - adminControllers.size(), checkedMethodCount);
            return;
        }

        reportViolations(violations, checkedMethodCount);
    }

    /**
     * 把已经使用了权限注解、但没有标注 {@code @AdminAuth} 的 REST 控制器补进扫描范围。
     */
    private void collectPermissionAnnotatedControllers(ApplicationContext context, Map<String, Object> targets) {
        for (Map.Entry<String, Object> entry : context.getBeansWithAnnotation(RestController.class).entrySet()) {
            if (targets.containsKey(entry.getKey())) {
                continue;
            }
            Class<?> clazz = AopUtils.getTargetClass(entry.getValue());
            if (usesPermissionAnnotation(clazz)) {
                targets.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean usesPermissionAnnotation(Class<?> clazz) {
        for (Class<? extends Annotation> annotation : PERMISSION_ANNOTATIONS) {
            if (AnnotatedElementUtils.findMergedAnnotation(clazz, annotation) != null) {
                return true;
            }
        }
        for (Method method : mappingMethods(clazz)) {
            for (Class<? extends Annotation> annotation : PERMISSION_ANNOTATIONS) {
                if (AnnotationUtils.findAnnotation(method, annotation) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 取出控制器上全部请求映射方法。
     *
     * <p>用 {@code getMethods()} 而非 {@code getDeclaredMethods()}，以覆盖从父类继承且未被重写的映射方法；
     * 用 {@link RequestMapping} 的元注解查找覆盖 {@code @GetMapping} 等全部组合注解。
     */
    private List<Method> mappingMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            if (method.isBridge() || method.isSynthetic() || method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                methods.add(method);
            }
        }
        return methods;
    }

    /**
     * 判断方法是否已有访问控制。
     *
     * <p>方法级查找用 {@link AnnotationUtils#findAnnotation(Method, Class)}，会一并搜索父类同名方法，
     * 与 Spring Security 的实际生效口径一致；类级注解对类内全部方法生效，同样计入。
     */
    private boolean hasPermissionAnnotation(Class<?> clazz, Method method) {
        for (Class<? extends Annotation> annotation : PERMISSION_ANNOTATIONS) {
            if (AnnotationUtils.findAnnotation(method, annotation) != null
                    || AnnotatedElementUtils.findMergedAnnotation(clazz, annotation) != null) {
                return true;
            }
        }
        return false;
    }

    private void reportViolations(List<String> violations, int checkedMethodCount) {
        String errorMsg = String.join("\n", violations);
        String header = "\n\n"
                + SEPARATOR + "\n"
                + "权限校验失败！以下 " + violations.size() + " 个方法缺少权限注解"
                + "（共检查 " + checkedMethodCount + " 个映射方法）：\n"
                + SEPARATOR + "\n"
                + errorMsg + "\n"
                + SEPARATOR + "\n"
                + "请为每个方法添加 @PreAuthorize / @RequiresPermission / @PermissionExempt 注解\n"
                + SEPARATOR + "\n";

        if (properties.isFailFast()) {
            throw new IllegalStateException(header);
        }

        log.error("{}当前为告警模式（han.security.permission-check.fail-fast=false），未阻止启动。"
                + "存量违规清零后请将该开关置为 true。\n{}", header, SEPARATOR);
    }
}
