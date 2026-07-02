package com.han.job.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务处理器标记注解
 * <p>
 * 标注在 Spring Bean 上，配合 {@link JobHandlerMethod} 将可调度方法
 * 暴露给管理端「新增任务 - 调用目标方法」下拉列表。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobHandler {

    /** 处理器描述（如：示例任务） */
    String value() default "";
}
