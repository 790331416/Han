package com.xuman.common.security.aspect;

import com.xuman.common.security.annotation.AdminAuth;
import com.xuman.common.security.annotation.InnerAuth;
import com.xuman.common.security.annotation.PermissionExempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 权限校验后置处理器
 * 
 * <p>在应用启动时扫描所有 Admin 控制器，检查是否缺少权限注解
 * 
 * <p><b>检查规则：</b>
 * <ul>
 *   <li>所有标注了 @AdminAuth 的控制器</li>
 *   <li>其中标注了 @Override 的请求映射方法</li>
 *   <li>必须有 @PreAuthorize、@InnerAuth 或 @PermissionExempt 注解</li>
 * </ul>
 * 
 * <p><b>失败处理：</b>
 * <ul>
 *   <li>如果发现违规方法，直接抛出异常，阻止应用启动</li>
 *   <li>在日志中输出详细的错误信息</li>
 * </ul>
 * 
 * @author XuMan
 * @since 1.0.0
 */
@Component
public class PermissionCheckPostProcessor implements ApplicationListener<ContextRefreshedEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionCheckPostProcessor.class);
    
    /**
     * 应用上下文刷新完成后执行权限校验
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        
        // 获取所有标注了 @AdminAuth 的控制器
        Map<String, Object> controllers = context.getBeansWithAnnotation(AdminAuth.class);
        
        if (controllers.isEmpty()) {
            log.debug("未发现 @AdminAuth 标注的控制器，跳过权限校验");
            return;
        }
        
        List<String> violations = new ArrayList<>();
        int checkedMethodCount = 0;
        
        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            String beanName = entry.getKey();
            Object controller = entry.getValue();
            Class<?> clazz = AopUtils.getTargetClass(controller);
            
            // 获取所有公共方法
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                // 检查是否为 Override 方法且有 RequestMapping 相关注解
                if (method.isAnnotationPresent(Override.class) && isRequestMappingMethod(method)) {
                    checkedMethodCount++;
                    
                    // 检查是否有权限注解
                    if (!hasPermissionAnnotation(method)) {
                        String violation = String.format(
                            "[权限校验失败] Controller: %s (Bean: %s), Method: %s 缺少权限注解 (@PreAuthorize 或 @PermissionExempt)",
                            clazz.getSimpleName(),
                            beanName,
                            method.getName()
                        );
                        violations.add(violation);
                        log.error(violation);
                    } else {
                        // 记录豁免的方法（用于审计）
                        if (method.isAnnotationPresent(PermissionExempt.class)) {
                            PermissionExempt exempt = method.getAnnotation(PermissionExempt.class);
                            log.warn("[权限豁免] Controller: {}, Method: {}, Reason: {}", 
                                clazz.getSimpleName(), 
                                method.getName(), 
                                exempt.value());
                        }
                    }
                }
            }
        }
        
        // 如果有违规，阻止应用启动
        if (!violations.isEmpty()) {
            String errorMsg = String.join("\n", violations);
            throw new IllegalStateException(
                "\n\n" +
                "========================================\n" +
                "权限校验失败！以下方法缺少权限注解：\n" +
                "========================================\n" +
                errorMsg + "\n" +
                "========================================\n" +
                "请为每个方法添加 @PreAuthorize 或 @PermissionExempt 注解\n" +
                "========================================\n"
            );
        }
        
        log.info("✅ 权限校验通过！共检查 {} 个 Admin 控制器，{} 个方法", 
            controllers.size(), checkedMethodCount);
    }
    
    /**
     * 判断方法是否为请求映射方法
     * 
     * @param method 方法对象
     * @return 是否为请求映射方法
     */
    private boolean isRequestMappingMethod(Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
            || method.isAnnotationPresent(GetMapping.class)
            || method.isAnnotationPresent(PostMapping.class)
            || method.isAnnotationPresent(PutMapping.class)
            || method.isAnnotationPresent(DeleteMapping.class)
            || method.isAnnotationPresent(PatchMapping.class);
    }
    
    /**
     * 判断方法是否有权限注解
     * 
     * @param method 方法对象
     * @return 是否有权限注解
     */
    private boolean hasPermissionAnnotation(Method method) {
        return method.isAnnotationPresent(PreAuthorize.class)
            || method.isAnnotationPresent(InnerAuth.class)
            || method.isAnnotationPresent(PermissionExempt.class);
    }
}
