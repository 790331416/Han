package com.han.common.log.config;

import com.han.common.log.aspect.OperLogAspect;
import com.han.common.log.service.IOperLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 操作日志自动配置
 * <p>
 * 当 Spring 容器中存在 IOperLogService Bean 时，自动注册 OperLogAspect。
 */
@Configuration
public class OperLogAutoConfiguration {

    @Bean
    @ConditionalOnBean(IOperLogService.class)
    public OperLogAspect operLogAspect(IOperLogService operLogService) {
        return new OperLogAspect(operLogService);
    }
}
