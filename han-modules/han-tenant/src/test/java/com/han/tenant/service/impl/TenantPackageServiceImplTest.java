package com.han.tenant.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.tenant.config.HanTenantProperties;
import com.han.tenant.converter.TenantPackageConverter;
import com.han.tenant.converter.TenantPackageConverterImpl;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.service.support.TenantRoleMenuSynchronizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 套餐写操作不得静默成功；菜单裁剪后的回灌只在显式触发或显式开关下发生。
 */
class TenantPackageServiceImplTest {

    private final TenantPackageMapper packageMapper = mock(TenantPackageMapper.class);
    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final TenantPackageConverter packageConverter = new TenantPackageConverterImpl();
    private final TenantRoleMenuSynchronizer roleMenuSynchronizer = mock(TenantRoleMenuSynchronizer.class);
    private final HanTenantProperties properties = new HanTenantProperties();

    private final TenantPackageServiceImpl packageService = new TenantPackageServiceImpl(
            packageMapper, tenantMapper, packageConverter, roleMenuSynchronizer, properties);

    @Test
    void shouldRejectUpdateOnMissingPackage() {
        when(packageMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> packageService.updatePackageMenus(9L, Set.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户套餐不存在");

        verify(packageMapper, never()).updateById(any(TenantPackagePo.class));
    }

    @Test
    void shouldRejectIllegalPackageStatus() {
        assertThatThrownBy(() -> packageService.updateStatus(1L, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("套餐状态");
    }

    @Test
    void shouldFailLoudlyOnCorruptedMenuJson() {
        givenPackage(1L, "not-a-json-array");

        assertThatThrownBy(() -> packageService.getPackageMenuIds(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("套餐菜单数据已损坏");
    }

    @Test
    void shouldNotAutoResyncWhenMenusShrinkByDefault() {
        givenPackage(1L, "[1,2,3]");
        givenTenantsOnPackage(1L, 100L, 200L);

        packageService.updatePackageMenus(1L, Set.of(1L));

        verify(packageMapper).updateById(any(TenantPackagePo.class));
        verify(roleMenuSynchronizer, never()).sync(anyLong(), any());
    }

    @Test
    void shouldAutoResyncWhenMenusShrinkAndSwitchEnabled() {
        properties.getPackageSync().setAutoResyncOnMenuShrink(true);
        givenPackage(1L, "[1,2,3]");
        givenTenantsOnPackage(1L, 100L, 200L);

        packageService.updatePackageMenus(1L, Set.of(1L));

        verify(roleMenuSynchronizer).sync(100L, Set.of(1L));
        verify(roleMenuSynchronizer).sync(200L, Set.of(1L));
    }

    @Test
    void shouldNotResyncWhenMenusOnlyGrow() {
        properties.getPackageSync().setAutoResyncOnMenuShrink(true);
        givenPackage(1L, "[1]");
        givenTenantsOnPackage(1L, 100L);

        packageService.updatePackageMenus(1L, Set.of(1L, 2L));

        verify(roleMenuSynchronizer, never()).sync(anyLong(), any());
    }

    @Test
    void explicitResyncShouldPushToEveryTenantOnThePackage() {
        givenPackage(1L, "[1,2]");
        givenTenantsOnPackage(1L, 100L, 200L);

        int synced = packageService.resyncPackageToTenants(1L);

        assertThat(synced).isEqualTo(2);
        verify(roleMenuSynchronizer).sync(100L, Set.of(1L, 2L));
        verify(roleMenuSynchronizer).sync(200L, Set.of(1L, 2L));
    }

    private void givenPackage(Long packageId, String menuIdsJson) {
        TenantPackagePo po = new TenantPackagePo();
        po.setId(packageId);
        po.setPackageName("标准版");
        po.setMenuIds(menuIdsJson);
        po.setStatus(0);
        when(packageMapper.selectById(packageId)).thenReturn(po);
    }

    private void givenTenantsOnPackage(Long packageId, Long... tenantIds) {
        List<TenantPo> tenants = java.util.Arrays.stream(tenantIds).map(id -> {
            TenantPo tenant = new TenantPo();
            tenant.setId(id);
            tenant.setPackageId(packageId);
            return tenant;
        }).toList();
        when(tenantMapper.selectList(any())).thenReturn(tenants);
        when(tenantMapper.selectCount(any())).thenReturn((long) tenants.size());
    }
}
