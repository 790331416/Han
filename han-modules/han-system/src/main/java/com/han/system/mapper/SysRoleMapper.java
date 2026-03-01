package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysRolePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper 接口
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRolePo> {
}
