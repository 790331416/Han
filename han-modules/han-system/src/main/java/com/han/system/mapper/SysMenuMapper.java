package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysMenuPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单 Mapper 接口
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenuPo> {
}
