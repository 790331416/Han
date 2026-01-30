package com.xuman.common.security.annotation;

import java.lang.annotation.*;

/**
 * 权限豁免注解
 * 
 * <p><b>用途：</b>
 * <ul>
 *   <li>标识不需要权限校验的公开方法（如验证码、公告等）
 *   <li>避免启动时权限校验误报
 *   <li>提供豁免原因说明，便于审计
 * </ul>
 * 
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>公开接口：验证码、公告、字典等
 *   <li>登录前可访问：找回密码、注册等
 *   <li>特殊业务逻辑：需要在方法内动态判断权限
 * </ul>
 * 
 * <p><b>使用示例：</b>
 * <pre>
 * &#64;AdminAuth
 * &#64;RestController("adminSysUserController")
 * &#64;RequestMapping("/admin/user")
 * public class ASysUserController extends BSysUserController {
 *     
 *     // ✅ 有@PreAuthorize，正常
 *     &#64;Override
 *     &#64;GetMapping("/list")
 *     &#64;PreAuthorize("&#64;ss.hasAuthority(&#64;Auth.SYS_USER_LIST)")
 *     public R&lt;PageResult&lt;SysUserDto&gt;&gt; list(SysUserQuery query) {
 *         return super.list(query);
 *     }
 *     
 *     // ✅ 有@PermissionExempt，豁免检查
 *     &#64;GetMapping("/public/info")
 *     &#64;PermissionExempt("公开接口，供前端未登录状态调用")
 *     public R&lt;SysUserDto&gt; publicInfo() {
 *         return R.ok(baseService.getPublicInfo());
 *     }
 *     
 *     // ❌ 既无@PreAuthorize也无@PermissionExempt，启动时报错
 *     // &#64;GetMapping("/test")
 *     // public R&lt;Void&gt; test() {
 *     //     return R.ok();
 *     // }
 * }
 * </pre>
 * 
 * <p><b>启动日志：</b>
 * <pre>
 * ⚠️ [权限豁免] Controller: ASysUserController, Method: publicInfo, Reason: 公开接口，供前端未登录状态调用
 * </pre>
 * 
 * @author XuMan Team
 * @see AdminAuth
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PermissionExempt {
    
    /**
     * 豁免原因（必填）
     * 
     * <p>说明为什么此方法不需要权限校验，便于后续审计和Code Review
     * 
     * @return 豁免原因
     */
    String value();
}
