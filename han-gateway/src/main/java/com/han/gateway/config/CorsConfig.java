package com.han.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 网关跨域配置
 *
 * <p>{@code allowedOriginPattern=*} 叠加 {@code allowCredentials=true} 时，浏览器会把
 * {@code Access-Control-Allow-Origin} 反射成请求方 Origin 并允许携带凭据，等价于对任意站点开放带
 * Cookie/Token 的跨域调用。原实现把这一组合硬编码在代码里，部署侧无法收敛。
 *
 * <p>现改为可配置：{@code han.gateway.cors.allowed-origin-patterns}（逗号分隔）。
 * <b>默认值仍为 {@code *} 以保持现有行为不变</b>，但启动时会打印告警。收敛为实际站点清单
 * 属于部署策略调整，需要拍板后在三档 {@code .env} / compose 中显式配置。
 */
@Slf4j
@Configuration
public class CorsConfig {

    private static final String ALLOW_ALL = "*";

    private final List<String> allowedOriginPatterns;
    private final boolean allowCredentials;

    public CorsConfig(@Value("${han.gateway.cors.allowed-origin-patterns:*}") List<String> allowedOriginPatterns,
                      @Value("${han.gateway.cors.allow-credentials:true}") boolean allowCredentials) {
        this.allowedOriginPatterns = allowedOriginPatterns;
        this.allowCredentials = allowCredentials;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        if (allowCredentials && allowedOriginPatterns.contains(ALLOW_ALL)) {
            log.warn("CORS 允许任意来源携带凭据（allowed-origin-patterns=*, allow-credentials=true），"
                    + "任何站点都可以用受害者的登录态发起跨域请求。请在部署侧通过 "
                    + "han.gateway.cors.allowed-origin-patterns 收敛为实际站点清单。");
        }

        CorsConfiguration config = new CorsConfiguration();
        allowedOriginPatterns.forEach(config::addAllowedOriginPattern);
        config.addAllowedMethod(ALLOW_ALL);
        config.addAllowedHeader(ALLOW_ALL);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
