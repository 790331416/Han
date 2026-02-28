package com.han.job.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Quartz 调度配置
 * 使用 Spring Boot 自动配置，基于数据库集群模式
 */
@Configuration
@EnableScheduling
public class QuartzConfig {
    
    // Spring Boot 4.0 已通过 spring-boot-starter-quartz 自动配置
    // 配置项在 application.yml 中定义
    // 集群模式需要执行 Quartz 官方提供的数据库初始化脚本
}
