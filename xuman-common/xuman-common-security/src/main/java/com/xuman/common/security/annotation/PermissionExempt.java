package com.xuman.common.security.annotation;

import java.lang.annotation.*;

/**
 * 权限豁免注解
 * 
 * <p>用于标注某些特殊方法可以不需要权限校验（如公开接口）
 * 
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>公开API接口（如注册、忘记密码等）</li>
 *   <li>健康检查接口</li>
 *   <li>其他无需认证的特殊接口</li>
 * </ul>
 * 
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>必须填写豁免原因，便于代码审查和安全审计</li>
 *   <li>谨慎使用，避免滥用导致安全风险</li>
 *   <li>豁免的接口仍需在业务层做必要的安全校验</li>
 * </ul>
 * 
 * @author XuMan
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PermissionExempt {
    
    /**
     * 豁免原因说明（必填，用于审计）
     * 
     * <p>示例：
     * <ul>
     *   <li>"公开接口，供前端未登录状态调用"</li>
     *   <li>"健康检查接口，无需权限"</li>
     *   <li>"内部系统对接接口，已在Gateway层做鉴权"</li>
     * </ul>
     * 
     * @return 豁免原因
     */
    String value();
}
