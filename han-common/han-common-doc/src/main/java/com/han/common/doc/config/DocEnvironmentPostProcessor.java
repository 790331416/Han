package com.han.common.doc.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 按 {@code han.doc.enabled} 统一下发 springdoc / knife4j 的开关默认值。
 * <p>
 * 只引 starter 而不配开关时，springdoc 与 knife4j 自身的默认值都是「开启」，
 * 于是 {@code /v3/api-docs} 与 {@code /doc.html} 在所有环境都可访问。
 * 这里在环境里追加一组<b>最低优先级</b>的默认值把它们关掉：
 * 各服务 yml 里如果显式配了 {@code springdoc.*} / {@code knife4j.*}，仍然以显式配置为准。
 * <p>
 * 通过 {@code META-INF/spring.factories} 注册；order 取最低，
 * 以保证在 ConfigData 加载完 application.yml 之后才读取 {@code han.doc.enabled}。
 */
public class DocEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "hanDocDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        boolean enabled = environment.getProperty("han.doc.enabled", Boolean.class, Boolean.FALSE);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("springdoc.api-docs.enabled", enabled);
        defaults.put("springdoc.swagger-ui.enabled", enabled);
        defaults.put("knife4j.enable", enabled);
        // knife4j 的生产模式会屏蔽 /doc.html 资源
        defaults.put("knife4j.production", !enabled);

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
