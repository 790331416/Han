package com.han.api.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户契约的分层守卫。
 *
 * <p>这条契约曾经声明成 {@code @HttpExchange("/tenant")}，打到 A 层带
 * {@code @RequiresPermission("tenant:query")} 的管理端接口上，内部调用必然鉴权失败；
 * 而 {@code HttpClientFactoryBean} 只对 {@code /inner/} 前缀追加内部签名头，
 * 所以这个客户端连鉴权头都不带。为了绕开它，{@code /tenant/listAllValid} 被塞进网关
 * 免登录白名单，造成未认证枚举全平台租户 PII。
 *
 * <p>本测试把「必须挂在 I 层」这条约束固化下来，防止再次退回去。
 */
class TenantServiceClientContractTest {

    @Test
    void clientMustBeMappedToInnerLayer() {
        HttpExchange exchange = TenantServiceClient.class.getAnnotation(HttpExchange.class);
        assertNotNull(exchange, "TenantServiceClient 必须声明 @HttpExchange");
        assertEquals("/inner/tenant", exchange.value(),
                "租户契约必须挂在 I 层 /inner/tenant，不能指向 A 层管理端路径");
    }

    @Test
    void everyMethodMustBeIdempotentGet() {
        Method[] methods = TenantServiceClient.class.getDeclaredMethods();
        assertEquals(4, methods.length, "契约方法数量变化时请同步复核幂等性与重试策略说明");
        for (Method method : methods) {
            assertTrue(method.isAnnotationPresent(GetExchange.class),
                    "方法 " + method.getName() + " 不是 GET；契约文档声明本接口全部方法幂等可重试，"
                            + "新增非幂等方法必须同步修改该说明");
        }
    }
}
