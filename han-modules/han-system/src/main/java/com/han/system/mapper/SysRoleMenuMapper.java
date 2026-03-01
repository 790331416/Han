package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysRoleMenuPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-菜单关联 Mapper 接口
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenuPo> {
}
