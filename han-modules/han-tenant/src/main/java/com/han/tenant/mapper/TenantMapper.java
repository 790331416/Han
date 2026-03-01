package com.han.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.tenant.domain.po.TenantPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper 接口
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantPo> {
}
