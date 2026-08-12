package com.han.api.system;

import com.han.api.system.domain.TenantInitDto;
import com.han.common.core.domain.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 {@code SystemServiceClient extends SystemClient} 之后，继承来的方法仍然能被
 * {@link HttpServiceProxyFactory} 正确代理。
 *
 * <p>为什么需要这条测试：这五个租户生命周期方法原先在两个接口里各写了一份，改成继承是为了
 * 消除契约漂移。但代理是运行期按接口反射生成的 —— 如果 {@code HttpServiceProxyFactory} 不扫描
 * 父接口，或者类级 {@code @HttpExchange} 前缀取的是声明类而不是被代理的接口，
 * 这五个方法会在编译期毫无征兆、到线上第一次调用才 404。这里用 {@code MockRestServiceServer}
 * 把真实的 URL 拼装结果断言出来。
 */
class SystemServiceClientContractTest {

    private static final String BASE_URL = "http://han-system";

    private MockRestServiceServer server;
    private SystemServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClientAdapter adapter = RestClientAdapter.create(builder.build());
        client = HttpServiceProxyFactory.builderFor(adapter).build().createClient(SystemServiceClient.class);
    }

    @Test
    void inheritedTenantMethodKeepsInnerPrefixAndQueryParam() {
        server.expect(requestTo(BASE_URL + "/inner/system/tenant/cleanup?tenantId=7"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"ok\"}", MediaType.APPLICATION_JSON));

        R<Void> result = client.cleanupTenantData(7L);

        server.verify();
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void inheritedPostBodyMethodIsProxied() {
        server.expect(requestTo(BASE_URL + "/inner/system/tenant/init"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"ok\"}", MediaType.APPLICATION_JSON));

        R<Void> result = client.initTenantData(TenantInitDto.builder().tenantId(7L).build());

        server.verify();
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void ownMethodStillWorks() {
        server.expect(requestTo(BASE_URL + "/inner/system/user/1"))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertEquals(200, client.getUserById(1L).getCode());
        server.verify();
    }
}
