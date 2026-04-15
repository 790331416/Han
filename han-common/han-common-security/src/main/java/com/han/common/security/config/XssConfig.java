package com.han.common.security.config;

import com.han.common.security.filter.XssFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * XSS 防护配置
 */
@Configuration
public class XssConfig {

    /** 排除 XSS 过滤的路径（文件上传等二进制接口） */
    private static final List<String> EXCLUDES = List.of(
            "/file/upload",
            "/inner/"
    );

    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter(EXCLUDES));
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(1);
        return registration;
    }
}
