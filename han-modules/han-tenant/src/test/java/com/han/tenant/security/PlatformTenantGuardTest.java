package com.han.tenant.security;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.tenant.config.HanTenantProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台租户边界：拿到 tenant:* 权限的普通租户用户不得管理其他租户。
 */
class PlatformTenantGuardTest {

    private final HanTenantProperties properties = new HanTenantProperties();
    private final PlatformTenantGuard guard = new PlatformTenantGuard(properties);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void shouldRejectAnonymousCaller() {
        assertThatThrownBy(guard::assertPlatformTenant).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldRejectNonPlatformTenantUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(200L).tenantId(2L).build());

        assertThatThrownBy(guard::assertPlatformTenant)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("平台租户");
    }

    @Test
    void shouldAllowPlatformTenantUser() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(200L).tenantId(1L).build());

        assertThatCode(guard::assertPlatformTenant).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowSuperAdminRegardlessOfTenant() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(9L).build());

        assertThatCode(guard::assertPlatformTenant).doesNotThrowAnyException();
    }

    @Test
    void shouldHonourKillSwitch() {
        properties.setEnforcePlatformBoundary(false);
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(200L).tenantId(2L).build());

        assertThatCode(guard::assertPlatformTenant).doesNotThrowAnyException();
    }
}
