package com.han.common.security.config;

import com.han.common.security.interceptor.InnerAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 安全拦截器配置
 */
@Configuration
@RequiredArgsConstructor
public class SecurityWebMvcConfigurer implements WebMvcConfigurer {

    private final InnerAuthInterceptor innerAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(innerAuthInterceptor).addPathPatterns("/**").order(-200);
    }
}
