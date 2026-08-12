package com.han.common.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解
 * <p>
 * 标注在 Mapper 方法或 Service 方法上，启用数据范围过滤。
 * 拦截器会根据当前用户角色的 dataScope 自动追加 SQL 条件。
 * <p>
 * 生成的条件形如：
 * <pre>
 *   -- 角色配置了部门范围（本部门 / 本部门及以下 / 自定义部门）
 *   AND (d.dept_id IN (1, 2, 3) OR u.create_by = 100)
 *   -- 角色只有「仅本人」
 *   AND (u.create_by = 100)
 * </pre>
 * <p>
 * 语义约定（与 {@code SysUser} 侧 {@code resolveDataScopeDeptIds} 的返回约定一致）：
 * <ul>
 *   <li>超级管理员：不追加任何条件</li>
 *   <li>部门 ID 集合为 null：数据范围是「全部数据」，不追加任何条件</li>
 *   <li>部门 ID 集合为空集：数据范围是「仅本人」，只按创建人过滤</li>
 *   <li>未登录：追加恒不成立条件直接拒绝，不会被当成「不限制」放行</li>
 * </ul>
 * <p>
 * 未标注本注解的语句不会产生任何额外条件，拦截器对其完全透明。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /**
     * 部门表别名（默认 d）
     * <p>
     * 用于限定 {@link #deptIdColumn()} 的归属表。单表查询没有别名时传空串。
     */
    String deptAlias() default "d";

    /**
     * 用户表别名（默认 u）
     * <p>
     * 用于限定 {@link #createByColumn()} 的归属表。单表查询没有别名时传空串。
     */
    String userAlias() default "u";

    /**
     * 部门ID字段名（默认 dept_id）
     */
    String deptIdColumn() default "dept_id";

    /**
     * 创建者字段名（用于"仅本人"模式，默认 create_by）
     */
    String createByColumn() default "create_by";
}
