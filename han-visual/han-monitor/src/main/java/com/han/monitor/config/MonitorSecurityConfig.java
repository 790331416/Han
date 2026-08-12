package com.han.monitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * 监控中心安全配置
 *
 * <p>本模块引入了 spring-boot-starter-security 但此前没有任何过滤器链配置，
 * 默认自动配置会对所有路径要求认证，导致容器 HEALTHCHECK 请求 /actuator/health 恒返回 401，
 * 容器永远处于 unhealthy；同时 Spring Boot Admin 客户端注册端点 /instances 会被 CSRF 过滤器拦截。
 *
 * <p>这里只放行健康探针与登录页所需的静态资源，其余路径（含 Spring Boot Admin 控制台）
 * 仍然要求认证，凭据由 MONITOR_ADMIN_USERNAME / MONITOR_ADMIN_PASSWORD 外部注入。
 */
@Configuration
@EnableWebSecurity
public class MonitorSecurityConfig {

    private final String adminContextPath;

    public MonitorSecurityConfig(@Value("${spring.boot.admin.context-path:}") String adminContextPath) {
        this.adminContextPath = adminContextPath;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(adminContextPath + "/");

        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(adminContextPath + "/assets/**").permitAll()
                        .requestMatchers(adminContextPath + "/login").permitAll()
                        .requestMatchers(adminContextPath + "/actuator/health").permitAll()
                        .requestMatchers(adminContextPath + "/actuator/health/**").permitAll()
                        .requestMatchers(adminContextPath + "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage(adminContextPath + "/login")
                        .successHandler(successHandler))
                .logout(logout -> logout.logoutUrl(adminContextPath + "/logout"))
                .httpBasic(Customizer.withDefaults())
                // Spring Boot Admin 客户端以 POST/DELETE 调用注册端点，无法携带 CSRF token
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        adminContextPath + "/instances",
                        adminContextPath + "/instances/*",
                        adminContextPath + "/actuator/**"))
                .build();
    }
}
