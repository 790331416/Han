package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysUserRolePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper 接口
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRolePo> {
}
