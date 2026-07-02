package com.han.job.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务可调度方法标记注解
 * <p>
 * 标注在 {@link JobHandler} Bean 的公开方法上，方法会出现在
 * 管理端「调用目标方法」下拉；带 String 参数的方法在调用目标括号内填参数。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobHandlerMethod {

    /** 方法描述（如：数据同步） */
    String value() default "";
}
