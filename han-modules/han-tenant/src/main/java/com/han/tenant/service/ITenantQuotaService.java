package com.han.tenant.service;

import com.han.tenant.domain.po.TenantQuotaPo;

/**
 * 租户资源配额服务接口
 */
public interface ITenantQuotaService {

    /**
     * 根据租户ID查询配额，未配置时返回 null
     */
    TenantQuotaPo getByTenantId(Long tenantId);

    /**
     * 根据租户ID查询配额；未配置时返回「不限」默认值，并回填实时用户数
     */
    TenantQuotaPo getOrDefault(Long tenantId);

    /**
     * 保存或更新配额
     */
    void saveOrUpdate(TenantQuotaPo quota);
}
