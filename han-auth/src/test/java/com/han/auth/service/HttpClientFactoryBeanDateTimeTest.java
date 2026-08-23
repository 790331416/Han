package com.han.auth.service;

import com.han.api.open.OpenServiceClient;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.domain.R;
import com.han.common.web.config.JacksonAutoConfiguration;
import com.han.common.web.http.HttpClientFactoryBean;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpClientFactoryBeanDateTimeTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readsTheProjectDateTimeFormatWithTheApplicationJsonMapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inner/open/vendor/application/status", exchange -> {
            byte[] body = ("{\"code\":200,\"msg\":\"操作成功\",\"data\":{"
                    + "\"applicationNo\":\"APP-1\",\"status\":1,\"statusName\":\"待审核\","
                    + "\"createTime\":\"2026-08-23 08:45:00\",\"reviewTime\":null},\"timestamp\":1}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
            when(loadBalancer.choose("han-open")).thenReturn(new DefaultServiceInstance(
                    "test", "han-open", "127.0.0.1", server.getAddress().getPort(), false));

            InnerAuthProperties innerAuth = new InnerAuthProperties();
            ObjectProvider<InnerAuthProperties> innerProvider = mock(ObjectProvider.class);
            when(innerProvider.getIfAvailable()).thenReturn(innerAuth);
            JsonMapper.Builder jsonBuilder = JsonMapper.builder();
            new JacksonAutoConfiguration().longToStringCustomizer().customize(jsonBuilder);
            ObjectProvider<JsonMapper> mapperProvider = mock(ObjectProvider.class);
            when(mapperProvider.getIfAvailable()).thenReturn(jsonBuilder.build());

            ApplicationContext context = mock(ApplicationContext.class);
            when(context.getBean(LoadBalancerClient.class)).thenReturn(loadBalancer);
            when(context.getBeanProvider(InnerAuthProperties.class)).thenReturn((ObjectProvider) innerProvider);
            when(context.getBeanProvider(JsonMapper.class)).thenReturn((ObjectProvider) mapperProvider);
            when(context.getEnvironment()).thenReturn(new MockEnvironment()
                    .withProperty("spring.application.name", "han-auth"));

            HttpClientFactoryBean<OpenServiceClient> factory = new HttpClientFactoryBean<>();
            factory.setClientInterface(OpenServiceClient.class);
            factory.setServiceName("han-open");
            factory.setApplicationContext(context);

            R<OpenVendorApplicationStatusVO> response = factory.getObject()
                    .queryPortalApplication("APP-1", "13800000000");

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getData().getCreateTime())
                    .isEqualTo(LocalDateTime.of(2026, 8, 23, 8, 45));
        } finally {
            server.stop(0);
        }
    }
}
