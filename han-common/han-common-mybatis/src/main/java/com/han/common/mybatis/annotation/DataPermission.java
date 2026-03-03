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
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /**
     * 部门表别名（默认 d）
     */
    String deptAlias() default "d";

    /**
     * 用户表别名（默认 u）
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
