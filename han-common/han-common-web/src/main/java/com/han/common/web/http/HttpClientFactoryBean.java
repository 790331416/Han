package com.han.common.web.http;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;

/**
 * 为 {@code @HttpExchange} 接口创建代理实例的 FactoryBean。
 * <p>
 * 通过 {@link LoadBalancerClient} 解析服务实例地址，
 * 再通过 {@link HttpServiceProxyFactory} 生成声明式 HTTP 客户端代理。
 * <p>
 * 由 {@link HttpClientRegistrar} 自动注册，无需手动配置。
 *
 * @param <T> 客户端接口类型
 */
public class HttpClientFactoryBean<T> implements FactoryBean<T> {

    private Class<T> clientInterface;
    private String serviceName;

    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @Override
    public T getObject() {
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
                    HttpRequestWrapper wrapper = new HttpRequestWrapper(request) {
                        @Override
                        public URI getURI() {
                            return resolved;
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
