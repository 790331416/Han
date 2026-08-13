package com.han.common.web.config;

import com.han.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 把 {@link GlobalExceptionHandler} 变成「依赖了 han-common-web 就一定生效」。
 * <p>
 * 同一个模块里 {@code JacksonAutoConfiguration} 和 {@code HttpClientAutoConfiguration} 走自动配置导入，
 * 而 {@code GlobalExceptionHandler} 只是个裸 {@code @RestControllerAdvice}，完全依赖各服务启动类
 * 写了 {@code scanBasePackages = "com.han"} 才能被扫到。目前业务服务都写了，
 * 但 han-gateway 与 han-visual/han-monitor 用的是裸 {@code @SpringBootApplication}。
 * 任何新服务忘记加这个属性，全局异常处理就会静默消失，异常以 Spring Boot 默认白页返回、
 * 把堆栈暴露给前端，而且没有任何编译期或启动期提示。
 * <p>
 * {@code @ConditionalOnMissingBean} 保证已被组件扫描注册时不会重复注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
