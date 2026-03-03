package com.han.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.tenant.domain.po.TenantQuotaPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户资源配额 Mapper
 */
@Mapper
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaPo> {
}
