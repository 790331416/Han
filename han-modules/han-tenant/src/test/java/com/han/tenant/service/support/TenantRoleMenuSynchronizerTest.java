package com.han.tenant.service.support;

import com.han.api.system.SystemClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 套餐菜单下发的两条纪律：空集拒绝下发、R.fail 必须响亮失败。
 */
class TenantRoleMenuSynchronizerTest {

    private final SystemClient systemClient = mock(SystemClient.class);
    private final TenantRoleMenuSynchronizer synchronizer = new TenantRoleMenuSynchronizer(systemClient);

    @Test
    void shouldRejectEmptyMenuSetToAvoidWipingTenantRoles() {
        assertThatThrownBy(() -> synchronizer.sync(100L, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝下发");

        verify(systemClient, never()).syncRoleMenusByTenantId(anyLong(), any());
    }

    @Test
    void shouldRejectNullMenuSet() {
        assertThatThrownBy(() -> synchronizer.sync(100L, null))
                .isInstanceOf(BusinessException.class);

        verify(systemClient, never()).syncRoleMenusByTenantId(anyLong(), any());
    }

    @Test
    void shouldFailLoudlyWhenRemoteReturnsBusinessFailure() {
        when(systemClient.syncRoleMenusByTenantId(anyLong(), any())).thenReturn(R.fail("角色不存在"));

        assertThatThrownBy(() -> synchronizer.sync(100L, Set.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldFailLoudlyWhenRemoteReturnsNull() {
        when(systemClient.syncRoleMenusByTenantId(anyLong(), any())).thenReturn(null);

        assertThatThrownBy(() -> synchronizer.sync(100L, Set.of(1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldPassMenuIdsThroughOnSuccess() {
        when(systemClient.syncRoleMenusByTenantId(anyLong(), any())).thenReturn(R.ok());

        synchronizer.sync(100L, Set.of(1L, 2L));

        verify(systemClient).syncRoleMenusByTenantId(100L, Set.of(1L, 2L));
    }
}
