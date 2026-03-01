package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysUserPostPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-岗位关联 Mapper 接口
 */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPostPo> {
}
