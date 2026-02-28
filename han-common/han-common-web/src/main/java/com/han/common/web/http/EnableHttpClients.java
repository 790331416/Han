package com.han.common.web.http;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用 HttpExchange 声明式客户端自动注册。
 * <p>
 * 在启动类上标注此注解，框架会自动扫描指定包下所有带 {@code @HttpExchange} 的接口，
 * 并通过 {@link org.springframework.web.client.support.RestClientAdapter} + {@link org.springframework.web.service.invoker.HttpServiceProxyFactory}
 * 创建代理 Bean 注册到 Spring 容器中。
 * <p>
 * 使用示例：
 * <pre>
 * {@code @EnableHttpClients(basePackages = "com.han.api")}
 * {@code @SpringBootApplication}
 * public class MyApplication { }
 * </pre>
 *
 * @see HttpClientRegistrar
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({HttpClientAutoConfiguration.class, HttpClientRegistrar.class})
public @interface EnableHttpClients {

    /**
     * 要扫描的包路径，默认扫描 "com.han.api"
     */
    String[] basePackages() default {"com.han.api"};
}
