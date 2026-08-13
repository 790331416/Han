package com.han.common.doc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 统一接口文档配置。
 * <p>
 * 整体受 {@code han.doc.enabled} 控制且<b>默认关闭</b>（见 {@link DocProperties}）；
 * 开关本身由 {@link DocEnvironmentPostProcessor} 同步下发给 springdoc 与 knife4j，
 * 因此关闭时连 {@code /v3/api-docs} 与 {@code /doc.html} 都不会暴露。
 * <p>
 * 开启后提供统一的标题 / 版本 / Bearer 鉴权方案，并按 A（管理端）、I（内部）、B（业务端）
 * 三层生成接口分组 —— 此前本模块只有一个 pom，六个服务拿到的都是 starter 的裸默认行为。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(DocProperties.class)
@ConditionalOnProperty(prefix = "han.doc", name = "enabled", havingValue = "true")
public class DocAutoConfiguration {

    private static final String BEARER_SCHEME = "Bearer";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI hanOpenAPI(DocProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .version(properties.getVersion())
                        .description(properties.getDescription()))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** A 层：管理端接口，控制器类名以 A 开头 */
    @Bean
    @ConditionalOnProperty(prefix = "han.doc", name = "group-by-tier", havingValue = "true", matchIfMissing = true)
    public GroupedOpenApi adminTierApi() {
        return GroupedOpenApi.builder()
                .group("A-管理端")
                .packagesToScan("com.han")
                .pathsToMatch("/admin/**", "/a/**")
                .build();
    }

    /** I 层：服务间内部接口 */
    @Bean
    @ConditionalOnProperty(prefix = "han.doc", name = "group-by-tier", havingValue = "true", matchIfMissing = true)
    public GroupedOpenApi innerTierApi() {
        return GroupedOpenApi.builder()
                .group("I-内部接口")
                .packagesToScan("com.han")
                .pathsToMatch("/inner/**")
                .build();
    }

    /** 全量分组，便于查找未落在 A / I 前缀下的 B 层业务接口 */
    @Bean
    @ConditionalOnProperty(prefix = "han.doc", name = "group-by-tier", havingValue = "true", matchIfMissing = true)
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .packagesToScan("com.han")
                .build();
    }
}
