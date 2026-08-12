package com.han.system.sdfz.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开定时任务支持。
 *
 * <p>han-system 的启动类上没有 {@code @EnableScheduling}，这里按 han-job 的
 * {@code QuartzConfig} 同样的做法在配置类上开，避免改动启动类。</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sdfz.order.schedule", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class CourseOrderScheduleConfig {
}
