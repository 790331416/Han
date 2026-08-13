package com.han.tenant.service.impl;

import com.han.api.system.SystemClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.tenant.config.HanTenantProperties;
import com.han.tenant.converter.TenantApiConverter;
import com.han.tenant.converter.TenantConverter;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.service.support.TenantRoleMenuSynchronizer;
import com.han.tenant.service.support.TenantSessionRevoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户限额门禁与停用联动的行为约束。
 */
class TenantServiceImplTest {

    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final TenantPackageMapper packageMapper = mock(TenantPackageMapper.class);
    private final TenantConverter tenantConverter = mock(TenantConverter.class);
    private final TenantApiConverter tenantApiConverter = mock(TenantApiConverter.class);
    private final TenantRoleMenuSynchronizer roleMenuSynchronizer = mock(TenantRoleMenuSynchronizer.class);
    private final TenantSessionRevoker sessionRevoker = mock(TenantSessionRevoker.class);
    private final SystemClient systemClient = mock(SystemClient.class);

    private final TenantServiceImpl tenantService = new TenantServiceImpl(
            tenantMapper,
            packageMapper,
            tenantConverter,
            tenantApiConverter,
            roleMenuSynchronizer,
            sessionRevoker,
            new HanTenantProperties(),
            systemClient
    );

    @Test
    void checkUserLimitShouldFailClosedWhenUserCountUnavailable() {
        givenTenant(100L, 5);
        when(systemClient.countUsersByTenantId(100L)).thenThrow(new IllegalStateException("No instances available"));

        assertThat(tenantService.checkUserLimit(100L)).isFalse();
    }

    @Test
    void checkUserLimitShouldFailClosedWhenRemoteReturnsBusinessFailure() {
        givenTenant(100L, 5);
        when(systemClient.countUsersByTenantId(100L)).thenReturn(R.fail("统计失败"));

        assertThat(tenantService.checkUserLimit(100L)).isFalse();
    }

    @Test
    void checkUserLimitShouldPassWhenUnderLimit() {
        givenTenant(100L, 5);
        when(systemClient.countUsersByTenantId(100L)).thenReturn(R.ok(3));

        assertThat(tenantService.checkUserLimit(100L)).isTrue();
    }

    @Test
    void countTenantUsersShouldDegradeToZeroForDisplay() {
        when(systemClient.countUsersByTenantId(100L)).thenThrow(new IllegalStateException("boom"));

        assertThat(tenantService.countTenantUsers(100L)).isZero();
    }

    @Test
    void updateStatusShouldRejectUndefinedStatusValue() {
        assertThatThrownBy(() -> tenantService.updateStatus(100L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户状态非法");

        verify(tenantMapper, never()).updateById(any(TenantPo.class));
    }

    @Test
    void updateStatusShouldRevokeSessionsWhenDisabling() {
        givenTenant(100L, -1);

        tenantService.updateStatus(100L, 1);

        verify(sessionRevoker).revokeByTenant(100L);
    }

    @Test
    void updateStatusShouldNotRevokeSessionsWhenEnabling() {
        givenTenant(100L, -1);

        tenantService.updateStatus(100L, 0);

        verify(sessionRevoker, never()).revokeByTenant(anyLong());
    }

    @Test
    void deleteTenantShouldRejectPlatformTenant() {
        assertThatThrownBy(() -> tenantService.deleteTenant(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台租户");
    }

    private void givenTenant(Long tenantId, Integer userLimit) {
        TenantPo tenant = new TenantPo();
        tenant.setId(tenantId);
        tenant.setUserLimit(userLimit);
        tenant.setStatus(0);
        when(tenantMapper.selectById(tenantId)).thenReturn(tenant);
    }
}
