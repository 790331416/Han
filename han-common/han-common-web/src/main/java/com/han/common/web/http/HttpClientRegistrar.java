package com.han.common.web.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.web.service.annotation.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * {@link EnableHttpClients} 的核心注册器。
 * <p>
 * 扫描指定包下所有标注了 {@code @HttpExchange} 的接口，
 * 为每个接口注册一个 {@link HttpClientFactoryBean}，由其负责通过
 * {@code RestClient + HttpServiceProxyFactory} 创建代理实例。
 * <p>
 * 使用 {@link PathMatchingResourcePatternResolver} 确保在 Spring Boot fat JAR 中也能扫描嵌套 JAR。
 */
public class HttpClientRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(HttpClientRegistrar.class);
    private static final String RESOURCE_PATTERN = "/**/*.class";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = importingClassMetadata.getAnnotationAttributes(EnableHttpClients.class.getName());
        String[] basePackages = (attrs != null) ? (String[]) attrs.get("basePackages") : new String[]{"com.han.api"};

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);

        for (String basePackage : basePackages) {
            String pattern = "classpath*:" + basePackage.replace('.', '/') + RESOURCE_PATTERN;
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    if (!resource.isReadable()) {
                        continue;
                    }
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
                    if (!reader.getAnnotationMetadata().hasAnnotation(HttpExchange.class.getName())) {
                        continue;
                    }
                    String className = reader.getClassMetadata().getClassName();
                    if (!reader.getClassMetadata().isInterface()) {
                        continue;
                    }

                    Class<?> clientInterface = Class.forName(className);
                    String beanName = toBeanName(clientInterface.getSimpleName());
                    if (registry.containsBeanDefinition(beanName)) {
                        log.debug("[HttpClient] Bean '{}' already registered, skipping", beanName);
                        continue;
                    }

                    String serviceName = resolveServiceName(clientInterface.getSimpleName());

                    AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                            .genericBeanDefinition(HttpClientFactoryBean.class)
                            .addPropertyValue("clientInterface", clientInterface)
                            .addPropertyValue("serviceName", serviceName)
                            .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)
                            .getBeanDefinition();

                    registry.registerBeanDefinition(beanName, beanDefinition);
                    log.info("[HttpClient] Registered @HttpExchange client: {} -> service: {}", className, serviceName);
                }
            } catch (IOException | ClassNotFoundException e) {
                // 扫描失败意味着部分 @HttpExchange 客户端没被注册，应用照常启动，
                // 直到运行时某个业务分支注入不到 Bean 才炸。启动期能发现的问题不要推迟到线上。
                throw new BeanDefinitionStoreException(
                        "[HttpClient] 扫描包 " + basePackage + " 失败，@HttpExchange 客户端可能未完整注册", e);
            }
        }
    }

    /**
     * 类名首字母小写作为 Bean 名
     */
    private String toBeanName(String simpleName) {
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 根据接口名推导微服务名。
     * 命名约定：XxxServiceClient -> han-xxx
     * 例如：SystemServiceClient -> han-system, FileServiceClient -> han-file
     */
    private String resolveServiceName(String simpleName) {
        String name = simpleName;
        if (name.endsWith("ServiceClient")) {
            name = name.substring(0, name.length() - "ServiceClient".length());
        } else if (name.endsWith("Client")) {
            name = name.substring(0, name.length() - "Client".length());
        }
        // 驼峰转小写：System -> system, TenantOrder -> tenant-order
        StringBuilder sb = new StringBuilder("han-");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
