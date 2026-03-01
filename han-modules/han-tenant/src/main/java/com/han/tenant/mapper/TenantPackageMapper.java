package com.han.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.tenant.domain.po.TenantPackagePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐 Mapper 接口
 */
@Mapper
public interface TenantPackageMapper extends BaseMapper<TenantPackagePo> {
}
