package com.han.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A 层启动期权限门禁配置
 *
 * @see com.han.common.security.aspect.PermissionCheckPostProcessor
 */
@Data
@Component
@ConfigurationProperties(prefix = "han.security.permission-check")
public class PermissionCheckProperties {

    /**
     * 扫描到缺少权限注解的映射方法时，是否抛异常阻止应用启动。
     *
     * <p>默认 false（仅输出违规清单告警）。门禁此前因筛选条件恒为 false 而从未真正执行过，
     * 直接开启阻断会让存量违规接口所在的服务无法启动，因此先以告警模式收敛存量，
     * 存量清零后由部署侧显式置为 true。
     */
    private boolean failFast = false;

    /**
     * 是否把「未标注 {@code @AdminAuth} 但已使用任一权限注解」的控制器一并纳入扫描。
     *
     * <p>仓库内 han-tenant / han-workflow 使用 {@code @RequiresPermission} 且未标注
     * {@code @AdminAuth}，若只按 {@code @AdminAuth} 取 Bean，这些控制器会被永久排除在门禁之外。
     */
    private boolean scanAnnotatedControllers = true;
}
