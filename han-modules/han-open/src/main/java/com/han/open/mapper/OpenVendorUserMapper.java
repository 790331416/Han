package com.han.open.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.open.domain.po.OpenVendorUserPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 厂商用户关联Mapper
 */
@Mapper
public interface OpenVendorUserMapper extends BaseMapper<OpenVendorUserPo> {

}
