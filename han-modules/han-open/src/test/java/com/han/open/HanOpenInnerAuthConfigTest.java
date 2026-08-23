package com.han.open;

import com.han.common.core.config.InnerAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class HanOpenInnerAuthConfigTest {

    @Test
    void bindsTheRuntimeInnerAuthSecretFromApplicationYaml() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("HAN_INNER_AUTH_ENABLED", "true")
                .withProperty("HAN_INNER_AUTH_SECRET", "runtime-secret")
                .withProperty("HAN_INNER_AUTH_CLOCK_SKEW_SECONDS", "60");
        new YamlPropertySourceLoader().load("han-open", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        InnerAuthProperties properties = Binder.get(environment)
                .bind("han.security.inner-auth", Bindable.of(InnerAuthProperties.class))
                .orElseThrow(() -> new AssertionError("han-open inner auth config was not bound"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getSecret()).isEqualTo("runtime-secret");
        assertThat(properties.getClockSkewSeconds()).isEqualTo(60);
    }
}
