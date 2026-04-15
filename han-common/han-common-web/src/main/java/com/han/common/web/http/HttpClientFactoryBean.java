package com.han.common.web.http;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.InnerAuthSignUtil;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;

/**
 * 为 {@code @HttpExchange} 接口创建代理实例的 FactoryBean。
 */
public class HttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private Class<T> clientInterface;
    private String serviceName;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public T getObject() {
        LoadBalancerClient loadBalancerClient = applicationContext.getBean(LoadBalancerClient.class);
        RestClient restClient = RestClient.builder()
                .baseUrl("http://" + serviceName)
                .requestInterceptor((request, body, execution) -> {
                    ServiceInstance instance = loadBalancerClient.choose(serviceName);
                    if (instance == null) {
                        throw new IllegalStateException("No instances available for service: " + serviceName);
                    }

                    URI originalUri = request.getURI();
                    URI resolved = URI.create(instance.getUri() + originalUri.getRawPath()
                            + (originalUri.getRawQuery() != null ? "?" + originalUri.getRawQuery() : ""));

                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(request.getHeaders());
                    String method = request.getMethod() != null ? request.getMethod().name() : "GET";
                    applyInnerAuthHeaders(headers, method, originalUri.getRawPath());

                    HttpRequestWrapper wrapper = new HttpRequestWrapper(request) {
                        @Override
                        public URI getURI() {
                            return resolved;
                        }

                        @Override
                        public HttpHeaders getHeaders() {
                            return headers;
                        }
                    };
                    return execution.execute(wrapper, body);
                })
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(clientInterface);
    }

    private void applyInnerAuthHeaders(HttpHeaders headers, String method, String path) {
        if (path == null || !path.startsWith("/inner/")) {
            return;
        }

        InnerAuthProperties properties = applicationContext.getBeanProvider(InnerAuthProperties.class).getIfAvailable();
        if (properties == null || !properties.isEnabled() || properties.getSecret() == null || properties.getSecret().isBlank()) {
            return;
        }

        String clientName = applicationContext.getEnvironment().getProperty("spring.application.name", "unknown-service");
        long timestamp = System.currentTimeMillis();
        String signature = InnerAuthSignUtil.sign(clientName, method, path, timestamp, properties.getSecret());

        headers.set(Constants.INNER_AUTH_CLIENT_HEADER, clientName);
        headers.set(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(timestamp));
        headers.set(Constants.INNER_AUTH_SIGNATURE_HEADER, signature);
    }

    @Override
    public Class<T> getObjectType() {
        return clientInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setClientInterface(Class<T> clientInterface) {
        this.clientInterface = clientInterface;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
