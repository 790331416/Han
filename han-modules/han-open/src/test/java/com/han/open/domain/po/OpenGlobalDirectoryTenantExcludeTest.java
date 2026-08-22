package com.han.open.domain.po;

import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局目录表（open_api_resource / open_api_resource_version）租户过滤排除测试。
 * <p>在 tenantId 非空的前提下，验证排除源于两张全局目录表，而非「租户为空」的短路分支。</p>
 */
class OpenGlobalDirectoryTenantExcludeTest {

    @Test
    void globalDirectoryTablesAreExcludedWhileTenantBusinessTablesAreNot() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getTenantId()).thenReturn(42L);

        TenantProperties tenantProperties = mock(TenantProperties.class);
        when(tenantProperties.getExcludes()).thenReturn(List.of());

        HanTenantLineHandler handler = new HanTenantLineHandler(tenantProperties, securityContext);

        // 两张全局目录表走 DEFAULT_EXCLUDES 排除
        assertThat(handler.ignoreTable("open_api_resource")).isTrue();
        assertThat(handler.ignoreTable("open_api_resource_version")).isTrue();

        // 对照：租户业务表仍参与租户过滤（证明排除针对这两张全局表，而非租户为空短路）
        assertThat(handler.ignoreTable("open_app")).isFalse();
    }
}
