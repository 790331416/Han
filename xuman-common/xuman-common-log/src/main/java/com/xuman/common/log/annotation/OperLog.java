package com.xuman.common.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * 模块名称
     */
    String module() default "";

    /**
     * 操作类型
     */
    OperType type() default OperType.OTHER;

    /**
     * 是否记录请求参数
     */
    boolean saveParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean saveResult() default true;

    /**
     * 操作类型枚举
     */
    enum OperType {
        /** 其他 */
        OTHER,
        /** 新增 */
        INSERT,
        /** 修改 */
        UPDATE,
        /** 删除 */
        DELETE,
        /** 查询 */
        SELECT,
        /** 导出 */
        EXPORT,
        /** 导入 */
        IMPORT,
        /** 授权 */
        GRANT,
        /** 强退 */
        FORCE_LOGOUT,
        /** 清空数据 */
        CLEAN
    }
}
