package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.tenant.domain.po.TenantQuotaPo;
import com.han.tenant.mapper.TenantQuotaMapper;
import com.han.tenant.service.ITenantQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户资源配额服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantQuotaServiceImpl implements ITenantQuotaService {

    private final TenantQuotaMapper quotaMapper;

    @Override
    public TenantQuotaPo getByTenantId(Long tenantId) {
        return quotaMapper.selectOne(
                new LambdaQueryWrapper<TenantQuotaPo>()
                        .eq(TenantQuotaPo::getTenantId, tenantId)
        );
    }

    @Override
    public void saveOrUpdate(TenantQuotaPo quota) {
        TenantQuotaPo existing = getByTenantId(quota.getTenantId());
        if (existing != null) {
            quota.setQuotaId(existing.getQuotaId());
            quotaMapper.updateById(quota);
        } else {
            quotaMapper.insert(quota);
        }
    }
}
