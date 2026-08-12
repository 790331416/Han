package com.han.tenant.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.po.TenantQuotaPo;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantQuotaMapper;
import com.han.tenant.service.ITenantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配额读写必须能跨租户命中真实数据：查得到就不能再伪造「不限」，查得到就不能再走 insert 撞唯一索引。
 */
class TenantQuotaServiceImplTest {

    private final TenantQuotaMapper quotaMapper = mock(TenantQuotaMapper.class);
    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final ITenantService tenantService = mock(ITenantService.class);
    private final TenantQuotaServiceImpl quotaService =
            new TenantQuotaServiceImpl(quotaMapper, tenantMapper, tenantService);

    @Test
    void shouldReturnRealQuotaInsteadOfFabricatedUnlimited() {
        givenTenantExists(5L);
        when(quotaMapper.selectOne(any())).thenReturn(quota(5L, 10));
        when(tenantService.countTenantUsers(5L)).thenReturn(3);

        TenantQuotaPo result = quotaService.getOrDefault(5L);

        assertThat(result.getUserLimit()).isEqualTo(10);
        assertThat(result.getUserUsed()).isEqualTo(3);
    }

    @Test
    void shouldFallBackToUnlimitedOnlyWhenQuotaTrulyAbsent() {
        givenTenantExists(5L);
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(tenantService.countTenantUsers(5L)).thenReturn(2);

        TenantQuotaPo result = quotaService.getOrDefault(5L);

        assertThat(result.getUserLimit()).isEqualTo(-1);
        assertThat(result.getUserUsed()).isEqualTo(2);
    }

    @Test
    void shouldRejectQuotaQueryForUnknownTenant() {
        when(tenantMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> quotaService.getOrDefault(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户不存在");
    }

    @Test
    void shouldUpdateExistingQuotaInsteadOfInsertingDuplicate() {
        givenTenantExists(5L);
        TenantQuotaPo existing = quota(5L, 10);
        existing.setQuotaId(77L);
        when(quotaMapper.selectOne(any())).thenReturn(existing);

        TenantQuotaPo submitted = quota(5L, 20);
        quotaService.saveOrUpdate(submitted);

        ArgumentCaptor<TenantQuotaPo> captor = ArgumentCaptor.forClass(TenantQuotaPo.class);
        verify(quotaMapper).updateById(captor.capture());
        verify(quotaMapper, never()).insert(any(TenantQuotaPo.class));
        assertThat(captor.getValue().getQuotaId()).isEqualTo(77L);
        assertThat(captor.getValue().getUserLimit()).isEqualTo(20);
    }

    @Test
    void shouldIgnoreCallerSuppliedQuotaIdWhenInserting() {
        givenTenantExists(5L);
        when(quotaMapper.selectOne(any())).thenReturn(null);

        TenantQuotaPo submitted = quota(5L, 20);
        submitted.setQuotaId(999L);
        quotaService.saveOrUpdate(submitted);

        ArgumentCaptor<TenantQuotaPo> captor = ArgumentCaptor.forClass(TenantQuotaPo.class);
        verify(quotaMapper).insert(captor.capture());
        assertThat(captor.getValue().getQuotaId()).isNull();
    }

    @Test
    void shouldRejectIllegalLimits() {
        givenTenantExists(5L);
        TenantQuotaPo submitted = quota(5L, -9);

        assertThatThrownBy(() -> quotaService.saveOrUpdate(submitted))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配额上限");
    }

    @Test
    void shouldRejectQuotaWithoutTenant() {
        assertThatThrownBy(() -> quotaService.saveOrUpdate(new TenantQuotaPo()))
                .isInstanceOf(BusinessException.class);
    }

    private void givenTenantExists(Long tenantId) {
        TenantPo tenant = new TenantPo();
        tenant.setId(tenantId);
        when(tenantMapper.selectById(tenantId)).thenReturn(tenant);
    }

    private TenantQuotaPo quota(Long tenantId, Integer userLimit) {
        TenantQuotaPo quota = new TenantQuotaPo();
        quota.setTenantId(tenantId);
        quota.setUserLimit(userLimit);
        quota.setStorageLimit(-1L);
        quota.setApiLimit(-1L);
        return quota;
    }
}
