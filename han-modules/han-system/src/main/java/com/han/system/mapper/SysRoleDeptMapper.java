package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysRoleDeptPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-部门关联 Mapper 接口
 */
@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDeptPo> {
}
