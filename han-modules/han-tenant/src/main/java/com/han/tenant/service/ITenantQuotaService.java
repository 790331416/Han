package com.han.tenant.service;

import com.han.tenant.domain.po.TenantQuotaPo;

/**
 * 租户资源配额服务接口
 */
public interface ITenantQuotaService {

    /**
     * 根据租户ID查询配额
     */
    TenantQuotaPo getByTenantId(Long tenantId);

    /**
     * 保存或更新配额
     */
    void saveOrUpdate(TenantQuotaPo quota);
}
