package com.han.common.doc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S-71 回归：接口文档此前无任何生产关闭开关，{@code /doc.html} 与 {@code /v3/api-docs}
 * 在所有环境默认开启。
 */
class DocEnvironmentPostProcessorTest {

    private final DocEnvironmentPostProcessor processor = new DocEnvironmentPostProcessor();

    @Test
    @DisplayName("未配置时接口文档默认关闭")
    void disabledByDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertFalse(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true));
        assertFalse(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true));
        assertFalse(environment.getProperty("knife4j.enable", Boolean.class, true));
        assertTrue(environment.getProperty("knife4j.production", Boolean.class, false));
    }

    @Test
    @DisplayName("han.doc.enabled=true 时同步放开 springdoc 与 knife4j")
    void enabledByHanDocSwitch() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("han.doc.enabled", "true")));

        processor.postProcessEnvironment(environment, null);

        assertTrue(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false));
        assertTrue(environment.getProperty("knife4j.enable", Boolean.class, false));
        assertFalse(environment.getProperty("knife4j.production", Boolean.class, true));
    }

    @Test
    @DisplayName("下发的只是默认值，服务自己显式配置的优先级更高")
    void explicitConfigurationWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("springdoc.api-docs.enabled", "true")));

        processor.postProcessEnvironment(environment, null);

        assertTrue(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false));
    }

    @Test
    @DisplayName("重复执行不会叠加属性源")
    void idempotent() {
        StandardEnvironment environment = new StandardEnvironment();
        int before = environment.getPropertySources().size();

        processor.postProcessEnvironment(environment, null);
        processor.postProcessEnvironment(environment, null);

        assertEquals(before + 1, environment.getPropertySources().size());
    }
}
